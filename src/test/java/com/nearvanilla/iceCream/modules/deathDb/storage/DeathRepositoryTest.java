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
