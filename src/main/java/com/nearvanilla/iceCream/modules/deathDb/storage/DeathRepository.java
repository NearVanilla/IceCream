package com.nearvanilla.iceCream.modules.deathDb.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite storage for player deaths. All JDBC work runs on one dedicated thread, so callers on the
 * server thread never block on disk. The single connection is only touched from that thread after
 * construction.
 */
public final class DeathRepository implements AutoCloseable {
  private static final int SCHEMA_VERSION = 1;

  private static final String INSERT =
      "INSERT INTO deaths (player_id, died_at, world, x, y, z, inventory)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String PRUNE =
      "DELETE FROM deaths WHERE player_id = ? AND id NOT IN"
          + " (SELECT id FROM deaths WHERE player_id = ?"
          + " ORDER BY died_at DESC, id DESC LIMIT ?)";
  private static final String COUNT = "SELECT COUNT(*) FROM deaths WHERE player_id = ?";
  private static final String PAGE =
      "SELECT id, died_at, world, x, y, z FROM deaths WHERE player_id = ?"
          + " ORDER BY died_at DESC, id DESC LIMIT ? OFFSET ?";
  private static final String FIND =
      "SELECT id, died_at, world, x, y, z, inventory FROM deaths WHERE id = ? AND player_id = ?";

  @FunctionalInterface
  private interface SqlTask<T> {
    T run() throws SQLException;
  }

  private final Connection connection;
  private final int maxRecordsPerPlayer;
  private final ExecutorService executor;

  /**
   * Opens (creating and migrating if needed) the database file.
   *
   * @param databaseFile the SQLite file
   * @param maxRecordsPerPlayer deaths kept per player, oldest pruned first; 0 keeps everything
   * @throws SQLException if the database cannot be opened or migrated
   * @throws IllegalStateException if the file was written by a newer schema version
   */
  public DeathRepository(Path databaseFile, int maxRecordsPerPlayer) throws SQLException {
    this.maxRecordsPerPlayer = maxRecordsPerPlayer;
    this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    try {
      migrate();
    } catch (SQLException | RuntimeException e) {
      connection.close();
      throw e;
    }
    this.executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "IceCream-DeathDb"));
  }

  private void migrate() throws SQLException {
    int version;
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("PRAGMA user_version")) {
      result.next();
      version = result.getInt(1);
    }
    if (version > SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Death database schema version "
              + version
              + " is newer than the supported version "
              + SCHEMA_VERSION);
    }
    if (version < 1) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "CREATE TABLE deaths ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "player_id TEXT NOT NULL, "
                + "died_at INTEGER NOT NULL, "
                + "world TEXT NOT NULL, "
                + "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, "
                + "inventory BLOB NOT NULL)");
        statement.execute(
            "CREATE INDEX idx_deaths_player_time ON deaths (player_id, died_at DESC, id DESC)");
        statement.execute("PRAGMA user_version = 1");
        connection.commit();
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }

  /** Stores a death and prunes the player's oldest records beyond the retention limit. */
  public CompletableFuture<Void> insert(
      UUID playerId,
      long diedAtMillis,
      String world,
      double x,
      double y,
      double z,
      byte[] inventory) {
    return submit(
        () -> {
          insertBlocking(playerId, diedAtMillis, world, x, y, z, inventory);
          return null;
        });
  }

  private void insertBlocking(
      UUID playerId,
      long diedAtMillis,
      String world,
      double x,
      double y,
      double z,
      byte[] inventory)
      throws SQLException {
    connection.setAutoCommit(false);
    try {
      try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
        insert.setString(1, playerId.toString());
        insert.setLong(2, diedAtMillis);
        insert.setString(3, world);
        insert.setDouble(4, x);
        insert.setDouble(5, y);
        insert.setDouble(6, z);
        insert.setBytes(7, inventory);
        insert.executeUpdate();
      }
      if (maxRecordsPerPlayer > 0) {
        try (PreparedStatement prune = connection.prepareStatement(PRUNE)) {
          prune.setString(1, playerId.toString());
          prune.setString(2, playerId.toString());
          prune.setInt(3, maxRecordsPerPlayer);
          prune.executeUpdate();
        }
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  /**
   * Loads one page of a player's deaths, newest first.
   *
   * @param page zero based page index; clamped into the valid range
   * @param pageSize number of entries per page, must be positive
   */
  public CompletableFuture<DeathPage> findPage(UUID playerId, int page, int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive");
    }
    return submit(() -> findPageBlocking(playerId, page, pageSize));
  }

  private DeathPage findPageBlocking(UUID playerId, int page, int pageSize) throws SQLException {
    int total;
    try (PreparedStatement count = connection.prepareStatement(COUNT)) {
      count.setString(1, playerId.toString());
      try (ResultSet result = count.executeQuery()) {
        result.next();
        total = result.getInt(1);
      }
    }
    int pageCount = Math.max(1, (total + pageSize - 1) / pageSize);
    int clamped = Math.min(Math.max(page, 0), pageCount - 1);

    List<DeathSummary> entries = new ArrayList<>();
    try (PreparedStatement query = connection.prepareStatement(PAGE)) {
      query.setString(1, playerId.toString());
      query.setInt(2, pageSize);
      query.setInt(3, clamped * pageSize);
      try (ResultSet result = query.executeQuery()) {
        while (result.next()) {
          entries.add(summaryFrom(result));
        }
      }
    }
    return new DeathPage(List.copyOf(entries), clamped, pageCount, total);
  }

  /** Loads one death with its inventory, only if it belongs to {@code playerId}. */
  public CompletableFuture<Optional<DeathRecord>> findRecord(long id, UUID playerId) {
    return submit(
        () -> {
          try (PreparedStatement query = connection.prepareStatement(FIND)) {
            query.setLong(1, id);
            query.setString(2, playerId.toString());
            try (ResultSet result = query.executeQuery()) {
              if (!result.next()) {
                return Optional.empty();
              }
              return Optional.of(
                  new DeathRecord(summaryFrom(result), result.getBytes("inventory")));
            }
          }
        });
  }

  private static DeathSummary summaryFrom(ResultSet result) throws SQLException {
    return new DeathSummary(
        result.getLong("id"),
        result.getLong("died_at"),
        result.getString("world"),
        result.getDouble("x"),
        result.getDouble("y"),
        result.getDouble("z"));
  }

  private <T> CompletableFuture<T> submit(SqlTask<T> task) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return task.run();
          } catch (SQLException e) {
            throw new CompletionException(e);
          }
        },
        executor);
  }

  /** Waits for queued work to finish, then closes the connection. Safe to call twice. */
  @Override
  public void close() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
      connection.close();
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to close the death database", e);
    }
  }
}
