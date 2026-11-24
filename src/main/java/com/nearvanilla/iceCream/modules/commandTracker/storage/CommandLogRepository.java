package com.nearvanilla.iceCream.modules.commandTracker.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite storage for the commands players run. All JDBC work runs on one dedicated thread, so
 * callers on the server thread never block on disk. The single connection is only touched from that
 * thread after construction.
 */
public final class CommandLogRepository implements AutoCloseable {
  private static final int SCHEMA_VERSION = 1;

  private static final String INSERT =
      "INSERT INTO commands (executed_at, source, player_id, name, command, command_line,"
          + " world, x, y, z, cancelled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String COLUMNS =
      "id, executed_at, source, player_id, name, command, command_line, world, x, y, z, cancelled";
  private static final String DELETE_OLDER = "DELETE FROM commands WHERE executed_at < ?";

  @FunctionalInterface
  private interface SqlTask<T> {
    T run() throws SQLException;
  }

  private final Connection connection;
  private final ExecutorService executor;

  /**
   * Opens (creating and migrating if needed) the database file.
   *
   * @param databaseFile the SQLite file
   * @throws SQLException if the database cannot be opened or migrated
   * @throws IllegalStateException if the file was written by a newer schema version
   */
  public CommandLogRepository(Path databaseFile) throws SQLException {
    this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    try {
      try (Statement statement = connection.createStatement()) {
        // Commands are written often. WAL with NORMAL sync avoids a full disk sync per command,
        // and at worst loses the last few commands if the machine itself loses power.
        statement.execute("PRAGMA journal_mode = WAL");
        statement.execute("PRAGMA synchronous = NORMAL");
      }
      migrate();
    } catch (SQLException | RuntimeException e) {
      connection.close();
      throw e;
    }
    this.executor =
        Executors.newSingleThreadExecutor(task -> new Thread(task, "IceCream-CommandTracker"));
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
          "Command tracker database schema version "
              + version
              + " is newer than the supported version "
              + SCHEMA_VERSION);
    }
    if (version < 1) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "CREATE TABLE commands ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "executed_at INTEGER NOT NULL, "
                + "source TEXT NOT NULL, "
                + "player_id TEXT, "
                + "name TEXT NOT NULL, "
                + "command TEXT NOT NULL, "
                + "command_line TEXT NOT NULL, "
                + "world TEXT, x REAL, y REAL, z REAL, "
                + "cancelled INTEGER NOT NULL)");
        statement.execute("CREATE INDEX idx_commands_time ON commands (executed_at DESC, id DESC)");
        statement.execute(
            "CREATE INDEX idx_commands_player ON commands (player_id, executed_at DESC, id DESC)");
        statement.execute(
            "CREATE INDEX idx_commands_name ON commands (name COLLATE NOCASE, executed_at DESC)");
        statement.execute(
            "CREATE INDEX idx_commands_command ON commands (command, executed_at DESC, id DESC)");
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

  /** Stores a command. */
  public CompletableFuture<Void> insert(CommandUse use) {
    return submit(
        () -> {
          try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
            insert.setLong(1, use.executedAtMillis());
            insert.setString(2, use.source().name());
            insert.setString(3, use.playerId() == null ? null : use.playerId().toString());
            insert.setString(4, use.name());
            insert.setString(5, use.command());
            insert.setString(6, use.commandLine());
            CommandPosition position = use.position();
            if (position == null) {
              insert.setNull(7, Types.VARCHAR);
              insert.setNull(8, Types.REAL);
              insert.setNull(9, Types.REAL);
              insert.setNull(10, Types.REAL);
            } else {
              insert.setString(7, position.world());
              insert.setDouble(8, position.x());
              insert.setDouble(9, position.y());
              insert.setDouble(10, position.z());
            }
            insert.setInt(11, use.cancelled() ? 1 : 0);
            insert.executeUpdate();
          }
          return null;
        });
  }

  /**
   * Loads one page of the commands matching a query, newest first.
   *
   * @param query the filters to apply
   * @param page zero based page index; clamped into the valid range
   * @param pageSize number of entries per page, must be positive
   */
  public CompletableFuture<CommandLogPage> findPage(CommandLogQuery query, int page, int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive");
    }
    return submit(() -> findPageBlocking(query, page, pageSize));
  }

  private CommandLogPage findPageBlocking(CommandLogQuery query, int page, int pageSize)
      throws SQLException {
    List<Object> parameters = new ArrayList<>();
    String where = whereClause(query, parameters);

    int total;
    try (PreparedStatement count =
        connection.prepareStatement("SELECT COUNT(*) FROM commands" + where)) {
      bind(count, parameters);
      try (ResultSet result = count.executeQuery()) {
        result.next();
        total = result.getInt(1);
      }
    }
    int pageCount = Math.max(1, (total + pageSize - 1) / pageSize);
    int clamped = Math.min(Math.max(page, 0), pageCount - 1);

    List<CommandEntry> entries = new ArrayList<>();
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT "
                + COLUMNS
                + " FROM commands"
                + where
                + " ORDER BY executed_at DESC, id DESC LIMIT ? OFFSET ?")) {
      List<Object> paged = new ArrayList<>(parameters);
      paged.add(pageSize);
      paged.add(clamped * pageSize);
      bind(select, paged);
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          entries.add(entryFrom(result));
        }
      }
    }
    return new CommandLogPage(List.copyOf(entries), clamped, pageCount, total);
  }

  private static String whereClause(CommandLogQuery query, List<Object> parameters) {
    List<String> conditions = new ArrayList<>();
    if (query.playerId() != null) {
      conditions.add("player_id = ?");
      parameters.add(query.playerId().toString());
    } else if (query.name() != null) {
      conditions.add("name = ? COLLATE NOCASE");
      parameters.add(query.name());
    }
    if (query.command() != null) {
      conditions.add("command = ?");
      parameters.add(query.command());
    }
    if (query.sinceMillis() != null) {
      conditions.add("executed_at >= ?");
      parameters.add(query.sinceMillis());
    }
    if (query.untilMillis() != null) {
      conditions.add("executed_at < ?");
      parameters.add(query.untilMillis());
    }
    if (query.search() != null) {
      conditions.add("command_line LIKE ? ESCAPE '\\'");
      parameters.add("%" + escapeLike(query.search()) + "%");
    }
    return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
  }

  /** Escapes LIKE wildcards so the search text matches literally. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static void bind(PreparedStatement statement, List<Object> parameters)
      throws SQLException {
    for (int i = 0; i < parameters.size(); i++) {
      statement.setObject(i + 1, parameters.get(i));
    }
  }

  private static CommandEntry entryFrom(ResultSet result) throws SQLException {
    String playerId = result.getString("player_id");
    String world = result.getString("world");
    CommandPosition position =
        world == null
            ? null
            : new CommandPosition(
                world, result.getDouble("x"), result.getDouble("y"), result.getDouble("z"));
    CommandUse use =
        new CommandUse(
            result.getLong("executed_at"),
            CommandSource.valueOf(result.getString("source")),
            playerId == null ? null : UUID.fromString(playerId),
            result.getString("name"),
            result.getString("command"),
            result.getString("command_line"),
            position,
            result.getInt("cancelled") != 0);
    return new CommandEntry(result.getLong("id"), use);
  }

  /**
   * Deletes every command run before a time.
   *
   * @param cutoffMillis commands run before this time are deleted
   * @return the number of deleted commands
   */
  public CompletableFuture<Integer> deleteOlderThan(long cutoffMillis) {
    return submit(
        () -> {
          try (PreparedStatement delete = connection.prepareStatement(DELETE_OLDER)) {
            delete.setLong(1, cutoffMillis);
            return delete.executeUpdate();
          }
        });
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
      throw new IllegalStateException("Failed to close the command tracker database", e);
    }
  }
}
