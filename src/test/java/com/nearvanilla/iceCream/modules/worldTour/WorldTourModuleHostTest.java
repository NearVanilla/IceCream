package com.nearvanilla.iceCream.modules.worldTour;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import java.util.UUID;
import org.bukkit.GameRules;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Tests the host, the glowing player, the takeover requests and the failsafe tasks of the tour. */
class WorldTourModuleHostTest {
  private ServerMock server;
  private WorldMock world;
  private WorldTourModule module;
  private TourPlayer host;
  private TourPlayer guest;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    world = server.addSimpleWorld("world");
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

  private BukkitTask scheduledTask(int[] runs) {
    return server.getScheduler().runTaskLater(IceCream.instance, () -> runs[0]++, 10);
  }

  @Test
  void thereIsNoHostBeforeATourStarts() {
    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
  }

  @Test
  void settingTheHostRecordsThem() {
    module.setCurrentHost(host);

    assertSame(host, module.getCurrentHost());
  }

  @Test
  void settingTheFirstHostTellsNobodyAnything() {
    module.setCurrentHost(host);

    assertNull(nextMessage(host));
    assertNull(nextMessage(guest));
  }

  @Test
  void replacingTheHostTellsThePreviousHost() {
    module.setCurrentHost(host);

    module.setCurrentHost(guest);

    assertSame(guest, module.getCurrentHost());
    assertEquals("Guest has taken over as World Tour host.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void replacingTheHostDoesNotTellTheNewHost() {
    module.setCurrentHost(host);

    module.setCurrentHost(guest);

    assertNull(nextMessage(guest));
  }

  @Test
  void settingTheSameHostAgainSendsNothing() {
    module.setCurrentHost(host);

    module.setCurrentHost(host);

    assertSame(host, module.getCurrentHost());
    assertNull(nextMessage(host));
  }

  @Test
  void clearingTheHostRemovesThem() {
    module.setCurrentHost(host);

    module.clearHost(false);

    assertNull(module.getCurrentHost());
  }

  @Test
  void clearingTheHostRemovesTheGlowingPlayer() {
    module.setCurrentHost(host);
    module.setGlowing(guest);

    module.clearHost(false);

    assertNull(module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void clearingTheHostWithoutRestoringLeavesTheTourEnvironment() {
    world.setStorm(true);
    world.setFullTime(30500);
    module.setCurrentHost(host);
    module.applyTourEnvironment();

    module.clearHost(false);

    assertFalse(world.hasStorm());
    assertEquals(1000L, world.getTime());
    assertEquals(Boolean.TRUE, world.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void clearingTheHostWithRestoringPutsTheEnvironmentBack() {
    world.setStorm(true);
    world.setFullTime(30500);
    module.setCurrentHost(host);
    module.applyTourEnvironment();

    module.clearHost(true);

    assertTrue(world.hasStorm());
    assertEquals(30500L, world.getFullTime());
    assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void clearingTheHostDropsTheirPendingTakeoverRequest() {
    module.setCurrentHost(host);
    module.setPendingTakeover(host.getUniqueId(), guest.getUniqueId());

    module.clearHost(false);

    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void clearingTheHostKeepsTheRequestsMadeToOtherHosts() {
    UUID otherHost = UUID.randomUUID();
    module.setCurrentHost(host);
    module.setPendingTakeover(otherHost, guest.getUniqueId());

    module.clearHost(false);

    assertEquals(guest.getUniqueId(), module.removePendingTakeover(otherHost));
  }

  @Test
  void clearingWhenThereIsNoHostIsHarmless() {
    module.clearHost(true);

    assertNull(module.getCurrentHost());
  }

  @Test
  void glowingGivesThePlayerAnEndlessHiddenGlowingEffect() {
    module.setGlowing(guest);

    PotionEffect effect = guest.getPotionEffect(PotionEffectType.GLOWING);
    assertNotNull(effect);
    assertEquals(1, effect.getAmplifier());
    assertEquals(Integer.MAX_VALUE, effect.getDuration());
    assertFalse(effect.hasParticles());
    assertFalse(effect.isAmbient());
  }

  @Test
  void glowingRecordsThePlayer() {
    module.setGlowing(guest);

    assertSame(guest, module.getCurrentGlowing());
  }

  @Test
  void glowingMovesTheEffectToTheNewPlayer() {
    module.setGlowing(host);

    module.setGlowing(guest);

    assertSame(guest, module.getCurrentGlowing());
    assertTrue(guest.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void glowingTheSamePlayerAgainKeepsTheEffect() {
    module.setGlowing(guest);

    module.setGlowing(guest);

    assertSame(guest, module.getCurrentGlowing());
    assertTrue(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void clearingTheGlowRemovesTheEffect() {
    module.setGlowing(guest);

    module.clearGlowing();

    assertNull(module.getCurrentGlowing());
    assertFalse(guest.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void clearingTheGlowWhenNobodyGlowsIsHarmless() {
    module.clearGlowing();

    assertNull(module.getCurrentGlowing());
  }

  @Test
  void aPendingTakeoverIsHandedBackOnce() {
    module.setPendingTakeover(host.getUniqueId(), guest.getUniqueId());

    assertEquals(guest.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aHostWithoutARequestHasNoPendingTakeover() {
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aNewTakeoverRequestReplacesTheOldOne() {
    TourPlayer third = addPlayer(server, "Third");
    module.setPendingTakeover(host.getUniqueId(), guest.getUniqueId());

    module.setPendingTakeover(host.getUniqueId(), third.getUniqueId());

    assertEquals(third.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void takeoverRequestsOfDifferentHostsAreIndependent() {
    UUID otherHost = UUID.randomUUID();
    module.setPendingTakeover(host.getUniqueId(), guest.getUniqueId());
    module.setPendingTakeover(otherHost, host.getUniqueId());

    assertEquals(guest.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
    assertEquals(host.getUniqueId(), module.removePendingTakeover(otherHost));
  }

  @Test
  void endingTheTourClearsTheHostAndTheGlow() {
    module.setCurrentHost(host);
    module.setGlowing(host);

    module.endTour();

    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void endingTheTourRestoresTheEnvironment() {
    world.setStorm(true);
    world.setFullTime(30500);
    module.setCurrentHost(host);
    module.applyTourEnvironment();

    module.endTour();

    assertTrue(world.hasStorm());
    assertEquals(30500L, world.getFullTime());
    assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void endingTheTourOptsOutEveryOnlineParticipantAndThanksThem() {
    module.setCurrentHost(host);
    setJoined(host, true);
    setJoined(guest, true);

    module.endTour();

    assertEquals(Boolean.FALSE, joinedFlag(host));
    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals("✨ The World Tour has concluded. Thank you for participating!", nextMessage(host));
    assertNull(nextMessage(host));
    assertEquals(
        "✨ The World Tour has concluded. Thank you for participating!", nextMessage(guest));
    assertNull(nextMessage(guest));
  }

  @Test
  void endingTheTourLeavesThePlayersWhoDidNotTakePart() {
    TourPlayer optedOut = addPlayer(server, "OptedOut");
    setJoined(optedOut, false);
    module.setCurrentHost(host);
    setJoined(host, true);

    module.endTour();

    assertNull(joinedFlag(guest));
    assertNull(nextMessage(guest));
    assertEquals(Boolean.FALSE, joinedFlag(optedOut));
    assertNull(nextMessage(optedOut));
  }

  @Test
  void endingTheTourDropsTheHostsPendingTakeoverRequest() {
    module.setCurrentHost(host);
    module.setPendingTakeover(host.getUniqueId(), guest.getUniqueId());

    module.endTour();

    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aScheduledFailsafeTaskIsKeptUntilItIsCancelled() {
    int[] runs = {0};
    BukkitTask task = scheduledTask(runs);

    module.scheduleDisconnectTask(guest.getUniqueId(), task);

    assertSame(task, module.disconnectTasks.get(guest.getUniqueId()));
    assertFalse(task.isCancelled());
  }

  @Test
  void schedulingAnotherFailsafeTaskForAPlayerCancelsTheFirst() {
    int[] runs = {0};
    BukkitTask first = scheduledTask(runs);
    BukkitTask second = scheduledTask(runs);
    module.scheduleDisconnectTask(guest.getUniqueId(), first);

    module.scheduleDisconnectTask(guest.getUniqueId(), second);
    server.getScheduler().performTicks(10);

    assertTrue(first.isCancelled());
    assertFalse(second.isCancelled());
    assertSame(second, module.disconnectTasks.get(guest.getUniqueId()));
    assertEquals(1, runs[0]);
  }

  @Test
  void cancellingAPlayersFailsafeTaskStopsItAndForgetsIt() {
    int[] runs = {0};
    BukkitTask task = scheduledTask(runs);
    module.scheduleDisconnectTask(guest.getUniqueId(), task);

    module.cancelDisconnectTask(guest.getUniqueId());
    server.getScheduler().performTicks(10);

    assertTrue(task.isCancelled());
    assertEquals(0, runs[0]);
    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void cancellingAPlayersFailsafeTaskLeavesTheOthers() {
    int[] runs = {0};
    BukkitTask guestsTask = scheduledTask(runs);
    BukkitTask hostsTask = scheduledTask(runs);
    module.scheduleDisconnectTask(guest.getUniqueId(), guestsTask);
    module.scheduleDisconnectTask(host.getUniqueId(), hostsTask);

    module.cancelDisconnectTask(guest.getUniqueId());
    server.getScheduler().performTicks(10);

    assertTrue(guestsTask.isCancelled());
    assertFalse(hostsTask.isCancelled());
    assertEquals(1, runs[0]);
  }

  @Test
  void cancellingAFailsafeTaskThatDoesNotExistIsHarmless() {
    module.cancelDisconnectTask(guest.getUniqueId());

    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void cancellingAFailsafeTaskTwiceIsHarmless() {
    int[] runs = {0};
    module.scheduleDisconnectTask(guest.getUniqueId(), scheduledTask(runs));

    module.cancelDisconnectTask(guest.getUniqueId());
    module.cancelDisconnectTask(guest.getUniqueId());

    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void cancellingEveryFailsafeTaskStopsAllOfThem() {
    int[] runs = {0};
    BukkitTask guestsTask = scheduledTask(runs);
    BukkitTask hostsTask = scheduledTask(runs);
    module.scheduleDisconnectTask(guest.getUniqueId(), guestsTask);
    module.scheduleDisconnectTask(host.getUniqueId(), hostsTask);

    module.cancelAllDisconnectTasks();
    server.getScheduler().performTicks(10);

    assertTrue(guestsTask.isCancelled());
    assertTrue(hostsTask.isCancelled());
    assertEquals(0, runs[0]);
    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void cancellingEveryFailsafeTaskSkipsOneThatIsAlreadyCancelled() {
    int[] runs = {0};
    BukkitTask cancelled = scheduledTask(runs);
    cancelled.cancel();
    module.scheduleDisconnectTask(guest.getUniqueId(), cancelled);

    module.cancelAllDisconnectTasks();

    assertTrue(module.disconnectTasks.isEmpty());
  }

  @Test
  void cancellingEveryFailsafeTaskWithNoneScheduledIsHarmless() {
    module.cancelAllDisconnectTasks();

    assertTrue(module.disconnectTasks.isEmpty());
  }
}
