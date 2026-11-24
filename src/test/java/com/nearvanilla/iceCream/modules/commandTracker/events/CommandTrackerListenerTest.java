package com.nearvanilla.iceCream.modules.commandTracker.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.commandTracker.CommandRules;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogQuery;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogRepository;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandPosition;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandSource;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandUse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandTrackerListenerTest {
  private static final long NOW = 1_700_000_000_000L;
  private static final Map<String, Command> COMMANDS =
      Map.of(
          "login", command("login", "l"),
          "l", command("login", "l"),
          "teleport", command("teleport", "tp"),
          "minecraft:teleport", command("teleport", "tp"));

  @TempDir Path tempDir;
  private ServerMock server;
  private PlayerMock player;
  private CommandLogRepository repository;
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
    player = server.addPlayer("Alice");
    player.teleport(new Location(server.addSimpleWorld("world"), 10.5, 64, -20.25));
    repository = new CommandLogRepository(tempDir.resolve("commands.db"));
    previousLogger = IceCream.logger;
    IceCream.logger = Logger.getLogger("CommandTrackerListenerTest");
  }

  @AfterEach
  void tearDown() {
    repository.close();
    MockBukkit.unmock();
    IceCream.logger = previousLogger;
  }

  private CommandTrackerListener listener(CommandRules rules, boolean recordConsole) {
    return new CommandTrackerListener(repository, rules, recordConsole, COMMANDS::get, () -> NOW);
  }

  private CommandTrackerListener listener() {
    return listener(
        new CommandRules(List.of("spawn"), List.of(), List.of("login"), List.of("msg")), true);
  }

  private List<CommandUse> stored() {
    return repository.findPage(CommandLogQuery.all(), 0, 50).join().entries().stream()
        .map(entry -> entry.use())
        .toList();
  }

  @Test
  void recordsAPlayerCommandWithItsPosition() {
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/give Bob diamond"));

    CommandUse use = stored().get(0);
    assertEquals(NOW, use.executedAtMillis());
    assertEquals(CommandSource.PLAYER, use.source());
    assertEquals(player.getUniqueId(), use.playerId());
    assertEquals("Alice", use.name());
    assertEquals("give", use.command());
    assertEquals("/give Bob diamond", use.commandLine());
    assertEquals(new CommandPosition("world", 10.5, 64, -20.25), use.position());
    assertFalse(use.cancelled());
  }

  @Test
  void storesTheRealCommandNameForAnAliasOrNamespacedLabel() {
    // tp is not registered in this lookup, so it is stored as typed.
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/tp Bob"));
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/minecraft:teleport Bob"));

    List<CommandUse> uses = stored();
    assertEquals("teleport", uses.get(0).command());
    assertEquals("/minecraft:teleport Bob", uses.get(0).commandLine());
    assertEquals("tp", uses.get(1).command());
  }

  @Test
  void recordsCancelledCommandsAsCancelled() {
    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/op Alice");
    event.setCancelled(true);

    listener().onPlayerCommand(event);

    assertTrue(stored().get(0).cancelled());
  }

  @Test
  void skipsIgnoredCommands() {
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/spawn"));
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/essentials:SPAWN now"));

    assertTrue(stored().isEmpty());
  }

  @Test
  void onlyRecordsTrackedCommandsWhenSome() {
    CommandTrackerListener listener =
        listener(new CommandRules(List.of(), List.of("teleport"), List.of(), List.of()), true);

    listener.onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/give Bob diamond"));
    listener.onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/minecraft:teleport Bob"));

    assertEquals(List.of("teleport"), stored().stream().map(CommandUse::command).toList());
  }

  @Test
  void redactsByAliasToo() {
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/l hunter2"));
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/msg Bob a secret"));

    List<CommandUse> uses = stored();
    assertEquals("/msg Bob " + CommandRules.REDACTED, uses.get(0).commandLine());
    assertEquals("/l " + CommandRules.REDACTED, uses.get(1).commandLine());
    assertEquals("login", uses.get(1).command());
  }

  @Test
  void skipsAnEmptyCommand() {
    listener().onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/"));

    assertTrue(stored().isEmpty());
  }

  @Test
  void recordsConsoleAndRconCommandsWhenEnabled() {
    RemoteConsoleCommandSender rcon = mock(RemoteConsoleCommandSender.class);
    when(rcon.getName()).thenReturn("Rcon");

    listener().onServerCommand(new ServerCommandEvent(server.getConsoleSender(), "say hi"));
    listener().onServerCommand(new ServerCommandEvent(rcon, "/whitelist add Bob"));

    List<CommandUse> uses = stored();
    assertEquals(CommandSource.RCON, uses.get(0).source());
    assertEquals("Rcon", uses.get(0).name());
    assertEquals("/whitelist add Bob", uses.get(0).commandLine());
    assertEquals(CommandSource.CONSOLE, uses.get(1).source());
    assertEquals("/say hi", uses.get(1).commandLine());
    assertNull(uses.get(1).playerId());
    assertNull(uses.get(1).position());
  }

  @Test
  void skipsConsoleCommandsWhenDisabled() {
    CommandTrackerListener listener =
        listener(new CommandRules(List.of(), List.of(), List.of(), List.of()), false);

    listener.onServerCommand(new ServerCommandEvent(server.getConsoleSender(), "say hi"));

    assertTrue(stored().isEmpty());
  }

  @Test
  void neverRecordsCommandBlocks() {
    BlockCommandSender block = mock(BlockCommandSender.class);
    when(block.getName()).thenReturn("@");

    listener().onServerCommand(new ServerCommandEvent(block, "say hi"));

    assertTrue(stored().isEmpty());
  }
}
