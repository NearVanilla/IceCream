package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.onePlayerSleep.events.OnePlayerSleepListener;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld.WriteMode;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.ClockTimeSkipEvent.SkipReason;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * How the module speeds up the night. MockBukkit advances every world by one tick for each
 * scheduler tick before the module's heartbeat runs, which stands in for the natural passing of
 * time, so with a multiplier of ten a settled night gains ten ticks per tick.
 */
class OnePlayerSleepModuleAccelerationTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long NIGHT = 13000L;

  private ServerMock server;
  private OnePlayerSleepModule module;
  private ClockWorld world;
  private SleepyPlayer sleeper;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = addWorld(server, "world", NIGHT);
    sleeper = addPlayer(server, "Sleeper");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // Starts the module with its default multiplier of ten.
  private void start() {
    start(ENABLED);
  }

  private void start(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    module = new OnePlayerSleepModule();
    module.register();
    // The module's own state is under test here. The listener that would also clear it when a
    // player leaves, dies or changes world is covered by its own tests.
    HandlerList.unregisterAll(IceCream.instance);
  }

  private void startWithMultiplier(String multiplier) {
    start(ENABLED + "    multiplier: " + multiplier + "\n");
  }

  private void bed(SleepyPlayer player) {
    player.goToBed();
    module.queueSleepCheck(player);
  }

  private void tick() {
    server.getScheduler().performOneTick();
  }

  // Runs one tick and returns how far the world's clock moved during it.
  private long gain(ClockWorld target) {
    long before = target.getFullTime();
    tick();
    return target.getFullTime() - before;
  }

  // Puts the sleeper to bed and runs the tick on which the module notices them and starts timing.
  private void settle() {
    bed(sleeper);
    tick();
  }

  private void callSkip(SkipReason reason, long amount) {
    module.observeTimeChange(new TimeSkipEvent(world, reason, amount));
  }

  @Test
  void doesNotAccelerateOnTheTickItStartsTiming() {
    start();

    settle();

    assertEquals(NIGHT + 1, world.getFullTime());
  }

  @Test
  void acceleratesByTheMultiplierOnceTiming() {
    start();
    settle();

    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void acceleratesByTheConfiguredMultiplier() {
    startWithMultiplier("3");
    settle();

    assertEquals(3, gain(world));
    assertEquals(3, gain(world));
  }

  @Test
  void multiplierOfOneAddsNoTime() {
    startWithMultiplier("1.0");
    settle();

    assertEquals(1, gain(world));
    assertEquals(1, gain(world));
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void carriesTheFractionOfAFractionalMultiplier() {
    startWithMultiplier("2.5");
    settle();

    List<Long> gains = List.of(gain(world), gain(world), gain(world), gain(world));

    assertEquals(List.of(2L, 3L, 2L, 3L), gains);
  }

  @Test
  void scalesTheExtraTimeWithTheTimeThatPassed() {
    start();
    settle();
    long timed = world.getFullTime();

    // Five ticks pass between two heartbeats: the four set here and the one the world ticks.
    world.setClock(timed + 4);
    tick();

    assertEquals(timed + 4 + 1 + 9 * 5, world.getFullTime());
  }

  @Test
  void twoSleepersInOneWorldDoNotStackTheAcceleration() {
    SleepyPlayer other = addPlayer(server, "Other");
    start();
    bed(sleeper);
    bed(other);
    tick();

    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void acceleratesEachWorldWithASleeperByItsOwnClock() {
    ClockWorld other = addWorld(server, "other", 15000);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    start();
    bed(sleeper);
    bed(traveller);
    tick();
    long worldBefore = world.getFullTime();
    long otherBefore = other.getFullTime();

    tick();

    assertEquals(worldBefore + 10, world.getFullTime());
    assertEquals(otherBefore + 10, other.getFullTime());
  }

  @Test
  void doesNotAccelerateAWorldWithoutASleeper() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    start();
    settle();

    assertEquals(10, gain(world));
    assertEquals(NIGHT + 2, other.getFullTime());
  }

  @Test
  void worldsSharingAClockAreAcceleratedOnce() {
    ClockWorld shared = addWorld(server, "shared", 0);
    shared.shareClockWith(world);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(shared, 0, 64, 0));
    start();
    bed(sleeper);
    bed(traveller);
    tick();

    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
    assertEquals(world.getFullTime(), shared.getFullTime());
    assertTrue(sleeper.isSleeping());
    assertTrue(traveller.isSleeping());
  }

  @Test
  void aNewSessionStartsWithTheUnspentFractionOfASessionAtTheSameTime() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    startWithMultiplier("2.5");
    settle();
    assertEquals(2, gain(world));
    assertEquals(3, gain(world));
    // The second world starts timing on the heartbeat that leaves the first with half a tick
    // unspent, and at the same time of day, as if both read one clock.
    other.setClock(world.getFullTime());
    bed(traveller);
    tick();
    long worldBefore = world.getFullTime();
    long otherBefore = other.getFullTime();

    tick();

    assertEquals(3, world.getFullTime() - worldBefore);
    assertEquals(3, other.getFullTime() - otherBefore);
  }

  @Test
  void aNewSessionAtAnotherTimeStartsWithoutTheFractionOfAnotherSession() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    startWithMultiplier("2.5");
    settle();
    assertEquals(2, gain(world));
    assertEquals(3, gain(world));
    other.setClock(world.getFullTime() + 1000);
    bed(traveller);
    tick();
    long otherBefore = other.getFullTime();

    tick();

    assertEquals(2, other.getFullTime() - otherBefore);
  }

  @Test
  void stopsAtDaybreakAndWakesTheSleeper() {
    world.setClock(23990);
    start();
    settle();

    tick();

    assertEquals(24000, world.getFullTime());
    assertFalse(sleeper.isSleeping());
    assertEquals(1, sleeper.wakeCalls());
  }

  @Test
  void wakesTheSleeperWhenTheDayArrivesOnItsOwn() {
    world.setClock(23998);
    startWithMultiplier("1.0");
    settle();
    assertTrue(sleeper.isSleeping());

    tick();

    assertEquals(24000, world.getFullTime());
    assertFalse(sleeper.isSleeping());
  }

  @Test
  void noLongerAcceleratesAfterDaybreak() {
    world.setClock(23990);
    start();
    settle();
    tick();

    assertEquals(1, gain(world));
  }

  @Test
  void wakesTheSleeperWhenAnotherSourceMakesItDay() {
    start();
    settle();
    world.setClock(1000);

    tick();

    assertFalse(sleeper.isSleeping());
    assertEquals(1001, world.getFullTime());
  }

  @Test
  void doesNotAccelerateTheDay() {
    start();
    settle();
    world.setClock(24000 + 1000);

    assertEquals(1, gain(world));
    assertFalse(sleeper.isSleeping());
  }

  @Test
  void stopsWhenTheSleeperLeavesBed() {
    start();
    settle();
    sleeper.setSleeping(false);

    assertEquals(1, gain(world));
    assertEquals(1, gain(world));
  }

  @Test
  void stopsWhenTheSleeperDies() {
    start();
    settle();
    sleeper.setHealth(0);

    assertEquals(1, gain(world));
  }

  @Test
  void stopsWhenTheSleeperLeavesTheServer() {
    start();
    settle();
    sleeper.disconnect();

    assertEquals(1, gain(world));
  }

  @Test
  void stopsWhenTheSleeperChangesWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    start();
    settle();
    sleeper.teleport(new Location(other, 0, 64, 0));

    assertEquals(1, gain(world));
  }

  @Test
  void keepsAcceleratingWhileAnotherPlayerStillSleeps() {
    SleepyPlayer other = addPlayer(server, "Other");
    start();
    bed(sleeper);
    bed(other);
    tick();
    sleeper.setSleeping(false);

    assertEquals(10, gain(world));
    assertTrue(other.isSleeping());
  }

  @Test
  void startsTimingAgainForAPlayerWhoSleepsLater() {
    start();
    settle();
    sleeper.setSleeping(false);
    tick();

    bed(sleeper);
    tick();

    assertEquals(10, gain(world));
  }

  @Test
  void doesNotAccelerateForAPlayerWhoIsAsleepButWasNeverQueued() {
    start();
    sleeper.goToBed();
    tick();

    assertEquals(1, gain(world));
  }

  @Test
  void startsTimingAgainAfterATimeCommand() {
    start();
    settle();
    tick();
    long before = world.getFullTime();
    world.setClock(before + 5000);

    callSkip(SkipReason.COMMAND, 5000);

    assertEquals(1, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void startsTimingAgainAfterACustomTimeChange() {
    start();
    settle();
    tick();
    world.setClock(world.getFullTime() + 2000);

    callSkip(SkipReason.CUSTOM, 2000);

    assertEquals(1, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void startsTimingAgainAfterAWorldIndependentTimeChange() {
    start();
    settle();
    tick();
    world.setClock(world.getFullTime() + 3000);

    module.observeTimeChange(new ClockTimeSkipEvent(SkipReason.COMMAND, 3000));

    assertEquals(1, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void aTimeChangeDropsTheUnspentFraction() {
    startWithMultiplier("2.5");
    settle();
    assertEquals(2, gain(world));
    // The half tick carried from that heartbeat is dropped by the time change.
    callSkip(SkipReason.COMMAND, 0);
    assertEquals(1, gain(world));

    assertEquals(2, gain(world));
    assertEquals(3, gain(world));
  }

  @Test
  void aNightSkipDoesNotRestartTheTiming() {
    start();
    settle();
    tick();
    long before = world.getFullTime();
    world.setClock(before + 100);

    module.observeTimeChange(new ClockTimeSkipEvent(SkipReason.NIGHT_SKIP, 100));

    assertEquals(1 + 9 * 101, gain(world));
  }

  @Test
  void startsTimingAgainWhenTheClockGoesBackwards() {
    start();
    settle();
    tick();
    world.setClock(world.getFullTime() - 500);

    assertEquals(1, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void aClockThatGoesBackwardsDropsTheUnspentFraction() {
    startWithMultiplier("2.5");
    settle();
    assertEquals(2, gain(world));
    world.setClock(world.getFullTime() - 501);

    assertEquals(1, gain(world));

    assertEquals(2, gain(world));
    assertEquals(3, gain(world));
  }

  @Test
  void ignoresTheTimeChangesItMakesItself() {
    start();
    // The world announces every write the way Paper does, so the module's listener must be in place
    // to hear its own.
    server.getPluginManager().registerEvents(new OnePlayerSleepListener(module), IceCream.instance);
    settle();

    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
    assertEquals(10, gain(world));
  }

  @Test
  void stopsWhenSomethingPreventsTheTimeFromAdvancing() {
    start();
    settle();
    world.writeMode(WriteMode.IGNORE);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      assertEquals(1, gain(world));

      assertEquals(
          List.of("Sleep acceleration stopped in world world: time advancement was prevented."),
          log.messages(Level.WARNING));
    }
    assertFalse(sleeper.isSleeping());
  }

  @Test
  void stopsWhenTheServerRefusesTheTime() {
    start();
    settle();
    world.writeMode(WriteMode.REJECT);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      assertEquals(1, gain(world));

      assertEquals(
          List.of("Sleep acceleration failed in world world."), log.messages(Level.WARNING));
      assertInstanceOf(IllegalArgumentException.class, log.records().getFirst().getThrown());
    }
    assertFalse(sleeper.isSleeping());
  }

  @Test
  void keepsObservingTimeChangesAfterAFailedWrite() {
    start();
    settle();
    world.writeMode(WriteMode.REJECT);
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      tick();
    }
    world.writeMode(WriteMode.ANNOUNCE);
    settle();
    tick();
    world.setClock(world.getFullTime() + 4000);

    callSkip(SkipReason.COMMAND, 4000);

    assertEquals(1, gain(world));
  }

  @Test
  void aSleeperWhoRefusesToWakeIsReported() {
    world.setClock(23990);
    start();
    sleeper.refuseToWake(true);
    settle();

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      tick();

      assertEquals(
          List.of("Could not wake Sleeper: another plugin prevented bed leaving."),
          log.messages(Level.WARNING));
    }
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void aWokenSleeperIsNotReported() {
    world.setClock(23990);
    start();
    settle();

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      tick();

      assertTrue(log.records().isEmpty());
    }
  }

  @Test
  void dropsTheSessionOfAWorldThatWasUnloaded() {
    start();
    settle();
    server.removeWorld(world);
    tick();
    server.addWorld(world);

    assertEquals(1, gain(world));
    assertTrue(sleeper.isSleeping());
  }
}
