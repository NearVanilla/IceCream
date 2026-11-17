package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.onePlayerSleep.commands.WakeUpCommand;
import com.nearvanilla.iceCream.modules.onePlayerSleep.events.OnePlayerSleepListener;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentMatchers;

class OnePlayerSleepModuleTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long NIGHT = 13000L;

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private OnePlayerSleepModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new OnePlayerSleepModule();
  }

  private static String enabledWithMultiplier(String yamlValue) {
    return ENABLED + "    multiplier: " + yamlValue + "\n";
  }

  private long listeners() {
    return HandlerList.getRegisteredListeners(IceCream.instance).stream()
        .map(registered -> registered.getListener())
        .filter(OnePlayerSleepListener.class::isInstance)
        .distinct()
        .count();
  }

  // How far the clock of a night world moves in one tick once a sleeper's session has settled. One
  // tick is the world's own natural advance, so the result is the total speed of the night.
  private long ticksGainedPerTick(OnePlayerSleepModule module) {
    ClockWorld world = addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    player.goToBed();
    module.queueSleepCheck(player);
    server.getScheduler().performOneTick();
    long before = world.getFullTime();
    server.getScheduler().performOneTick();
    return world.getFullTime() - before;
  }

  private void assertNothingRegistered(OnePlayerSleepModule module) {
    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertEquals(0, listeners());
    assertTrue(server.getScheduler().getPendingTasks().isEmpty());
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  oneplayersleep:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    OnePlayerSleepModule module = module("");

    module.register();

    assertNothingRegistered(module);
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    OnePlayerSleepModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(WakeUpCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    OnePlayerSleepModule module = module(ENABLED);
    ClockWorld world = addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();

    TimeSkipEvent skip = new TimeSkipEvent(world, ClockTimeSkipEvent.SkipReason.NIGHT_SKIP, 1000);
    server.getPluginManager().callEvent(skip);

    assertEquals(1, listeners());
    assertTrue(skip.isCancelled());
  }

  @Test
  void registerWhenEnabledStartsTheHeartbeat() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    module.queueSleepCheck(player);
    assertNull(player.nextComponentMessage());

    server.getScheduler().performOneTick();

    assertEquals(1, server.getScheduler().getPendingTasks().size());
    assertEquals("Sleeper has gone to sleep [Wake up]", plainText(player.nextComponentMessage()));
  }

  @Test
  void registerTwiceRegistersOnce() {
    OnePlayerSleepModule module = module(ENABLED);

    module.register();
    module.register();

    verify(IceCream.annotationParser, times(1)).parse(any(WakeUpCommand.class));
    assertEquals(1, listeners());
    assertEquals(1, server.getScheduler().getPendingTasks().size());
  }

  @Test
  void defaultMultiplierIsTen() {
    OnePlayerSleepModule module = module(ENABLED);
    module.register();

    assertEquals(10, ticksGainedPerTick(module));
  }

  @Test
  void registerReadsAWholeNumberMultiplier() {
    OnePlayerSleepModule module = module(enabledWithMultiplier("4"));
    module.register();

    assertTrue(module.isEnabled());
    assertEquals(4, ticksGainedPerTick(module));
  }

  @Test
  void registerReadsADecimalMultiplier() {
    OnePlayerSleepModule module = module(enabledWithMultiplier("2.0"));
    module.register();

    assertEquals(2, ticksGainedPerTick(module));
  }

  @Test
  void registerAcceptsAMultiplierOfExactlyOne() {
    OnePlayerSleepModule module = module(enabledWithMultiplier("1.0"));
    module.register();

    assertTrue(module.isEnabled());
    assertEquals(1, ticksGainedPerTick(module));
  }

  private void assertInvalidMultiplier(String yamlValue) {
    OnePlayerSleepModule module = module(enabledWithMultiplier(yamlValue));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of(
              "Invalid modules.oneplayersleep.multiplier: expected a finite number >= 1.0; One"
                  + " Player Sleep module is disabled."),
          log.messages(Level.SEVERE));
    }
    assertNothingRegistered(module);
  }

  @Test
  void registerRejectsAMultiplierBelowOne() {
    assertInvalidMultiplier("0.99");
  }

  @Test
  void registerRejectsAZeroMultiplier() {
    assertInvalidMultiplier("0");
  }

  @Test
  void registerRejectsANegativeMultiplier() {
    assertInvalidMultiplier("-3");
  }

  @Test
  void registerRejectsAMultiplierThatIsNotANumber() {
    assertInvalidMultiplier("fast");
  }

  @Test
  void registerRejectsABooleanMultiplier() {
    assertInvalidMultiplier("true");
  }

  @Test
  void registerRejectsAMultiplierThatIsNaN() {
    assertInvalidMultiplier(".nan");
  }

  @Test
  void registerRejectsAnInfiniteMultiplier() {
    assertInvalidMultiplier(".inf");
  }

  @Test
  void registerWhenTheCommandFailsStaysDisabled() {
    OnePlayerSleepModule module = module(ENABLED);
    doThrow(new IllegalStateException("no commands"))
        .when(IceCream.annotationParser)
        .parse(ArgumentMatchers.<Object>any());

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register One Player Sleep module."), log.messages(Level.SEVERE));
      assertEquals("no commands", log.records().getFirst().getThrown().getMessage());
    }

    assertFalse(module.isEnabled());
    assertEquals(0, listeners());
    assertTrue(server.getScheduler().getPendingTasks().isEmpty());
  }

  @Test
  void registerCanBeRetriedAfterAFailure() {
    OnePlayerSleepModule module = module(ENABLED);
    doThrow(new IllegalStateException("no commands"))
        .doReturn(List.of())
        .when(IceCream.annotationParser)
        .parse(ArgumentMatchers.<Object>any());

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    module.register();

    assertTrue(module.isEnabled());
    assertEquals(1, listeners());
    assertEquals(1, server.getScheduler().getPendingTasks().size());
  }

  @Test
  void queueSleepCheckIsIgnoredWhileDisabled() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    player.goToBed();

    module.queueSleepCheck(player);
    module.register();
    server.getScheduler().performOneTick();

    assertNull(player.nextComponentMessage());
  }

  @Test
  void unregisterMarksTheModuleDisabled() {
    OnePlayerSleepModule module = module(ENABLED);
    module.register();

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterRemovesTheListenerAndStopsTheHeartbeat() {
    OnePlayerSleepModule module = module(ENABLED);
    module.register();

    module.unregister();

    assertEquals(0, listeners());
    assertTrue(server.getScheduler().getPendingTasks().isEmpty());
  }

  @Test
  void unregisterStopsAcceleratingTheNight() {
    OnePlayerSleepModule module = module(ENABLED);
    ClockWorld world = addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    module.queueSleepCheck(player);
    server.getScheduler().performTicks(3);

    module.unregister();
    long before = world.getFullTime();
    server.getScheduler().performTicks(3);

    assertEquals(before + 3, world.getFullTime());
  }

  @Test
  void unregisterWakesAnnouncedSleepers() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    module.queueSleepCheck(player);
    server.getScheduler().performOneTick();
    assertTrue(player.isSleeping());

    module.unregister();

    assertFalse(player.isSleeping());
    assertEquals(1, player.wakeCalls());
  }

  @Test
  void unregisterLeavesSleepersThatWereNeverAnnounced() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    module.queueSleepCheck(player);

    module.unregister();

    assertTrue(player.isSleeping());
    assertEquals(0, player.wakeCalls());
  }

  @Test
  void unregisterToleratesAnAnnouncedSleeperWhoLeft() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    module.queueSleepCheck(player);
    server.getScheduler().performOneTick();
    // Without the listener's quit handler the module is left holding the player who left.
    HandlerList.unregisterAll(IceCream.instance);
    player.disconnect();

    module.unregister();

    assertFalse(module.isEnabled());
    assertEquals(0, player.wakeCalls());
  }

  @Test
  void unregisterWakesAnnouncedSleepersInEveryWorld() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer first = addPlayer(server, "First");
    SleepyPlayer second = addPlayer(server, "Second");
    second.teleport(new Location(other, 0, 64, 0));
    module.register();
    first.goToBed();
    second.goToBed();
    module.queueSleepCheck(first);
    module.queueSleepCheck(second);
    server.getScheduler().performOneTick();

    module.unregister();

    assertFalse(first.isSleeping());
    assertFalse(second.isSleeping());
  }

  @Test
  void unregisterForgetsAnnouncedSleepers() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer stubborn = addPlayer(server, "Stubborn");
    SleepyPlayer watcher = addPlayer(server, "Watcher");
    stubborn.refuseToWake(true);
    module.register();
    stubborn.goToBed();
    module.queueSleepCheck(stubborn);
    server.getScheduler().performOneTick();
    String token = wakeToken(watcher.nextComponentMessage());
    module.unregister();
    module.register();

    module.requestWakeUp(watcher, token);

    assertEquals(
        "That sleep request is no longer active.", plainText(watcher.nextComponentMessage()));
    assertFalse(module.isCoolingDown());
  }

  @Test
  void unregisterEndsTheCooldown() {
    OnePlayerSleepModule module = module(ENABLED);
    addWorld(server, "world", NIGHT);
    SleepyPlayer sleeper = addPlayer(server, "Sleeper");
    module.register();
    sleeper.goToBed();
    module.queueSleepCheck(sleeper);
    server.getScheduler().performOneTick();
    module.requestWakeUp(sleeper, wakeTokenOf(sleeper));
    assertTrue(module.isCoolingDown());

    module.unregister();

    assertFalse(module.isCoolingDown());
  }

  @Test
  void unregisterWhenNeverRegisteredDoesNothing() {
    OnePlayerSleepModule module = module(ENABLED);

    module.unregister();

    assertFalse(module.isEnabled());
    assertEquals(0, listeners());
  }

  @Test
  void registerAfterUnregisterEnablesTheModuleAgain() {
    OnePlayerSleepModule module = module(ENABLED);
    module.register();
    module.unregister();

    module.register();

    assertTrue(module.isEnabled());
    assertEquals(1, listeners());
    assertEquals(1, server.getScheduler().getPendingTasks().size());
  }

  @Test
  void unregisteredModuleBlocksNothing() {
    OnePlayerSleepModule module = module(ENABLED);
    ClockWorld world = addWorld(server, "world", NIGHT);
    SleepyPlayer player = addPlayer(server, "Sleeper");
    module.register();
    player.goToBed();
    assertTrue(module.shouldBlockNightSkip(world));

    module.unregister();

    assertFalse(module.shouldBlockNightSkip(world));
  }

  private static String wakeTokenOf(SleepyPlayer observer) {
    return wakeToken(observer.nextComponentMessage());
  }
}
