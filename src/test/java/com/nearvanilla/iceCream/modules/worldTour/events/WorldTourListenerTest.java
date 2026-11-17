package com.nearvanilla.iceCream.modules.worldTour.events;

import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.quitEvent;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
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
import net.kyori.adventure.text.Component;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class WorldTourListenerTest {
  // The shipped configuration waits ten minutes before it opts a player who left out of the tour.
  private static final long TIMEOUT_TICKS = 10L * 60L * 20L;

  private ServerMock server;
  private WorldTourModule module;
  private TourPlayer host;
  private TourPlayer guest;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    host = addPlayer(server, "Host");
    guest = addPlayer(server, "Guest");
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void startTour() {
    module.setCurrentHost(host);
    setJoined(host, true);
    module.setGlowing(host);
  }

  private PlayerJoinEvent joinEvent(TourPlayer player) {
    return new PlayerJoinEvent(player, Component.text("joined"));
  }

  @Test
  void aParticipantWhoLeavesIsOptedOutOnceTheTimeoutHasPassed() {
    startTour();
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void theOptedOutParticipantsDataIsSaved() {
    startTour();
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertEquals(1, guest.saveCount());
  }

  @Test
  void aParticipantWhoLeavesIsKeptUntilTheTimeoutHasPassed() {
    startTour();
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS - 1);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());

    server.getScheduler().performTicks(1);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void theTimeoutFollowsTheConfiguredMinutes() {
    // Registering again reads the new timeout, and the first listener goes so only one handles it.
    HandlerList.unregisterAll(IceCream.instance);
    IceCream.config.set("modules.worldtour.failsafe-timeout-minutes", 2);
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    startTour();
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(2L * 60L * 20L - 1);
    assertEquals(Boolean.TRUE, joinedFlag(guest));
    server.getScheduler().performTicks(1);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void aParticipantWhoLeavesWhenNoTourIsRunningIsStillOptedOut() {
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void aPlayerWhoNeverTookPartIsLeftAlone() {
    startTour();

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertNull(joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void aPlayerWhoAlreadyOptedOutIsLeftAlone() {
    startTour();
    setJoined(guest, false);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void aParticipantWhoComesBackBeforeTheTimeoutStaysInTheTour() {
    startTour();
    setJoined(guest, true);
    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS / 2);

    guest.reconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void aParticipantWhoComesBackAndLeavesAgainStartsANewTimeout() {
    startTour();
    setJoined(guest, true);
    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS - 100);
    guest.reconnect();

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS - 1);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());

    server.getScheduler().performTicks(1);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(1, guest.saveCount());
  }

  @Test
  void aParticipantWhoIsOnlineWhenTheTimeoutEndsIsKept() {
    startTour();
    setJoined(guest, true);

    server.getPluginManager().callEvent(quitEvent(guest));
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertTrue(guest.isOnline());
    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void eachParticipantWhoLeavesHasATimeoutOfTheirOwn() {
    TourPlayer second = addPlayer(server, "Second");
    startTour();
    setJoined(guest, true);
    setJoined(second, true);

    guest.disconnect();
    server.getScheduler().performTicks(100);
    second.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS - 100);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(Boolean.TRUE, joinedFlag(second));

    server.getScheduler().performTicks(100);

    assertEquals(Boolean.FALSE, joinedFlag(second));
    assertEquals(1, guest.saveCount());
    assertEquals(1, second.saveCount());
  }

  @Test
  void aParticipantWhoLeavesDoesNotEndTheTourOrMoveTheHost() {
    startTour();
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertSame(host, module.getCurrentHost());
    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void theHostIsNeverOptedOutWhenTheyLeave() {
    startTour();

    host.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertEquals(Boolean.TRUE, joinedFlag(host));
    assertEquals(0, host.saveCount());
  }

  @Test
  void theTourContinuesWhenTheHostLeaves() {
    startTour();

    host.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);

    assertSame(host, module.getCurrentHost());
  }

  @Test
  void theHostWhoLeavesAndReturnsIsStillTheHost() {
    startTour();
    host.disconnect();
    server.getScheduler().performTicks(TIMEOUT_TICKS / 2);

    host.reconnect();

    assertSame(host, module.getCurrentHost());
    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void theGlowIsClearedWhenTheGlowingPlayerLeaves() {
    startTour();
    assertSame(host, module.getCurrentGlowing());

    host.disconnect();

    assertNull(module.getCurrentGlowing());
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void aGlowingParticipantWhoLeavesLosesTheGlowAndIsStillOptedOutLater() {
    startTour();
    setJoined(guest, true);
    module.setGlowing(guest);

    guest.disconnect();

    assertNull(module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));

    server.getScheduler().performTicks(TIMEOUT_TICKS);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void theGlowStaysWhenSomeoneElseLeaves() {
    startTour();
    setJoined(guest, true);

    guest.disconnect();

    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void aStaleParticipationFlagIsClearedWhenAPlayerJoinsAndNoTourIsRunning() {
    setJoined(guest, true);

    server.getPluginManager().callEvent(joinEvent(guest));

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void theParticipationFlagIsKeptWhenAPlayerJoinsDuringATour() {
    startTour();
    setJoined(guest, true);

    server.getPluginManager().callEvent(joinEvent(guest));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
  }

  @Test
  void aPlayerWithoutAFlagIsNotGivenOneWhenJoining() {
    server.getPluginManager().callEvent(joinEvent(guest));

    assertNull(joinedFlag(guest));
  }

  @Test
  void aPlayerWhoOptedOutStaysOptedOutWhenJoining() {
    setJoined(guest, false);

    server.getPluginManager().callEvent(joinEvent(guest));

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void joiningOnlyClearsTheFlagOfThePlayerWhoJoined() {
    TourPlayer other = addPlayer(server, "Other");
    setJoined(guest, true);
    setJoined(other, true);

    server.getPluginManager().callEvent(joinEvent(guest));

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals(Boolean.TRUE, joinedFlag(other));
  }

  @Test
  void aPlayerWhoRejoinsAfterTheTourEndedIsNoLongerAParticipant() {
    startTour();
    setJoined(guest, true);
    guest.disconnect();

    module.clearHost(true);
    guest.reconnect();

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    server.getScheduler().performTicks(TIMEOUT_TICKS * 2);
    assertEquals(0, guest.saveCount());
  }
}
