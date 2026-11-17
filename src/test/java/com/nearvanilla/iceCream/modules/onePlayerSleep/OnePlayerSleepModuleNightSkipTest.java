package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.ageCooldown;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import java.time.Duration;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * When the module stops the vanilla night skip: while someone is asleep or about to be, and while
 * the night is being kept.
 */
class OnePlayerSleepModuleNightSkipTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long DAY_TICKS = 24000L;
  private static final long NIGHT = 13000L;

  private ServerMock server;
  private OnePlayerSleepModule module;
  private ClockWorld world;
  private SleepyPlayer sleeper;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, ENABLED);
    world = addWorld(server, "world", NIGHT);
    sleeper = addPlayer(server, "Sleeper");
    module = new OnePlayerSleepModule();
    module.register();
    // The module's own state is under test here. The listener that would also clear it when a
    // player leaves, dies or changes world is covered by its own tests.
    HandlerList.unregisterAll(IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // Makes the night be kept: the sleeper is announced and another player clicks their wake button.
  private void keepTheNight() {
    SleepyPlayer other = addPlayer(server, "Other");
    sleeper.goToBed();
    module.queueSleepCheck(sleeper);
    server.getScheduler().performOneTick();
    module.requestWakeUp(other, wakeToken(other.nextComponentMessage()));
    assertTrue(module.isCoolingDown());
  }

  @Test
  void doesNotBlockWhenNobodyIsAsleep() {
    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksWhenAPlayerIsAsleep() {
    sleeper.goToBed();

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksWhenAPlayerHasJustEnteredABed() {
    module.queueSleepCheck(sleeper);

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForAQueuedPlayerOfAnotherWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    module.queueSleepCheck(traveller);

    assertFalse(module.shouldBlockNightSkip(world));
    assertTrue(module.shouldBlockNightSkip(other));
  }

  @Test
  void doesNotBlockForAPlayerQueuedInAnotherWorldWhoMovedHere() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    module.queueSleepCheck(sleeper);
    sleeper.teleport(new Location(other, 0, 64, 0));

    assertFalse(module.shouldBlockNightSkip(other));
  }

  @Test
  void doesNotBlockForAQueuedPlayerWhoDied() {
    module.queueSleepCheck(sleeper);
    sleeper.setHealth(0);

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForAQueuedPlayerWhoLeft() {
    module.queueSleepCheck(sleeper);
    sleeper.disconnect();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForAQueuedPlayerWhoChangedWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    module.queueSleepCheck(sleeper);
    sleeper.teleport(new Location(other, 0, 64, 0));

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForAForgottenPlayer() {
    module.queueSleepCheck(sleeper);

    module.forgetPlayer(sleeper.getUniqueId());

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForAForgottenWorld() {
    module.queueSleepCheck(sleeper);

    module.forgetWorld(world.getUID());

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForASleeperOfAnotherWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    sleeper.teleport(new Location(other, 0, 64, 0));
    sleeper.goToBed();

    assertFalse(module.shouldBlockNightSkip(world));
    assertTrue(module.shouldBlockNightSkip(other));
  }

  @Test
  void doesNotBlockForADeadSleeper() {
    sleeper.goToBed();
    sleeper.setHealth(0);

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockForASleeperWhoLeft() {
    sleeper.goToBed();
    sleeper.disconnect();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksAtTheStartOfTheNight() {
    world.setClock(12000);
    sleeper.goToBed();

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockBeforeTheNight() {
    world.setClock(11999);
    sleeper.goToBed();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksOnALaterNight() {
    world.setClock(7 * DAY_TICKS + NIGHT);
    sleeper.goToBed();

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockDuringALaterDay() {
    world.setClock(7 * DAY_TICKS + 1000);
    sleeper.goToBed();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockInTheNether() {
    world.setEnvironment(World.Environment.NETHER);
    sleeper.goToBed();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockWhenTimeDoesNotAdvance() {
    world.setGameRule(GameRules.ADVANCE_TIME, false);
    sleeper.goToBed();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksWhileTheNightIsKept() {
    keepTheNight();

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksWhileTheNightIsKeptEvenInTheDaytime() {
    keepTheNight();
    world.setClock(1000);

    assertTrue(module.shouldBlockNightSkip(world));
  }

  @Test
  void blocksWhileTheNightIsKeptInAnotherWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    keepTheNight();

    assertTrue(module.shouldBlockNightSkip(other));
  }

  @Test
  void stopsBlockingOnceTheCooldownHasPassed() {
    keepTheNight();
    ageCooldown(module, Duration.ofSeconds(11));

    assertFalse(module.shouldBlockNightSkip(world));
  }

  @Test
  void doesNotBlockWhileDisabled() {
    sleeper.goToBed();
    module.unregister();

    assertFalse(module.shouldBlockNightSkip(world));
  }
}
