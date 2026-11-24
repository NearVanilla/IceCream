package com.nearvanilla.iceCream.modules.commandTracker.storage;

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

class CommandLogRepositoryTest {
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @TempDir Path tempDir;
  private CommandLogRepository repository;

  @BeforeEach
  void open() throws SQLException {
    repository = new CommandLogRepository(tempDir.resolve("commands.db"));
  }

  @AfterEach
  void close() {
    repository.close();
  }

  private static CommandUse use(UUID player, String name, long time, String commandLine) {
    String command = commandLine.substring(1).split(" ")[0];
    return new CommandUse(
        time,
        CommandSource.PLAYER,
        player,
        name,
        command,
        commandLine,
        new CommandPosition("world", 1.5, 64.0, -3.25),
        false);
  }

  private void insert(UUID player, String name, long time, String commandLine) {
    repository.insert(use(player, name, time, commandLine)).join();
  }

  private List<Long> times(CommandLogQuery query) {
    return repository.findPage(query, 0, 50).join().entries().stream()
        .map(entry -> entry.use().executedAtMillis())
        .toList();
  }

  @Test
  void insertedCommandRoundTripsIncludingTimestampsBeyond32Bits() {
    long time = 5_000_000_000_000L;
    CommandUse use =
        new CommandUse(
            time,
            CommandSource.PLAYER,
            ALICE,
            "Alice",
            "tp",
            "/tp Bob",
            new CommandPosition("world_nether", 1.5, 64.0, -3.25),
            true);
    repository.insert(use).join();

    CommandLogPage page = repository.findPage(CommandLogQuery.all(), 0, 10).join();
    assertEquals(1, page.entries().size());
    assertEquals(use, page.entries().get(0).use());
  }

  @Test
  void consoleCommandsRoundTripWithoutPlayerOrPosition() {
    CommandUse use =
        new CommandUse(1000L, CommandSource.RCON, null, "Rcon", "say", "/say hi", null, false);
    repository.insert(use).join();

    assertEquals(
        use, repository.findPage(CommandLogQuery.all(), 0, 10).join().entries().get(0).use());
  }

  @Test
  void pagesAreNewestFirstAndRequestedPageIsClamped() {
    for (int i = 1; i <= 7; i++) {
      insert(ALICE, "Alice", i * 1000L, "/spawn");
    }

    CommandLogPage first = repository.findPage(CommandLogQuery.all(), 0, 3).join();
    assertEquals(3, first.pageCount());
    assertEquals(7, first.totalEntries());
    assertEquals(
        List.of(7000L, 6000L, 5000L),
        first.entries().stream().map(e -> e.use().executedAtMillis()).toList());

    CommandLogPage clamped = repository.findPage(CommandLogQuery.all(), 99, 3).join();
    assertEquals(2, clamped.page());
    assertEquals(1, clamped.entries().size());

    assertEquals(0, repository.findPage(CommandLogQuery.all(), -5, 3).join().page());
  }

  @Test
  void commandsRunInTheSameMillisecondKeepTheirOrder() {
    insert(ALICE, "Alice", 1000L, "/first");
    insert(ALICE, "Alice", 1000L, "/second");

    List<String> lines =
        repository.findPage(CommandLogQuery.all(), 0, 10).join().entries().stream()
            .map(e -> e.use().commandLine())
            .toList();
    assertEquals(List.of("/second", "/first"), lines);
  }

  @Test
  void emptyResultIsOneEmptyPage() {
    CommandLogPage page = repository.findPage(CommandLogQuery.all(), 0, 10).join();
    assertTrue(page.entries().isEmpty());
    assertEquals(0, page.page());
    assertEquals(1, page.pageCount());
    assertEquals(0, page.totalEntries());
  }

  @Test
  void filtersByPlayerIdAcrossNameChanges() {
    insert(ALICE, "Alice", 1000L, "/spawn");
    insert(ALICE, "Alicia", 2000L, "/spawn");
    insert(BOB, "Bob", 3000L, "/spawn");

    assertEquals(List.of(2000L, 1000L), times(CommandLogQuery.all().withPlayerId(ALICE)));
  }

  @Test
  void filtersByNameIgnoringCase() {
    insert(ALICE, "Alice", 1000L, "/spawn");
    insert(BOB, "Bob", 2000L, "/spawn");
    repository
        .insert(
            new CommandUse(
                3000L, CommandSource.CONSOLE, null, "Console", "op", "/op Bob", null, false))
        .join();

    assertEquals(List.of(1000L), times(CommandLogQuery.all().withName("aLiCe")));
    assertEquals(List.of(3000L), times(CommandLogQuery.all().withName("console")));
  }

  @Test
  void filtersByCommand() {
    insert(ALICE, "Alice", 1000L, "/tp Bob");
    insert(ALICE, "Alice", 2000L, "/spawn");
    insert(BOB, "Bob", 3000L, "/tp Alice");

    assertEquals(List.of(3000L, 1000L), times(CommandLogQuery.all().withCommand("tp")));
  }

  @Test
  void filtersByTimeRangeWithInclusiveStartAndExclusiveEnd() {
    for (int i = 1; i <= 5; i++) {
      insert(ALICE, "Alice", i * 1000L, "/spawn");
    }

    assertEquals(
        List.of(3000L, 2000L), times(CommandLogQuery.all().withSince(2000L).withUntil(4000L)));
    assertEquals(List.of(5000L, 4000L), times(CommandLogQuery.all().withSince(4000L)));
    assertEquals(List.of(1000L), times(CommandLogQuery.all().withUntil(2000L)));
  }

  @Test
  void searchMatchesTextIgnoringCaseAndTreatsWildcardsLiterally() {
    insert(ALICE, "Alice", 1000L, "/give Bob diamond 64");
    insert(ALICE, "Alice", 2000L, "/say 100% done");
    insert(ALICE, "Alice", 3000L, "/say 100 percent");
    insert(ALICE, "Alice", 4000L, "/say a_b");
    insert(ALICE, "Alice", 5000L, "/say axb");

    assertEquals(List.of(1000L), times(CommandLogQuery.all().withSearch("DIAMOND")));
    assertEquals(List.of(2000L), times(CommandLogQuery.all().withSearch("100%")));
    assertEquals(List.of(4000L), times(CommandLogQuery.all().withSearch("a_b")));
  }

  @Test
  void filtersCombine() {
    insert(ALICE, "Alice", 1000L, "/tp Bob");
    insert(ALICE, "Alice", 2000L, "/tp Carol");
    insert(BOB, "Bob", 3000L, "/tp Carol");

    assertEquals(
        List.of(2000L),
        times(CommandLogQuery.all().withPlayerId(ALICE).withCommand("tp").withSearch("carol")));
  }

  @Test
  void deleteOlderThanRemovesOnlyOlderCommands() {
    insert(ALICE, "Alice", 1000L, "/spawn");
    insert(BOB, "Bob", 2000L, "/spawn");
    insert(ALICE, "Alice", 3000L, "/spawn");

    assertEquals(2, repository.deleteOlderThan(3000L).join());
    assertEquals(List.of(3000L), times(CommandLogQuery.all()));
  }

  @Test
  void reopeningAnExistingFileKeepsItsData() throws SQLException {
    Path file = tempDir.resolve("reopen.db");
    try (CommandLogRepository first = new CommandLogRepository(file)) {
      first.insert(use(ALICE, "Alice", 1000L, "/spawn")).join();
    }
    try (CommandLogRepository second = new CommandLogRepository(file)) {
      assertEquals(1, second.findPage(CommandLogQuery.all(), 0, 10).join().totalEntries());
    }
  }

  @Test
  void closeWaitsForQueuedWrites() throws SQLException {
    Path file = tempDir.resolve("flush.db");
    try (CommandLogRepository first = new CommandLogRepository(file)) {
      for (int i = 0; i < 100; i++) {
        first.insert(use(ALICE, "Alice", i, "/spawn"));
      }
    }
    try (CommandLogRepository second = new CommandLogRepository(file)) {
      assertEquals(100, second.findPage(CommandLogQuery.all(), 0, 10).join().totalEntries());
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
    assertThrows(IllegalStateException.class, () -> new CommandLogRepository(file));
  }

  @Test
  void rejectsANonPositivePageSize() {
    assertThrows(
        IllegalArgumentException.class, () -> repository.findPage(CommandLogQuery.all(), 0, 0));
  }
}
