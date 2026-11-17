package com.nearvanilla.iceCream.modules.worldTour;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.commands.WorldTourCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Walks a tour through its whole life with the real module, commands and listener working together:
 * start, participants joining, steps, takeovers, players leaving and rejoining, and the end.
 */
class WorldTourFlowTest {
  // The shipped configuration waits ten minutes before it opts a player who left out of the tour.
  private static final long TIMEOUT_TICKS = 10L * 60L * 20L;
  private static final String CONCLUDED =
      "✨ The World Tour has concluded. Thank you for participating!";

  private ServerMock server;
  private WorldMock overworld;
  private WorldMock nether;
  private WorldTourModule module;
  private WorldTourCommand command;
  private TourPlayer host;
  private TourPlayer guest;
  private TourPlayer other;

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
    other = addPlayer(server, "Other");
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void arrangeTheWorlds(boolean storm, long overworldTime, long netherTime) {
    overworld.setStorm(storm);
    overworld.setFullTime(overworldTime);
    nether.setFullTime(netherTime);
    overworld.setGameRule(GameRules.KEEP_INVENTORY, false);
    nether.setGameRule(GameRules.KEEP_INVENTORY, false);
  }

  private void drain(TourPlayer player) {
    while (nextMessage(player) != null) {
      // Read past the messages the test is not interested in.
    }
  }

  private void assertTheTourEnvironment() {
    assertEquals(1000L, overworld.getTime());
    assertEquals(1000L, nether.getTime());
    assertTheTourWeatherAndRules();
  }

  // The mock worlds' clocks run with the scheduler, so a test that lets ticks pass cannot assert
  // the time.
  private void assertTheTourWeatherAndRules() {
    assertFalse(overworld.hasStorm());
    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  private void assertTheWorldsAre(boolean storm, long overworldTime, long netherTime) {
    assertEquals(storm, overworld.hasStorm());
    assertEquals(overworldTime, overworld.getFullTime());
    assertEquals(netherTime, nether.getFullTime());
    assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.FALSE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void aTourRunsFromStartToEnd() {
    arrangeTheWorlds(true, 50000, 100500);

    // Start.
    command.startCommand(source(host));
    assertSame(host, module.getCurrentHost());
    assertTheTourEnvironment();

    // Participants join and are gathered.
    command.joinCommand(source(guest));
    command.joinCommand(source(other));
    host.teleport(new Location(nether, 4, 65, 8));
    command.tpallCommand(source(host));
    assertEquals(new Location(nether, 4, 65, 8), guest.getLocation());
    assertEquals(new Location(nether, 4, 65, 8), other.getLocation());

    // Steps: the host changes the weather and the time, and passes the glow on.
    command.weatherCommand(source(host), "thunder");
    command.timeCommand(source(host), "midnight");
    command.glowCommand(source(host), guest);
    assertTrue(overworld.isThundering());
    assertEquals(18000L, nether.getTime());
    assertSame(guest, module.getCurrentGlowing());

    // End.
    command.endCommand(source(host));
    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
    assertTheWorldsAre(true, 50000, 100500);
    assertEquals(Boolean.FALSE, joinedFlag(host));
    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(Boolean.FALSE, joinedFlag(other));
  }

  @Test
  void everyParticipantIsThankedWhenTheTourEnds() {
    command.startCommand(source(host));
    command.joinCommand(source(guest));
    nextMessage(guest);
    nextMessage(host);

    command.endCommand(source(host));

    assertEquals(CONCLUDED, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertEquals(CONCLUDED, nextMessage(host));
    assertEquals("✓ World Tour ended. Environment restored.", nextMessage(host));
    assertNull(nextMessage(other));
  }

  @Test
  void aSecondTourRestoresTheStateFromBeforeTheSecondTour() {
    arrangeTheWorlds(true, 50000, 100500);
    command.startCommand(source(host));
    command.endCommand(source(host));
    arrangeTheWorlds(false, 70000, 120000);

    command.startCommand(source(guest));
    assertTheTourEnvironment();
    command.endCommand(source(guest));

    assertTheWorldsAre(false, 70000, 120000);
  }

  @Test
  void theHostLeavingMidTourDoesNotEndIt() {
    arrangeTheWorlds(true, 50000, 100500);
    command.startCommand(source(host));
    command.joinCommand(source(guest));

    host.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertSame(host, module.getCurrentHost());
    assertEquals(Boolean.TRUE, joinedFlag(host));
    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertTheTourWeatherAndRules();
  }

  @Test
  void aParticipantLeavingMidTourIsOptedOutButTheTourGoesOn() {
    command.startCommand(source(host));
    command.joinCommand(source(guest));
    command.joinCommand(source(other));

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(Boolean.TRUE, joinedFlag(other));
    assertSame(host, module.getCurrentHost());
    assertTheTourWeatherAndRules();
    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void aParticipantWhoRejoinsInTimeIsStillGatheredByTheHost() {
    command.startCommand(source(host));
    command.joinCommand(source(guest));
    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS / 2);

    guest.reconnect();

    assertTrue(module.disconnectTasks.isEmpty());
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);
    host.teleport(new Location(nether, 4, 65, 8));

    command.tpallCommand(source(host));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(new Location(nether, 4, 65, 8), guest.getLocation());
  }

  @Test
  void aParticipantWhoWasOptedOutIsNotGathered() {
    command.startCommand(source(host));
    command.joinCommand(source(guest));
    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);
    guest.reconnect();
    Location guestsSpot = guest.getLocation();
    host.teleport(new Location(nether, 4, 65, 8));

    command.tpallCommand(source(host));

    assertEquals(guestsSpot, guest.getLocation());
  }

  @Test
  void aNewHostCanTakeOverFromAHostWhoLeftAndTheOldHostComesBackAsAParticipant() {
    arrangeTheWorlds(true, 50000, 100500);
    command.startCommand(source(host));
    command.takeoverCommand(source(other));
    host.disconnect();

    command.takeoverCommand(source(guest));
    host.reconnect();

    assertSame(guest, module.getCurrentHost());
    assertSame(guest, module.getCurrentGlowing());
    assertEquals(Boolean.TRUE, joinedFlag(host));
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertTheTourEnvironment();
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void theReturningOldHostCanNoLongerEndTheTourOrGatherThePlayers() {
    command.startCommand(source(host));
    host.disconnect();
    command.takeoverCommand(source(guest));
    host.reconnect();
    drain(host);

    command.endCommand(source(host));
    command.tpallCommand(source(host));

    assertEquals("✗ Only the World Tour host can end the tour.", nextMessage(host));
    assertEquals("✗ Only the World Tour host can use this command.", nextMessage(host));
    assertNull(nextMessage(host));
    assertSame(guest, module.getCurrentHost());
  }

  @Test
  void theOldHostCanLeaveTheTourAfterAHandover() {
    command.startCommand(source(host));
    command.takeoverCommand(source(guest));
    command.takeoverConfirmCommand(source(host));
    drain(host);

    command.leaveCommand(source(host));

    assertEquals("✓ You have left the World Tour.", nextMessage(host));
    assertEquals(Boolean.FALSE, joinedFlag(host));
    assertSame(guest, module.getCurrentHost());
  }

  @Test
  void aTourWhoseHostLeftCanBeForceEndedAndTheHostIsNoLongerAParticipantOnReturn() {
    arrangeTheWorlds(true, 50000, 100500);
    command.startCommand(source(host));
    host.disconnect();

    command.forceEndCommand(source(server.getConsoleSender()));
    host.reconnect();

    assertNull(module.getCurrentHost());
    assertTheWorldsAre(true, 50000, 100500);
    assertEquals(Boolean.FALSE, joinedFlag(host));
  }

  @Test
  void aParticipantWhoWasAwayWhenTheTourEndedIsNoLongerAParticipantOnReturn() {
    command.startCommand(source(host));
    command.joinCommand(source(guest));
    guest.disconnect();
    command.endCommand(source(host));

    guest.reconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void afterTheTourEndsEveryHostCommandIsRefused() {
    arrangeTheWorlds(false, 50000, 100500);
    command.startCommand(source(host));
    command.endCommand(source(host));
    drain(host);

    command.tpallCommand(source(host));
    command.glowCommand(source(host), guest);
    command.weatherCommand(source(host), "rain");
    command.timeCommand(source(host), "night");
    command.endCommand(source(host));
    command.forceEndCommand(source(host));

    assertEquals("✗ Only the World Tour host can use this command.", nextMessage(host));
    assertEquals("✗ Only the World Tour host can use this command.", nextMessage(host));
    assertEquals("✗ There is no World Tour host set.", nextMessage(host));
    assertEquals("✗ There is no World Tour host set.", nextMessage(host));
    assertEquals("✗ There is no World Tour host set.", nextMessage(host));
    assertEquals("✗ There is no World Tour host set.", nextMessage(host));
    assertNull(nextMessage(host));
    assertNull(module.getCurrentGlowing());
    assertTheWorldsAre(false, 50000, 100500);
  }

  @Test
  void aHandoverKeepsTheEnvironmentAndTheNewHostRestoresItWhenTheTourEnds() {
    arrangeTheWorlds(true, 50000, 100500);
    command.startCommand(source(host));
    command.takeoverCommand(source(guest));
    command.takeoverConfirmCommand(source(host));
    assertTheTourEnvironment();

    command.endCommand(source(guest));

    assertTheWorldsAre(true, 50000, 100500);
    assertNull(module.getCurrentHost());
  }
}
