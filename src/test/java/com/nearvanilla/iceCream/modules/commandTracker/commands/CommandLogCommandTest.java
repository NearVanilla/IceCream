package com.nearvanilla.iceCream.modules.commandTracker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogQuery;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogRepository;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandPosition;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandSource;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandUse;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandLogCommandTest {
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final Map<String, Command> COMMANDS = Map.of("tp", command("teleport", "tp"));

  @TempDir Path tempDir;
  private ServerMock server;
  private PlayerMock staff;
  private CommandLogRepository repository;
  private CommandLogCommand command;
  private Logger previousLogger;

  private static Command command(String name, String... aliases) {
    return new Command(name, "", "/" + name, List.of(aliases)) {
      @Override
      public boolean execute(CommandSender sender, String label, String[] args) {
        return true;
      }
    };
  }

  @BeforeEach
  void setUp() throws SQLException {
    server = MockBukkit.mock();
    staff = server.addPlayer("Staff");
    repository = new CommandLogRepository(tempDir.resolve("commands.db"));
    CommandLogFormatter formatter =
        new CommandLogFormatter(
            DateTimeFormatter.ofPattern("HH:mm"), ZoneId.of("UTC"), world -> null);
    command =
        new CommandLogCommand(
            repository,
            formatter,
            2,
            Runnable::run,
            COMMANDS::get,
            Clock.fixed(NOW, ZoneId.of("UTC")));
    previousLogger = IceCream.logger;
    IceCream.logger = Logger.getLogger("CommandLogCommandTest");
  }

  @AfterEach
  void tearDown() {
    repository.close();
    MockBukkit.unmock();
    IceCream.logger = previousLogger;
  }

  private void insert(UUID player, String name, Duration ago, String command, String line) {
    repository
        .insert(
            new CommandUse(
                NOW.minus(ago).toEpochMilli(),
                CommandSource.PLAYER,
                player,
                name,
                command,
                line,
                new CommandPosition("world", 0, 64, 0),
                false))
        .join();
  }

  /** Runs a lookup and returns what the staff member was sent. */
  private List<String> lookup(int page, CommandLogFilters filters) {
    command.lookup(staff, page, filters);
    // The repository runs tasks in order, so this waits for the lookup to be sent.
    repository.findPage(CommandLogQuery.all(), 0, 1).join();
    List<String> lines = new ArrayList<>();
    Component message;
    while ((message = staff.nextComponentMessage()) != null) {
      lines.add(PlainTextComponentSerializer.plainText().serialize(message));
    }
    return lines;
  }

  private static CommandLogFilters filters(
      String player, String command, String since, String until, String search) {
    return new CommandLogFilters(player, command, since, until, search);
  }

  @Test
  void showsTheNewestCommandsFirstAPageAtATime() {
    insert(OFFLINE, "Bob", Duration.ofHours(3), "spawn", "/spawn");
    insert(OFFLINE, "Bob", Duration.ofHours(2), "home", "/home");
    insert(OFFLINE, "Bob", Duration.ofHours(1), "tp", "/tp Alice");

    List<String> first = lookup(1, filters(null, null, null, null, null));
    assertEquals("Command log page 1 of 2, 3 found", first.get(0));
    assertEquals("11:00 Bob /tp Alice", first.get(1));
    assertEquals("10:00 Bob /home", first.get(2));
    assertEquals("[Previous] | [Next]", first.get(3));

    List<String> second = lookup(2, filters(null, null, null, null, null));
    assertEquals(
        List.of("Command log page 2 of 2, 3 found", "09:00 Bob /spawn", "[Previous] | [Next]"),
        second);
  }

  @Test
  void filtersByAnOnlinePlayersUuid() {
    PlayerMock alice = server.addPlayer("Alice");
    insert(alice.getUniqueId(), "OldAliceName", Duration.ofHours(2), "spawn", "/spawn");
    insert(OFFLINE, "Bob", Duration.ofHours(1), "spawn", "/spawn");

    List<String> lines = lookup(1, filters("Alice", null, null, null, null));

    assertEquals(3, lines.size());
    assertEquals("10:00 OldAliceName /spawn", lines.get(2));
  }

  @Test
  void filtersByStoredNameForUnknownPlayers() {
    insert(OFFLINE, "Bob", Duration.ofHours(2), "spawn", "/spawn");
    insert(UUID.randomUUID(), "Carol", Duration.ofHours(1), "spawn", "/spawn");

    List<String> lines = lookup(1, filters("bob", null, null, null, null));

    assertEquals("10:00 Bob /spawn", lines.get(2));
    assertEquals(3, lines.size());
  }

  @Test
  void commandFilterResolvesAliasesAndIgnoresTheSlash() {
    insert(OFFLINE, "Bob", Duration.ofHours(2), "teleport", "/tp Alice");
    insert(OFFLINE, "Bob", Duration.ofHours(1), "spawn", "/spawn");

    List<String> lines = lookup(1, filters(null, "/tp", null, null, null));

    assertEquals(
        List.of(
            "Command log page 1 of 1, 1 found", "Filters: --command /tp", "10:00 Bob /tp Alice"),
        lines);
  }

  @Test
  void filtersByTimeRange() {
    insert(OFFLINE, "Bob", Duration.ofDays(3), "a", "/a");
    insert(OFFLINE, "Bob", Duration.ofHours(30), "b", "/b");
    insert(OFFLINE, "Bob", Duration.ofMinutes(10), "c", "/c");

    List<String> lines = lookup(1, filters(null, null, "2d", "1h", null));

    assertEquals("06:00 Bob /b", lines.get(2));
    assertEquals(3, lines.size());
  }

  @Test
  void searchesTheCommandLine() {
    insert(OFFLINE, "Bob", Duration.ofHours(2), "give", "/give Bob diamond 64");
    insert(OFFLINE, "Bob", Duration.ofHours(1), "give", "/give Bob dirt 1");

    List<String> lines = lookup(1, filters(null, null, null, null, "diamond"));

    assertEquals("10:00 Bob /give Bob diamond 64", lines.get(2));
    assertEquals(3, lines.size());
  }

  @Test
  void explainsATimeItCannotRead() {
    insert(OFFLINE, "Bob", Duration.ofHours(1), "spawn", "/spawn");

    List<String> lines = lookup(1, filters(null, null, "yesterday", null, null));

    assertEquals(1, lines.size());
    assertTrue(lines.get(0).startsWith("Could not read --since yesterday."), lines.get(0));
  }

  @Test
  void saysWhenNothingMatches() {
    assertEquals(
        List.of("No commands found."), lookup(1, filters("Nobody", null, null, null, null)));
  }

  @Test
  void commandMethodPassesTheSenderAndFilters() {
    insert(OFFLINE, "Bob", Duration.ofHours(1), "spawn", "/spawn");
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(staff);

    command.commandLog(source, 1, "Bob", null, null, null, null);
    repository.findPage(CommandLogQuery.all(), 0, 1).join();

    assertEquals(
        "Command log page 1 of 1, 1 found",
        PlainTextComponentSerializer.plainText().serialize(staff.nextComponentMessage()));
  }

  @Test
  void commandMethodShowsTheFirstPageWithoutOrBelowAPage() {
    insert(OFFLINE, "Bob", Duration.ofHours(3), "a", "/a");
    insert(OFFLINE, "Bob", Duration.ofHours(2), "b", "/b");
    insert(OFFLINE, "Bob", Duration.ofHours(1), "c", "/c");
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(staff);

    for (Integer page : new Integer[] {null, 0, -3}) {
      command.commandLog(source, page, null, null, null, null, null);
      repository.findPage(CommandLogQuery.all(), 0, 1).join();
      assertEquals(
          "Command log page 1 of 2, 3 found",
          PlainTextComponentSerializer.plainText().serialize(staff.nextComponentMessage()));
      while (staff.nextComponentMessage() != null) {}
    }
  }

  @Test
  void suggestsOnlinePlayersAndTheConsole() {
    server.addPlayer("Alice");

    List<String> names = command.suggestPlayers(null, null);

    assertTrue(names.containsAll(List.of("Staff", "Alice", server.getConsoleSender().getName())));
    assertNull(staff.nextComponentMessage());
  }
}
