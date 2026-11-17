package com.nearvanilla.iceCream.modules.worldTour.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.clickCommands;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRules;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Tests the commands that start a tour and the ones that end it, by the host or by force. */
class WorldTourCommandStartEndTest {
  private static final String PLAYERS_ONLY = "✗ This command can only be used by players.";
  private static final String NO_HOST = "✗ There is no World Tour host set.";
  private static final String CONCLUDED =
      "✨ The World Tour has concluded. Thank you for participating!";

  private ServerMock server;
  private WorldMock overworld;
  private WorldMock nether;
  private WorldTourModule module;
  private WorldTourCommand command;
  private TourPlayer host;
  private TourPlayer guest;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    overworld = server.addSimpleWorld("world");
    nether = server.addSimpleWorld("world_nether");
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    command = new WorldTourCommand(module);
    host = addPlayer(server, "Host");
    guest = addPlayer(server, "Guest");
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  /** Gives the worlds a weather, a time and a keepInventory rule that a tour would change. */
  private void arrangeTheWorlds() {
    overworld.setStorm(true);
    overworld.setThundering(true);
    overworld.setWeatherDuration(100);
    overworld.setThunderDuration(200);
    overworld.setFullTime(50000);
    overworld.setGameRule(GameRules.KEEP_INVENTORY, false);
    nether.setStorm(false);
    nether.setFullTime(100500);
    nether.setGameRule(GameRules.KEEP_INVENTORY, true);
  }

  private void startTourAs(TourPlayer player) {
    command.startCommand(source(player));
    nextMessage(player);
  }

  private void assertWorldsAreAsArranged() {
    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertEquals(100, overworld.getWeatherDuration());
    assertEquals(200, overworld.getThunderDuration());
    assertEquals(50000L, overworld.getFullTime());
    assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertFalse(nether.hasStorm());
    assertEquals(100500L, nether.getFullTime());
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  // Start

  @Test
  void startingMakesThePlayerTheHost() {
    command.startCommand(source(host));

    assertSame(host, module.getCurrentHost());
  }

  @Test
  void startingTellsTheHostWhatChanged() {
    command.startCommand(source(host));

    assertEquals(
        "✓ World Tour started! Time set to the configured start time, "
            + "weather set to clear, keepInventory enabled across all loaded dimensions.",
        nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void startingMarksTheHostAsAParticipant() {
    command.startCommand(source(host));

    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void startingMakesTheHostGlow() {
    command.startCommand(source(host));

    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void startingAppliesTheTourEnvironmentToEveryLoadedWorld() {
    arrangeTheWorlds();

    command.startCommand(source(host));

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertEquals(1000L, overworld.getTime());
    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertFalse(nether.hasStorm());
    assertEquals(1000L, nether.getTime());
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void startingIsRefusedWhileATourIsRunningAndPointsToTheTakeoverCommand() {
    startTourAs(host);

    command.startCommand(source(guest));

    Component message = guest.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "✗ A World Tour is already ongoing. Use /worldtour takeover to request host.",
        MessageTestUtils.plainText(message));
    assertEquals(List.of("/worldtour takeover"), clickCommands(message));
    assertNull(nextMessage(guest));
  }

  @Test
  void aRefusedStartChangesNothing() {
    startTourAs(host);
    overworld.setStorm(true);
    overworld.setFullTime(30500);

    command.startCommand(source(guest));

    assertSame(host, module.getCurrentHost());
    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
    assertNull(joinedFlag(guest));
    assertTrue(overworld.hasStorm());
    assertEquals(30500L, overworld.getFullTime());
  }

  @Test
  void startingIsRefusedWhileTheHostIsOffline() {
    startTourAs(host);
    host.disconnect();

    command.startCommand(source(guest));

    assertEquals(
        "✗ A World Tour is already ongoing. Use /worldtour takeover to request host.",
        nextMessage(guest));
    assertSame(host, module.getCurrentHost());
  }

  @Test
  void aNewTourCanStartAfterTheLastOneEnded() {
    startTourAs(host);
    command.endCommand(source(host));
    nextMessage(host);
    nextMessage(host);

    command.startCommand(source(guest));

    assertSame(guest, module.getCurrentHost());
    assertSame(guest, module.getCurrentGlowing());
    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void startingIsRefusedForTheConsole() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.startCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertNull(module.getCurrentHost());
  }

  // End

  @Test
  void endingWhenNoTourIsRunningTellsThePlayerThereIsNoHost() {
    command.endCommand(source(host));

    assertEquals(NO_HOST, nextMessage(host));
    assertNull(nextMessage(host));
    assertNull(module.getCurrentHost());
  }

  @Test
  void endingAsSomeoneElseIsRefused() {
    startTourAs(host);
    setJoined(guest, true);

    command.endCommand(source(guest));

    assertEquals("✗ Only the World Tour host can end the tour.", nextMessage(guest));
    assertNull(nextMessage(guest));
    assertSame(host, module.getCurrentHost());
    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void aRefusedEndLeavesTheTourEnvironment() {
    arrangeTheWorlds();
    startTourAs(host);

    command.endCommand(source(guest));

    assertFalse(overworld.hasStorm());
    assertEquals(1000L, overworld.getTime());
  }

  @Test
  void theHostEndingTheTourClearsTheHostAndTheGlow() {
    startTourAs(host);

    command.endCommand(source(host));

    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void theHostEndingTheTourRestoresTheEnvironment() {
    arrangeTheWorlds();
    startTourAs(host);

    command.endCommand(source(host));

    assertWorldsAreAsArranged();
  }

  @Test
  void theHostEndingTheTourThanksThemThenConfirmsTheEnd() {
    startTourAs(host);

    command.endCommand(source(host));

    assertEquals(CONCLUDED, nextMessage(host));
    assertEquals("✓ World Tour ended. Environment restored.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void endingTheTourOptsOutParticipantsAndThanksOnlyThem() {
    startTourAs(host);
    command.joinCommand(source(guest));
    nextMessage(guest);
    TourPlayer bystander = addPlayer(server, "Bystander");

    command.endCommand(source(host));

    assertEquals(Boolean.FALSE, joinedFlag(host));
    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(CONCLUDED, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertNull(joinedFlag(bystander));
    assertNull(nextMessage(bystander));
  }

  @Test
  void endingTwiceTellsThePlayerThereIsNoHostTheSecondTime() {
    startTourAs(host);
    command.endCommand(source(host));
    nextMessage(host);
    nextMessage(host);

    command.endCommand(source(host));

    assertEquals(NO_HOST, nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void endingIsRefusedForTheConsole() {
    startTourAs(host);
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.endCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertSame(host, module.getCurrentHost());
  }

  // Force end

  @Test
  void forceEndingWhenNoTourIsRunningTellsTheSenderThereIsNoHost() {
    command.forceEndCommand(source(guest));

    assertEquals(NO_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
  }

  @Test
  void forceEndingWhenNoTourIsRunningTellsTheConsoleThereIsNoHost() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.forceEndCommand(source(console));

    assertEquals(NO_HOST, nextMessage(console));
    assertNull(nextMessage(console));
  }

  @Test
  void theConsoleCanForceEndATour() {
    arrangeTheWorlds();
    startTourAs(host);
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.forceEndCommand(source(console));

    assertEquals("✓ World Tour force-ended. Environment restored.", nextMessage(console));
    assertNull(nextMessage(console));
    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertWorldsAreAsArranged();
  }

  @Test
  void forceEndingThanksTheParticipants() {
    startTourAs(host);
    command.joinCommand(source(guest));
    nextMessage(guest);

    command.forceEndCommand(source(server.getConsoleSender()));

    assertEquals(Boolean.FALSE, joinedFlag(host));
    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(CONCLUDED, nextMessage(host));
    assertEquals(CONCLUDED, nextMessage(guest));
  }

  @Test
  void aPlayerWhoIsNotTheHostCanForceEndATour() {
    startTourAs(host);

    command.forceEndCommand(source(guest));

    assertEquals("✓ World Tour force-ended. Environment restored.", nextMessage(guest));
    assertNull(nextMessage(guest));
    assertNull(module.getCurrentHost());
  }

  @Test
  void forceEndingWorksWhileTheHostIsOffline() {
    arrangeTheWorlds();
    startTourAs(host);
    host.disconnect();

    command.forceEndCommand(source(guest));

    assertNull(module.getCurrentHost());
    assertWorldsAreAsArranged();
  }
}
