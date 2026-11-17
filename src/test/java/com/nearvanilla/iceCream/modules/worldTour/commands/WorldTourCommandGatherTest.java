package com.nearvanilla.iceCream.modules.worldTour.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Tests the commands the host uses to gather the participants and to pass on the glow. */
class WorldTourCommandGatherTest {
  private static final String PLAYERS_ONLY = "✗ This command can only be used by players.";
  private static final String NOT_HOST = "✗ Only the World Tour host can use this command.";

  private ServerMock server;
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
    server.addSimpleWorld("world");
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

  private void startTourAs(TourPlayer player) {
    command.startCommand(source(player));
    nextMessage(player);
  }

  private Location inTheNether() {
    return new Location(nether, 10.5, 70, -20.5, 90f, 15f);
  }

  // Teleport everyone to the host

  @Test
  void theHostGathersEveryOnlineParticipantAtTheirLocation() {
    startTourAs(host);
    setJoined(guest, true);
    setJoined(other, true);
    host.teleport(inTheNether());

    command.tpallCommand(source(host));

    assertEquals(inTheNether(), guest.getLocation());
    assertEquals(inTheNether(), other.getLocation());
    assertSame(nether, guest.getWorld());
  }

  @Test
  void theHostStaysWhereTheyAre() {
    startTourAs(host);
    setJoined(guest, true);
    host.teleport(inTheNether());

    command.tpallCommand(source(host));

    assertEquals(inTheNether(), host.getLocation());
  }

  @Test
  void eachParticipantIsToldTheyWereTeleported() {
    startTourAs(host);
    setJoined(guest, true);
    setJoined(other, true);

    command.tpallCommand(source(host));

    assertEquals("✓ You have been teleported to the World Tour host.", nextMessage(guest));
    assertNull(nextMessage(guest));
    assertEquals("✓ You have been teleported to the World Tour host.", nextMessage(other));
    assertNull(nextMessage(other));
  }

  @Test
  void theHostIsToldHowManyParticipantsWereTeleported() {
    startTourAs(host);
    setJoined(guest, true);
    setJoined(other, true);

    command.tpallCommand(source(host));

    assertEquals("✓ All participants have been teleported to you!", nextMessage(host));
    assertEquals("2 participant(s) teleported.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void aSingleParticipantIsCounted() {
    startTourAs(host);
    setJoined(guest, true);

    command.tpallCommand(source(host));

    assertEquals("✓ All participants have been teleported to you!", nextMessage(host));
    assertEquals("1 participant(s) teleported.", nextMessage(host));
  }

  @Test
  void withoutParticipantsTheHostIsOnlyToldItWorked() {
    startTourAs(host);

    command.tpallCommand(source(host));

    assertEquals("✓ All participants have been teleported to you!", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void playersWhoDidNotJoinStayWhereTheyAre() {
    startTourAs(host);
    setJoined(other, false);
    Location guestsSpot = guest.getLocation();
    Location othersSpot = other.getLocation();
    host.teleport(inTheNether());

    command.tpallCommand(source(host));

    assertEquals(guestsSpot, guest.getLocation());
    assertEquals(othersSpot, other.getLocation());
    assertNull(nextMessage(guest));
    assertNull(nextMessage(other));
    assertEquals("✓ All participants have been teleported to you!", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void participantsWhoAreOfflineAreSkippedAndNotCounted() {
    startTourAs(host);
    setJoined(guest, true);
    setJoined(other, true);
    host.teleport(inTheNether());
    Location othersSpot = other.getLocation();
    other.disconnect();

    command.tpallCommand(source(host));

    assertEquals(inTheNether(), guest.getLocation());
    assertEquals(othersSpot, other.getLocation());
    assertEquals("✓ All participants have been teleported to you!", nextMessage(host));
    assertEquals("1 participant(s) teleported.", nextMessage(host));
  }

  @Test
  void aPlayerWhoIsNotTheHostCannotGatherTheParticipants() {
    startTourAs(host);
    setJoined(other, true);
    host.teleport(inTheNether());
    Location othersSpot = other.getLocation();

    command.tpallCommand(source(guest));

    assertEquals(NOT_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertEquals(othersSpot, other.getLocation());
    assertNull(nextMessage(other));
  }

  @Test
  void gatheringNeedsATourToBeRunning() {
    setJoined(other, true);
    Location othersSpot = other.getLocation();

    command.tpallCommand(source(host));

    assertEquals(NOT_HOST, nextMessage(host));
    assertNull(nextMessage(host));
    assertEquals(othersSpot, other.getLocation());
  }

  @Test
  void gatheringIsRefusedForTheConsole() {
    startTourAs(host);
    setJoined(guest, true);
    Location guestsSpot = guest.getLocation();
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.tpallCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertEquals(guestsSpot, guest.getLocation());
  }

  // Pass on the glow

  @Test
  void theHostPassesTheGlowToAnotherPlayer() {
    startTourAs(host);

    command.glowCommand(source(host), guest);

    assertSame(guest, module.getCurrentGlowing());
    assertTrue(guest.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void passingOnTheGlowTellsTheHostAndTheTarget() {
    startTourAs(host);

    command.glowCommand(source(host), guest);

    assertEquals("✓ Glowing effect transferred to Guest.", nextMessage(host));
    assertNull(nextMessage(host));
    assertEquals("✓ You are now glowing for the World Tour!", nextMessage(guest));
    assertNull(nextMessage(guest));
  }

  @Test
  void passingOnTheGlowAgainTakesItFromThePreviousTarget() {
    startTourAs(host);
    command.glowCommand(source(host), guest);

    command.glowCommand(source(host), other);

    assertSame(other, module.getCurrentGlowing());
    assertTrue(other.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void theHostCanTakeTheGlowBack() {
    startTourAs(host);
    command.glowCommand(source(host), guest);

    command.glowCommand(source(host), host);

    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void aPlayerWhoIsNotTheHostCannotPassOnTheGlow() {
    startTourAs(host);

    command.glowCommand(source(guest), other);

    assertEquals(NOT_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertNull(nextMessage(other));
    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(other.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void passingOnTheGlowNeedsATourToBeRunning() {
    command.glowCommand(source(host), guest);

    assertEquals(NOT_HOST, nextMessage(host));
    assertNull(module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void passingOnTheGlowIsRefusedForTheConsole() {
    startTourAs(host);
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.glowCommand(source(console), guest);

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertSame(host, module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }
}
