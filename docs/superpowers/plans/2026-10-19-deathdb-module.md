# DeathDB Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an IceCream module that logs player deaths to SQLite and lets staff browse and restore them through paginated chest GUIs via `/restoredeath <player>` (alias `/rd`).

**Architecture:** A `deathDb` module following the existing `Module` pattern. A `DeathRepository` owns SQLite on a single dedicated thread and exposes `CompletableFuture`s. A listener snapshots the inventory (41 fixed slots) on death. Two `InventoryHolder` GUIs (list and preview) are made read-only by a single listener that cancels all interaction, and only a plain left click on a top inventory button acts.

**Tech Stack:** Java 25, Paper API `26.3.build.200-stable`, Cloud `org.incendo` 2.x annotations, Adventure MiniMessage, SQLite via the JDBC driver bundled with Paper, JUnit 6.1.2 (repository tests only), Gradle with Spotless (google-java-format).

**Spec:** `docs/superpowers/specs/2026-10-19-deathdb-module-design.md`

## Global Constraints

- Base package: `com.nearvanilla.iceCream.modules.deathDb`. Config key prefix: `modules.deathdb` (lowercase, like every other module).
- Java 25 (`targetJavaVersion = 25`). No wildcard imports (Spotless `forbidWildcardImports`). Run `./gradlew spotlessApply` before every commit.
- Do not declare permissions in `plugin.yml`. The single permission `icecream.modules.deathdb.restoredeath` is declared only in the Cloud `@Permission` annotation.
- Player-facing text uses MiniMessage templates; dynamic plain text goes through `Placeholder.unparsed(...)`. Never concatenate untrusted text (player names, world names) into a template.
- Do not shade or `compileOnly` the SQLite driver. Paper bundles it (3.49.1.0 in `run/libraries`). Use `DriverManager.getConnection("jdbc:sqlite:...")`.
- Do not declare the module in `ARCHITECTURE.md` (repo rule: new modules are not documented there).
- Documentation and comments: no em dashes, no HTML tags (JavaDoc excepted).
- Register the module in `IceCream.onEnable` and call `unregister()` from `IceCream.onDisable`.
- Commit messages: conventional style (`feat:`, `test:`, `build:`). Do not push.

## File Structure

```
build.gradle                                              (modify: test deps + useJUnitPlatform)
src/main/resources/config.yml                             (modify: modules.deathdb block)
src/main/java/com/nearvanilla/iceCream/IceCream.java      (modify: register/unregister module)
src/main/java/com/nearvanilla/iceCream/modules/deathDb/
  DeathDbModule.java
  InventorySnapshot.java
  InventoryRestorer.java
  storage/DeathSummary.java
  storage/DeathRecord.java
  storage/DeathPage.java
  storage/DeathRepository.java
  gui/DeathGui.java
  gui/DeathGuiItems.java
  gui/DeathListGui.java
  gui/DeathPreviewGui.java
  events/DeathRecordListener.java
  events/DeathGuiListener.java
  commands/RestoreDeathCommand.java
src/test/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRepositoryTest.java
```

---

### Task 1: Storage layer with tests

**Files:**
- Modify: `build.gradle` (`dependencies` block and a new `test` block)
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathSummary.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRecord.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathPage.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRepository.java`
- Test: `src/test/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRepositoryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces (used by Tasks 2 and 3):
  - `record DeathSummary(long id, long diedAtMillis, String world, double x, double y, double z)`
  - `record DeathRecord(DeathSummary summary, byte[] inventory)`
  - `record DeathPage(List<DeathSummary> entries, int page, int pageCount, int totalRecords)` (`page` is zero based, `pageCount >= 1`)
  - `DeathRepository(Path databaseFile, int maxRecordsPerPlayer) throws SQLException` (`0` means unlimited; throws `IllegalStateException` for a database with a newer schema)
  - `CompletableFuture<Void> insert(UUID playerId, long diedAtMillis, String world, double x, double y, double z, byte[] inventory)`
  - `CompletableFuture<DeathPage> findPage(UUID playerId, int page, int pageSize)` (newest first, `page` clamped into range, `pageSize > 0`)
  - `CompletableFuture<Optional<DeathRecord>> findRecord(long id, UUID playerId)` (empty when the id belongs to another player)
  - `void close()` (waits for queued work, idempotent, does not throw checked exceptions)

- [ ] **Step 1: Add test dependencies and enable JUnit in `build.gradle`**

In the `dependencies { ... }` block, after the `compileOnly("de.hexaoxi:carbonchat-api:3.0.0-beta.37")` line, add:

```groovy
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
```

After the `runServer { ... }` block, add:

```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRepositoryTest.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeathRepositoryTest {
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @TempDir Path tempDir;
  private DeathRepository repository;

  @BeforeEach
  void open() throws SQLException {
    repository = new DeathRepository(tempDir.resolve("deaths.db"), 0);
  }

  @AfterEach
  void close() {
    repository.close();
  }

  private static void insert(DeathRepository repo, UUID player, long time) {
    repo.insert(player, time, "world", 1.5, 64.0, -3.25, new byte[] {1, 2, 3}).join();
  }

  private static List<Long> times(DeathPage page) {
    return page.entries().stream().map(DeathSummary::diedAtMillis).toList();
  }

  @Test
  void insertedRecordRoundTripsIncludingTimestampsBeyond32Bits() {
    byte[] inventory = {1, 2, 3, 4};
    long time = 5_000_000_000_000L;
    repository.insert(ALICE, time, "world_nether", 1.5, 64.0, -3.25, inventory).join();

    DeathPage page = repository.findPage(ALICE, 0, 45).join();
    assertEquals(1, page.entries().size());
    DeathSummary summary = page.entries().get(0);
    assertEquals(time, summary.diedAtMillis());
    assertEquals("world_nether", summary.world());
    assertEquals(1.5, summary.x());
    assertEquals(64.0, summary.y());
    assertEquals(-3.25, summary.z());

    DeathRecord record = repository.findRecord(summary.id(), ALICE).join().orElseThrow();
    assertEquals(summary, record.summary());
    assertArrayEquals(inventory, record.inventory());
  }

  @Test
  void pagesAreNewestFirstAndRequestedPageIsClamped() {
    for (int i = 1; i <= 7; i++) {
      insert(repository, ALICE, i * 1000L);
    }

    DeathPage first = repository.findPage(ALICE, 0, 3).join();
    assertEquals(3, first.pageCount());
    assertEquals(7, first.totalRecords());
    assertEquals(List.of(7000L, 6000L, 5000L), times(first));

    DeathPage last = repository.findPage(ALICE, 2, 3).join();
    assertEquals(List.of(1000L), times(last));

    DeathPage clamped = repository.findPage(ALICE, 99, 3).join();
    assertEquals(2, clamped.page());
    assertEquals(List.of(1000L), times(clamped));

    DeathPage negative = repository.findPage(ALICE, -5, 3).join();
    assertEquals(0, negative.page());
  }

  @Test
  void playerWithoutDeathsGetsOneEmptyPage() {
    insert(repository, BOB, 1000L);
    DeathPage page = repository.findPage(ALICE, 0, 45).join();
    assertTrue(page.entries().isEmpty());
    assertEquals(0, page.page());
    assertEquals(1, page.pageCount());
    assertEquals(0, page.totalRecords());
  }

  @Test
  void findRecordRejectsAnIdOwnedByAnotherPlayer() {
    insert(repository, ALICE, 1000L);
    long aliceRecordId = repository.findPage(ALICE, 0, 45).join().entries().get(0).id();

    assertTrue(repository.findRecord(aliceRecordId, BOB).join().isEmpty());
    assertTrue(repository.findRecord(aliceRecordId, ALICE).join().isPresent());
  }

  @Test
  void retentionPrunesOldestRecordsPerPlayerOnly() throws SQLException {
    try (DeathRepository pruned = new DeathRepository(tempDir.resolve("pruned.db"), 2)) {
      insert(pruned, ALICE, 1000L);
      insert(pruned, BOB, 1500L);
      insert(pruned, ALICE, 2000L);
      insert(pruned, ALICE, 3000L);

      assertEquals(List.of(3000L, 2000L), times(pruned.findPage(ALICE, 0, 45).join()));
      assertEquals(List.of(1500L), times(pruned.findPage(BOB, 0, 45).join()));
    }
  }

  @Test
  void reopeningAnExistingFileKeepsItsData() throws SQLException {
    Path file = tempDir.resolve("reopen.db");
    try (DeathRepository first = new DeathRepository(file, 0)) {
      insert(first, ALICE, 1000L);
    }
    try (DeathRepository second = new DeathRepository(file, 0)) {
      assertEquals(List.of(1000L), times(second.findPage(ALICE, 0, 45).join()));
    }
  }

  @Test
  void refusesADatabaseFromANewerSchema() throws SQLException {
    Path file = tempDir.resolve("future.db");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE marker (x)");
      statement.execute("PRAGMA user_version = 99");
    }
    assertThrows(IllegalStateException.class, () -> new DeathRepository(file, 0));
  }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepositoryTest"`
Expected: FAIL at `compileTestJava` with "cannot find symbol" for `DeathRepository`, `DeathPage`, `DeathSummary`, `DeathRecord`.

- [ ] **Step 4: Create the value records**

`src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathSummary.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.storage;

/** Metadata for one recorded death. The inventory blob is deliberately not included. */
public record DeathSummary(
    long id, long diedAtMillis, String world, double x, double y, double z) {}
```

`src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRecord.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.storage;

/**
 * A recorded death together with its serialized inventory snapshot.
 *
 * @param summary the death metadata
 * @param inventory bytes produced by {@code InventorySnapshot.serialize}; treat as read-only
 */
public record DeathRecord(DeathSummary summary, byte[] inventory) {}
```

`src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathPage.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.storage;

import java.util.List;

/**
 * One page of a player's deaths, newest first.
 *
 * @param entries the deaths on this page
 * @param page the zero based page index actually returned (after clamping)
 * @param pageCount total number of pages, always at least 1
 * @param totalRecords total number of stored deaths for the player
 */
public record DeathPage(List<DeathSummary> entries, int page, int pageCount, int totalRecords) {}
```

- [ ] **Step 5: Implement `DeathRepository`**

`src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage/DeathRepository.java`:

```java
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
              return Optional.of(new DeathRecord(summaryFrom(result), result.getBytes("inventory")));
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
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepositoryTest"`
Expected: PASS, 7 tests. If `refusesADatabaseFromANewerSchema` fails because no exception was thrown, the pragma did not persist; keep the `CREATE TABLE marker` statement before it (it forces the file to be written).

- [ ] **Step 7: Format and commit**

```bash
./gradlew spotlessApply
git add build.gradle src/main/java/com/nearvanilla/iceCream/modules/deathDb/storage src/test
git commit -m "feat(deathdb): add SQLite death repository with tests"
```

---

### Task 2: Record deaths (snapshot, listener, module, config)

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/InventorySnapshot.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/events/DeathRecordListener.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/DeathDbModule.java`
- Modify: `src/main/resources/config.yml` (after the `deathlocation` block)
- Modify: `src/main/java/com/nearvanilla/iceCream/IceCream.java` (import, field, `register()`, `unregister()`)

**Interfaces:**
- Consumes: `DeathRepository` from Task 1.
- Produces (used by Task 3):
  - `InventorySnapshot.SIZE = 41`, `STORAGE_SLOTS = 36`, `ARMOR_START = 36` (boots, leggings, chestplate, helmet), `OFFHAND_SLOT = 40`
  - `static ItemStack[] capture(PlayerInventory)` (returns clones, length 41)
  - `static void apply(PlayerInventory, ItemStack[])`
  - `static byte[] serialize(ItemStack[])`
  - `static ItemStack[] deserialize(byte[]) throws IOException`
  - `static boolean isEmpty(ItemStack)` (null or air)
  - `DeathDbModule.RESTORE_PERMISSION = "icecream.modules.deathdb.restoredeath"`
  - `DeathDbModule` has `register()`, `unregister()`, and keeps the repository in a field that `registerCommands()` and `registerEvents()` read.

Bukkit classes cannot be unit tested without a server, so this task is verified by compiling and a smoke run.

- [ ] **Step 1: Create `InventorySnapshot`**

```java
package com.nearvanilla.iceCream.modules.deathDb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Fixed layout snapshot of a player's inventory and its byte format.
 *
 * <p>Slot order: 0-35 storage (0-8 hotbar, 9-35 main inventory), 36-39 armor (boots, leggings,
 * chestplate, helmet), 40 offhand. Explicit Bukkit accessors are used instead of {@code
 * getContents()} so the layout never depends on server internals.
 *
 * <p>Bytes: version byte (1), int slot count (41), then per slot an int length (0 means empty)
 * followed by {@link ItemStack#serializeAsBytes()}.
 */
public final class InventorySnapshot {
  public static final int STORAGE_SLOTS = 36;
  public static final int ARMOR_SLOTS = 4;
  public static final int ARMOR_START = STORAGE_SLOTS;
  public static final int OFFHAND_SLOT = ARMOR_START + ARMOR_SLOTS;
  public static final int SIZE = OFFHAND_SLOT + 1;

  private static final int FORMAT_VERSION = 1;

  private InventorySnapshot() {}

  /** Returns true for a null item or an air stack. */
  public static boolean isEmpty(ItemStack item) {
    return item == null || item.getType().isAir();
  }

  /** Copies the inventory into a 41 slot array of cloned stacks. */
  public static ItemStack[] capture(PlayerInventory inventory) {
    ItemStack[] slots = new ItemStack[SIZE];
    copyInto(inventory.getStorageContents(), slots, 0, STORAGE_SLOTS);
    copyInto(inventory.getArmorContents(), slots, ARMOR_START, ARMOR_SLOTS);
    ItemStack offhand = inventory.getItemInOffHand();
    slots[OFFHAND_SLOT] = isEmpty(offhand) ? null : offhand.clone();
    return slots;
  }

  private static void copyInto(ItemStack[] source, ItemStack[] target, int start, int max) {
    for (int i = 0; i < Math.min(source.length, max); i++) {
      target[start + i] = isEmpty(source[i]) ? null : source[i].clone();
    }
  }

  /** Writes all 41 slots into the inventory, replacing whatever was there. */
  public static void apply(PlayerInventory inventory, ItemStack[] slots) {
    inventory.setStorageContents(Arrays.copyOfRange(slots, 0, STORAGE_SLOTS));
    inventory.setArmorContents(Arrays.copyOfRange(slots, ARMOR_START, ARMOR_START + ARMOR_SLOTS));
    inventory.setItemInOffHand(slots[OFFHAND_SLOT]);
  }

  /** Serializes a 41 slot array. Must run on the server thread. */
  public static byte[] serialize(ItemStack[] slots) {
    if (slots.length != SIZE) {
      throw new IllegalArgumentException("Expected " + SIZE + " slots but got " + slots.length);
    }
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(FORMAT_VERSION);
      out.writeInt(SIZE);
      for (ItemStack item : slots) {
        if (isEmpty(item)) {
          out.writeInt(0);
        } else {
          byte[] data = item.serializeAsBytes();
          out.writeInt(data.length);
          out.write(data);
        }
      }
      out.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a snapshot produced by {@link #serialize(ItemStack[])}. Any malformed or unreadable slot
   * fails the whole snapshot so callers never apply a partial inventory.
   *
   * @throws IOException if the bytes are corrupt or from an unsupported version
   */
  public static ItemStack[] deserialize(byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      int version = in.readUnsignedByte();
      if (version != FORMAT_VERSION) {
        throw new IOException("Unsupported inventory snapshot version " + version);
      }
      int size = in.readInt();
      if (size != SIZE) {
        throw new IOException("Expected " + SIZE + " slots but snapshot has " + size);
      }
      ItemStack[] slots = new ItemStack[SIZE];
      for (int i = 0; i < SIZE; i++) {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
          throw new IOException("Corrupt item length " + length + " in slot " + i);
        }
        if (length == 0) {
          continue;
        }
        byte[] data = new byte[length];
        in.readFully(data);
        slots[i] = ItemStack.deserializeBytes(data);
      }
      return slots;
    }
  }
}
```

- [ ] **Step 2: Create `DeathRecordListener`**

```java
package com.nearvanilla.iceCream.modules.deathDb.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.InventorySnapshot;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Records every player death. The inventory is snapshotted on the server thread at MONITOR so it
 * reflects the final state other plugins left, then the insert runs on the repository thread.
 */
public class DeathRecordListener implements Listener {
  private final DeathRepository repository;

  public DeathRecordListener(DeathRepository repository) {
    this.repository = repository;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    try {
      Location location = player.getLocation();
      byte[] inventory =
          InventorySnapshot.serialize(InventorySnapshot.capture(player.getInventory()));
      repository
          .insert(
              player.getUniqueId(),
              System.currentTimeMillis(),
              location.getWorld().getName(),
              location.getX(),
              location.getY(),
              location.getZ(),
              inventory)
          .exceptionally(
              error -> {
                IceCream.logger.log(
                    Level.SEVERE, "Failed to store the death of " + player.getName(), error);
                return null;
              });
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to record the death of " + player.getName(), e);
    }
  }
}
```

- [ ] **Step 3: Create `DeathDbModule`**

```java
package com.nearvanilla.iceCream.modules.deathDb;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.deathDb.events.DeathRecordListener;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Logs player deaths (time, position and a full inventory snapshot) to SQLite and lets staff browse
 * and restore them through a chest GUI.
 */
public class DeathDbModule implements Module {
  public static final String RESTORE_PERMISSION = "icecream.modules.deathdb.restoredeath";
  private static final String DATABASE_FILE = "deathdb.db";

  protected boolean isEnabled = false;
  private DeathRepository repository;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.deathdb.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    // Commands are added in a later task.
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new DeathRecordListener(repository), IceCream.instance);
  }

  @Override
  public void register() {
    if (!shouldEnable()) {
      IceCream.logger.info("Death DB module is disabled.");
      return;
    }
    try {
      Path dataFolder = IceCream.instance.getDataFolder().toPath();
      Files.createDirectories(dataFolder);
      int maxRecords =
          Math.max(0, IceCream.config.getInt("modules.deathdb.max-records-per-player", 100));
      repository = new DeathRepository(dataFolder.resolve(DATABASE_FILE), maxRecords);
      registerCommands();
      registerEvents();
      isEnabled = true;
    } catch (Exception e) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Death DB module", e);
      unregister();
      return;
    }
    IceCream.logger.info("Death DB module has been enabled.");
  }

  /** Flushes queued writes and closes the database. Safe to call when never enabled. */
  public void unregister() {
    isEnabled = false;
    if (repository == null) {
      return;
    }
    try {
      repository.close();
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to close the Death DB database", e);
    }
    repository = null;
  }
}
```

- [ ] **Step 4: Add the config block**

In `src/main/resources/config.yml`, directly after

```yaml
  deathlocation:
    enabled: true
```

insert:

```yaml
  deathdb:
    enabled: true
    # Maximum stored deaths per player; the oldest are pruned first. 0 keeps everything.
    max-records-per-player: 100
```

- [ ] **Step 5: Register the module in `IceCream.java`**

1. Add the import before the `deathLocation` import (ASCII order puts `deathDb` first):
   `import com.nearvanilla.iceCream.modules.deathDb.DeathDbModule;`
2. Add the field next to the other modules:
   `private final DeathDbModule deathDbModule = new DeathDbModule();`
3. In `onEnable`, after `bonemealableSporeBlossomsModule.register();` add `deathDbModule.register();`
4. In `onDisable`, add `deathDbModule.unregister();` as the first line (alphabetical with the others is not required; it just must be called).

- [ ] **Step 6: Compile and format**

Run: `./gradlew spotlessApply compileJava`
Expected: BUILD SUCCESSFUL. A "cannot find symbol" on `setStorageContents`, `getStorageContents` or `editMeta` means the API signature differs from the verified one; re-check with `javap` against `paper-api-26.3.build.200-stable.jar`.

- [ ] **Step 7: Smoke test recording**

1. `./gradlew runServer`, join with a client (set `online-mode=false` in `run/server.properties` if needed).
2. Equip a helmet, put an item in the offhand, fill a few hotbar and main slots, then run `/kill`.
3. Confirm the console shows `Death DB module has been enabled.` and no stack traces.
4. Stop the server, then run in the `eval` tool (Python):

```python
import sqlite3, struct
c = sqlite3.connect("D:/Projects/Java/IceCream/run/plugins/IceCream/deathdb.db")
for row in c.execute("SELECT id, player_id, died_at, world, x, y, z, inventory FROM deaths ORDER BY id DESC LIMIT 3"):
    blob = row[7]
    version, count = blob[0], struct.unpack(">i", blob[1:5])[0]
    pos, filled = 5, []
    for slot in range(count):
        n = struct.unpack(">i", blob[pos:pos+4])[0]; pos += 4 + n
        if n: filled.append(slot)
    print(row[:7], "version", version, "slots", count, "filled", filled, "trailing bytes", len(blob) - pos)
```

Expected: `version 1`, `slots 41`, `trailing bytes 0`, and `filled` contains the hotbar index you used (0-8), a main slot (9-35), `39` for the helmet (armor index 3), and `40` for the offhand.

- [ ] **Step 8: Commit**

```bash
git add build.gradle src/main
git commit -m "feat(deathdb): record player deaths with full inventory snapshots"
```

---

### Task 3: GUIs, restore and command

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/InventoryRestorer.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/gui/DeathGui.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/gui/DeathGuiItems.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/gui/DeathListGui.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/gui/DeathPreviewGui.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/events/DeathGuiListener.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/commands/RestoreDeathCommand.java`
- Modify: `src/main/java/com/nearvanilla/iceCream/modules/deathDb/DeathDbModule.java` (`registerCommands`, `registerEvents`)

**Interfaces:**
- Consumes: everything from Tasks 1 and 2.
- Produces:
  - `InventoryRestorer.restore(Player target, ItemStack[] snapshot)`
  - `DeathGui extends InventoryHolder { void onClick(Player viewer, int rawSlot); }`
  - `DeathListGui.open(Player viewer, DeathRepository repository, UUID targetId, String targetName, int page)` and `DeathListGui.PAGE_SIZE = 45`
  - `DeathPreviewGui.open(Player viewer, DeathRepository repository, UUID targetId, String targetName, long deathId)`
  - `RestoreDeathCommand(DeathRepository repository)`

- [ ] **Step 1: Create `InventoryRestorer`**

```java
package com.nearvanilla.iceCream.modules.deathDb;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Applies a deserialized snapshot to an online player. */
public final class InventoryRestorer {
  private InventoryRestorer() {}

  /**
   * Replaces the target's storage, armor and offhand with the snapshot. What they were carrying is
   * dropped at their feet. Must run on the server thread with a fully deserialized snapshot.
   */
  public static void restore(Player target, ItemStack[] snapshot) {
    PlayerInventory inventory = target.getInventory();
    ItemStack[] previous = InventorySnapshot.capture(inventory);
    InventorySnapshot.apply(inventory, snapshot);
    for (ItemStack item : previous) {
      if (!InventorySnapshot.isEmpty(item)) {
        target.getWorld().dropItem(target.getLocation(), item);
      }
    }
    target.updateInventory();
  }
}
```

- [ ] **Step 2: Create `DeathGui` and `DeathGuiItems`**

`gui/DeathGui.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder for the death browsing GUIs; the listener cancels all interaction with them. */
public interface DeathGui extends InventoryHolder {
  /**
   * Called for a plain left click on a slot of this GUI's own inventory.
   *
   * @param viewer the clicking player
   * @param rawSlot the clicked slot index in the GUI inventory
   */
  void onClick(Player viewer, int rawSlot);
}
```

`gui/DeathGuiItems.java`:

```java
package com.nearvanilla.iceCream.modules.deathDb.gui;

import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Shared item, text and formatting helpers for the death GUIs. */
final class DeathGuiItems {
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private DeathGuiItems() {}

  /** Deserializes a MiniMessage template with italics disabled (item text is italic by default). */
  static Component line(String template, TagResolver... resolvers) {
    return MiniMessage.miniMessage().deserialize("<!italic>" + template, resolvers);
  }

  static ItemStack named(Material material, Component name, Component... lore) {
    ItemStack stack = new ItemStack(material);
    stack.editMeta(
        meta -> {
          meta.displayName(name);
          if (lore.length > 0) {
            meta.lore(List.of(lore));
          }
        });
    return stack;
  }

  static ItemStack filler() {
    return named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
  }

  static String formatTime(long epochMillis) {
    return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
  }

  static String formatPosition(DeathSummary death) {
    return (long) Math.floor(death.x())
        + ", "
        + (long) Math.floor(death.y())
        + ", "
        + (long) Math.floor(death.z());
  }
}
```

- [ ] **Step 3: Create `DeathListGui`**

```java
package com.nearvanilla.iceCream.modules.deathDb.gui;

import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.filler;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.line;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.named;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathPage;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Paginated chest GUI listing a player's recorded deaths, newest first. */
public final class DeathListGui implements DeathGui {
  public static final int PAGE_SIZE = 45;
  private static final int SIZE = 54;
  private static final int PREVIOUS_SLOT = 45;
  private static final int INFO_SLOT = 49;
  private static final int NEXT_SLOT = 53;

  private final DeathRepository repository;
  private final UUID targetId;
  private final String targetName;
  private final DeathPage page;
  private final Map<Integer, Long> deathIdBySlot = new HashMap<>();
  private final Inventory inventory;

  private DeathListGui(
      DeathRepository repository, UUID targetId, String targetName, DeathPage page) {
    this.repository = repository;
    this.targetId = targetId;
    this.targetName = targetName;
    this.page = page;
    this.inventory =
        Bukkit.createInventory(
            this,
            SIZE,
            line(
                "<dark_gray>Deaths of <name> (<page>/<pages>)",
                Placeholder.unparsed("name", targetName),
                Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                Placeholder.unparsed("pages", String.valueOf(page.pageCount()))));
    populate();
  }

  private void populate() {
    List<DeathSummary> entries = page.entries();
    for (int slot = 0; slot < entries.size(); slot++) {
      DeathSummary death = entries.get(slot);
      inventory.setItem(
          slot,
          named(
              Material.SKELETON_SKULL,
              line(
                  "<red><time></red>",
                  Placeholder.unparsed("time", DeathGuiItems.formatTime(death.diedAtMillis()))),
              line("<gray>World: <white><world></white>", Placeholder.unparsed("world", death.world())),
              line(
                  "<gray>Position: <white><position></white>",
                  Placeholder.unparsed("position", DeathGuiItems.formatPosition(death))),
              line("<dark_gray>Click to preview this inventory")));
      deathIdBySlot.put(slot, death.id());
    }
    for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
      inventory.setItem(slot, filler());
    }
    if (page.page() > 0) {
      inventory.setItem(PREVIOUS_SLOT, named(Material.ARROW, line("<yellow>Previous page")));
    }
    if (page.page() < page.pageCount() - 1) {
      inventory.setItem(NEXT_SLOT, named(Material.ARROW, line("<yellow>Next page")));
    }
    inventory.setItem(
        INFO_SLOT,
        named(
            Material.PAPER,
            line(
                "<gold>Page <page> of <pages>",
                Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                Placeholder.unparsed("pages", String.valueOf(page.pageCount()))),
            line(
                "<gray><total> recorded deaths",
                Placeholder.unparsed("total", String.valueOf(page.totalRecords())))));
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public void onClick(Player viewer, int rawSlot) {
    if (rawSlot == PREVIOUS_SLOT && page.page() > 0) {
      open(viewer, repository, targetId, targetName, page.page() - 1);
    } else if (rawSlot == NEXT_SLOT && page.page() < page.pageCount() - 1) {
      open(viewer, repository, targetId, targetName, page.page() + 1);
    } else {
      Long deathId = deathIdBySlot.get(rawSlot);
      if (deathId != null) {
        DeathPreviewGui.open(viewer, repository, targetId, targetName, deathId);
      }
    }
  }

  /** Loads the page asynchronously, then opens the GUI for the viewer on the server thread. */
  public static void open(
      Player viewer, DeathRepository repository, UUID targetId, String targetName, int page) {
    repository
        .findPage(targetId, page, PAGE_SIZE)
        .whenComplete(
            (result, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        IceCream.instance,
                        () -> {
                          if (!viewer.isOnline()) {
                            return;
                          }
                          if (error != null) {
                            IceCream.logger.log(
                                Level.SEVERE, "Failed to load deaths of " + targetName, error);
                            viewer.sendMessage(
                                line("<red>Could not load death records. Check the console.</red>"));
                            return;
                          }
                          if (result.totalRecords() == 0) {
                            viewer.sendMessage(
                                line(
                                    "<gray><name> has no recorded deaths.</gray>",
                                    Placeholder.unparsed("name", targetName)));
                            return;
                          }
                          viewer.openInventory(
                              new DeathListGui(repository, targetId, targetName, result)
                                  .getInventory());
                        }));
  }
}
```

- [ ] **Step 4: Create `DeathPreviewGui`**

```java
package com.nearvanilla.iceCream.modules.deathDb.gui;

import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.filler;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.line;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.named;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.InventoryRestorer;
import com.nearvanilla.iceCream.modules.deathDb.InventorySnapshot;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRecord;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only preview of one recorded inventory, laid out like the in-game inventory, with a cancel
 * pane and a restore pane.
 */
public final class DeathPreviewGui implements DeathGui {
  private static final int SIZE = 54;
  private static final int INFO_SLOT = 0;
  private static final int HELMET_SLOT = 2;
  private static final int CHESTPLATE_SLOT = 3;
  private static final int LEGGINGS_SLOT = 4;
  private static final int BOOTS_SLOT = 5;
  private static final int OFFHAND_SLOT = 7;
  private static final int MAIN_START = 9;
  private static final int HOTBAR_START = 36;
  private static final int CANCEL_SLOT = 48;
  private static final int RESTORE_SLOT = 50;

  private final UUID targetId;
  private final String targetName;
  private final DeathSummary death;
  private final ItemStack[] snapshot;
  private final Inventory inventory;
  private boolean restored = false;

  private DeathPreviewGui(
      UUID targetId, String targetName, DeathSummary death, ItemStack[] snapshot) {
    this.targetId = targetId;
    this.targetName = targetName;
    this.death = death;
    this.snapshot = snapshot;
    this.inventory =
        Bukkit.createInventory(
            this,
            SIZE,
            line("<dark_gray>Inventory of <name>", Placeholder.unparsed("name", targetName)));
    populate();
  }

  private void populate() {
    for (int slot = 0; slot < 9; slot++) {
      inventory.setItem(slot, filler());
    }
    for (int slot = 45; slot < SIZE; slot++) {
      inventory.setItem(slot, filler());
    }

    inventory.setItem(
        INFO_SLOT,
        named(
            Material.CLOCK,
            line("<gold>Death of <name>", Placeholder.unparsed("name", targetName)),
            line(
                "<gray>Time: <white><time></white>",
                Placeholder.unparsed("time", DeathGuiItems.formatTime(death.diedAtMillis()))),
            line("<gray>World: <white><world></white>", Placeholder.unparsed("world", death.world())),
            line(
                "<gray>Position: <white><position></white>",
                Placeholder.unparsed("position", DeathGuiItems.formatPosition(death)))));

    placeEquipment(HELMET_SLOT, InventorySnapshot.ARMOR_START + 3, "helmet");
    placeEquipment(CHESTPLATE_SLOT, InventorySnapshot.ARMOR_START + 2, "chestplate");
    placeEquipment(LEGGINGS_SLOT, InventorySnapshot.ARMOR_START + 1, "leggings");
    placeEquipment(BOOTS_SLOT, InventorySnapshot.ARMOR_START, "boots");
    placeEquipment(OFFHAND_SLOT, InventorySnapshot.OFFHAND_SLOT, "offhand item");

    for (int index = 9; index < InventorySnapshot.STORAGE_SLOTS; index++) {
      inventory.setItem(MAIN_START + (index - 9), snapshot[index]);
    }
    for (int index = 0; index < 9; index++) {
      inventory.setItem(HOTBAR_START + index, snapshot[index]);
    }

    inventory.setItem(
        CANCEL_SLOT,
        named(
            Material.RED_STAINED_GLASS_PANE,
            line("<red>Cancel"),
            line("<gray>Close without restoring anything")));
    inventory.setItem(
        RESTORE_SLOT,
        named(
            Material.LIME_STAINED_GLASS_PANE,
            line("<green>Restore this inventory"),
            line(
                "<gray>Replaces the current inventory of <white><name></white>",
                Placeholder.unparsed("name", targetName)),
            line("<gray>Their current items are dropped at their feet")));
  }

  private void placeEquipment(int guiSlot, int snapshotIndex, String label) {
    ItemStack item = snapshot[snapshotIndex];
    inventory.setItem(
        guiSlot,
        InventorySnapshot.isEmpty(item)
            ? named(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                line("<gray>No <label>", Placeholder.unparsed("label", label)))
            : item);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public void onClick(Player viewer, int rawSlot) {
    if (rawSlot == CANCEL_SLOT) {
      viewer.closeInventory();
    } else if (rawSlot == RESTORE_SLOT) {
      restore(viewer);
    }
  }

  private void restore(Player staff) {
    if (restored) {
      return;
    }
    Player target = Bukkit.getPlayer(targetId);
    if (target == null) {
      staff.closeInventory();
      staff.sendMessage(
          line(
              "<red><name> is offline. They must be online to restore an inventory.</red>",
              Placeholder.unparsed("name", targetName)));
      return;
    }
    if (target.isDead()) {
      staff.closeInventory();
      staff.sendMessage(
          line(
              "<red><name> is on the respawn screen. Try again once they have respawned.</red>",
              Placeholder.unparsed("name", targetName)));
      return;
    }
    restored = true;
    InventoryRestorer.restore(target, snapshot);
    staff.closeInventory();
    staff.sendMessage(
        line(
            "<green>Restored the inventory of <name>.</green>",
            Placeholder.unparsed("name", targetName)));
    IceCream.logger.info(
        staff.getName() + " restored death " + death.id() + " of " + targetName);
  }

  /** Loads the record asynchronously, then opens the preview for the viewer on the server thread. */
  public static void open(
      Player viewer,
      DeathRepository repository,
      UUID targetId,
      String targetName,
      long deathId) {
    repository
        .findRecord(deathId, targetId)
        .whenComplete(
            (result, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        IceCream.instance,
                        () -> {
                          if (!viewer.isOnline()) {
                            return;
                          }
                          if (error != null) {
                            IceCream.logger.log(
                                Level.SEVERE, "Failed to load death " + deathId, error);
                            viewer.sendMessage(
                                line("<red>Could not load that death. Check the console.</red>"));
                            return;
                          }
                          if (result.isEmpty()) {
                            viewer.sendMessage(
                                line("<red>That death record no longer exists.</red>"));
                            return;
                          }
                          DeathRecord record = result.get();
                          ItemStack[] snapshot;
                          try {
                            snapshot = InventorySnapshot.deserialize(record.inventory());
                          } catch (IOException | RuntimeException e) {
                            IceCream.logger.log(
                                Level.SEVERE, "Death " + deathId + " has an unreadable snapshot", e);
                            viewer.sendMessage(
                                line(
                                    "<red>That record is corrupt and cannot be previewed.</red>"));
                            return;
                          }
                          viewer.openInventory(
                              new DeathPreviewGui(targetId, targetName, record.summary(), snapshot)
                                  .getInventory());
                        }));
  }
}
```

- [ ] **Step 5: Create `DeathGuiListener`**

```java
package com.nearvanilla.iceCream.modules.deathDb.events;

import com.nearvanilla.iceCream.modules.deathDb.DeathDbModule;
import com.nearvanilla.iceCream.modules.deathDb.gui.DeathGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Makes the death GUIs strictly read-only and dispatches button clicks. While a death GUI is the
 * top inventory every click and drag is cancelled, whichever inventory it targets (this covers
 * shift clicks, number keys and double click collection). Only a plain left click on the GUI's own
 * inventory runs an action.
 */
public class DeathGuiListener implements Listener {

  @EventHandler(priority = EventPriority.HIGH)
  public void onClick(InventoryClickEvent event) {
    Inventory top = event.getView().getTopInventory();
    if (!(top.getHolder(false) instanceof DeathGui gui)) {
      return;
    }
    event.setCancelled(true);
    if (event.getClickedInventory() != top || event.getClick() != ClickType.LEFT) {
      return;
    }
    if (!(event.getWhoClicked() instanceof Player viewer)) {
      return;
    }
    if (!viewer.hasPermission(DeathDbModule.RESTORE_PERMISSION)) {
      viewer.closeInventory();
      return;
    }
    gui.onClick(viewer, event.getRawSlot());
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onDrag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder(false) instanceof DeathGui) {
      event.setCancelled(true);
    }
  }
}
```

- [ ] **Step 6: Create `RestoreDeathCommand`**

```java
package com.nearvanilla.iceCream.modules.deathDb.commands;

import com.nearvanilla.iceCream.modules.deathDb.DeathDbModule;
import com.nearvanilla.iceCream.modules.deathDb.gui.DeathListGui;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/** Opens the death browser for a player so staff can preview and restore an inventory. */
public class RestoreDeathCommand {
  private final DeathRepository repository;

  public RestoreDeathCommand(DeathRepository repository) {
    this.repository = repository;
  }

  /**
   * Opens the paginated death list for the given player.
   *
   * @param commandSourceStack the command source, must be a player
   * @param target the player whose deaths to browse; may be offline
   */
  @Command("restoredeath|rd <player>")
  @CommandDescription("Browse a player's recorded deaths and restore an inventory.")
  @Permission(DeathDbModule.RESTORE_PERMISSION)
  @SuppressWarnings("unused")
  public void restoreDeath(
      CommandSourceStack commandSourceStack,
      @Argument(value = "player", description = "The player whose deaths to browse")
          OfflinePlayer target) {
    if (!(commandSourceStack.getSender() instanceof Player staff)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }
    String name = target.getName();
    if (name == null || !(target.isOnline() || target.hasPlayedBefore())) {
      staff.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red><name> has never played on this server.</red>",
                  Placeholder.unparsed("name", name == null ? "That player" : name)));
      return;
    }
    DeathListGui.open(staff, repository, target.getUniqueId(), name, 0);
  }
}
```

- [ ] **Step 7: Wire the command and GUI listener into `DeathDbModule`**

Add imports:

```java
import com.nearvanilla.iceCream.modules.deathDb.commands.RestoreDeathCommand;
import com.nearvanilla.iceCream.modules.deathDb.events.DeathGuiListener;
```

Replace `registerCommands()` with:

```java
  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new RestoreDeathCommand(repository));
  }
```

Replace `registerEvents()` with:

```java
  @Override
  public void registerEvents() {
    var pluginManager = IceCream.instance.getServer().getPluginManager();
    pluginManager.registerEvents(new DeathRecordListener(repository), IceCream.instance);
    pluginManager.registerEvents(new DeathGuiListener(), IceCream.instance);
  }
```

- [ ] **Step 8: Compile and run the full build**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL, `DeathRepositoryTest` passes, the shadow jar builds.

- [ ] **Step 9: Smoke test the full flow (real client required)**

Seed more than one page. Stop the server, then in the `eval` tool (Python) copy an existing record 60 times with decreasing timestamps:

```python
import sqlite3
c = sqlite3.connect("D:/Projects/Java/IceCream/run/plugins/IceCream/deathdb.db")
for i in range(1, 61):
    c.execute("INSERT INTO deaths (player_id, died_at, world, x, y, z, inventory) "
              "SELECT player_id, died_at - ?, world, x, y, z, inventory FROM deaths ORDER BY id LIMIT 1",
              (i * 60000,))
c.commit()
```

Start `./gradlew runServer`, join as an op, and check each row. Record the outcome of every item.

| # | Action | Expected |
|---|---|---|
| 1 | `/rd <yourname>` | List GUI titled "Deaths of NAME (1/2)", 45 skulls newest first, next arrow at slot 53, no previous arrow |
| 2 | Click next, then previous | Page 2/2 shows the remainder with a previous arrow only, then back to page 1 |
| 3 | Click the newest skull | Preview opens. Helmet at slot 2, offhand at slot 7, hotbar on row 5 of the chest (slots 36-44), main inventory rows 2-4, red pane at 48, lime pane at 50 |
| 4 | Compare to what you carried when you died | Every item and its slot matches, armor and offhand correct, empty armor shows a gray "No helmet" style pane |
| 5 | Try to take items: left click, right click, shift click, number keys, drag across the GUI, double click, shift click an item from your own inventory | Nothing moves in either inventory |
| 6 | Click the red pane | GUI closes, inventory unchanged |
| 7 | Reopen, click the lime pane while carrying different items | Current items drop at your feet, your inventory equals the preview, success message, console logs the restore |
| 8 | Double click the lime pane quickly on a fresh preview | Restore happens once (only one set of drops) |
| 9 | `/rd <offline player>` then click the lime pane | List opens; restore refuses with the offline message |
| 10 | `/rd Nobody` (never joined) | "has never played" message |
| 11 | Run `/rd <yourname>` from the console | "only be used by players" |
| 12 | Without op or the permission | Command denied by Cloud |
| 13 | Die again, restart the server, `/rd` | New death present after the restart (flush on disable works) |

- [ ] **Step 10: Commit**

```bash
git add src/main
git commit -m "feat(deathdb): add death browser GUI and /restoredeath command"
```

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| Record deaths with time, world, position, full inventory | Task 2 (listener, snapshot) |
| SQLite, async, single thread, retention, schema versioning | Task 1 |
| `/restoredeath <player>` alias `/rd`, one permission, players only | Task 3 Step 6 |
| Paginated list GUI, 45 per page, arrows, info item | Task 3 Step 3 |
| Preview GUI layout (armor, offhand, main, hotbar, red and green panes) | Task 3 Step 4 |
| Read-only guarantee (clicks, drags, shift, number keys, double click) | Task 3 Step 5, smoke row 5 |
| Permission re-check on click, restored flag, offline and dead guards | Task 3 Steps 4 and 5 |
| Corrupt snapshot aborts without modifying the player | Task 2 Step 1 (`deserialize`), Task 3 Step 4 |
| Restore logged to console | Task 3 Step 4 |
| Config keys and default `max-records-per-player` | Task 2 Step 4 |
| Flush pending inserts on disable | Task 1 `close()`, Task 2 module `unregister()` |
| Tests for the repository only | Task 1 |

**Placeholder scan:** none. Every code step contains the full code.

**Type consistency:** `DeathRepository.findPage(UUID, int, int)` and `findRecord(long, UUID)` match their uses in `DeathListGui` and `DeathPreviewGui`. `DeathPage` accessors (`entries`, `page`, `pageCount`, `totalRecords`), `DeathSummary` accessors (`id`, `diedAtMillis`, `world`, `x`, `y`, `z`), and `InventorySnapshot` constants (`ARMOR_START`, `OFFHAND_SLOT`, `STORAGE_SLOTS`) are used consistently. `DeathDbModule.RESTORE_PERMISSION` is a compile time constant, as required by `@Permission`.

**Known risks to watch during execution**

- `getStorageContents`, `setStorageContents`, `getArmorContents`, `setItemInOffHand`, `getHolder(boolean)` and `editMeta` were checked with `javap` against the 26.3 API jar. `editMeta` and the Cloud `OfflinePlayer` argument parser were not checked directly; `SeenCommand` already uses the parser.
- Whether the inventory is still populated when `PlayerDeathEvent` fires at `MONITOR` is confirmed by the Task 2 Step 7 smoke test, not assumed.
- Closing or reopening an inventory inside `InventoryClickEvent` is safe here because every click is cancelled first. If a Paper warning appears, wrap the `gui.onClick` call in `Bukkit.getScheduler().runTask(...)`.
