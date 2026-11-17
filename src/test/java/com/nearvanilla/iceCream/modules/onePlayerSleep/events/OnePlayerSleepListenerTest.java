package com.nearvanilla.iceCream.modules.onePlayerSleep.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.ageCooldown;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.onePlayerSleep.OnePlayerSleepModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import io.papermc.paper.block.bed.BedEnterAction;
import io.papermc.paper.block.bed.BedEnterProblem;
import io.papermc.paper.block.bed.BedRuleResult;
import java.time.Duration;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.ClockTimeSkipEvent.SkipReason;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Every handler of the listener, fired through the plugin manager at a module that registered it
 * itself. The module's state is read back through the wake buttons: a forgotten sleeper's button no
 * longer works, even though the mock player is still in bed.
 */
class OnePlayerSleepListenerTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long NIGHT = 13000L;
  private static final String PAUSED =
      "Sleeping is temporarily paused because someone wants to keep the night.";
  private static final String INACTIVE = "That sleep request is no longer active.";

  private ServerMock server;
  private OnePlayerSleepModule module;
  private ClockWorld world;
  private SleepyPlayer sleeper;
  private SleepyPlayer watcher;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, ENABLED);
    world = addWorld(server, "world", NIGHT);
    sleeper = addPlayer(server, "Sleeper");
    watcher = addPlayer(server, "Watcher");
    module = new OnePlayerSleepModule();
    module.register();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void tick() {
    server.getScheduler().performOneTick();
  }

  private <E extends Event> E call(E event) {
    server.getPluginManager().callEvent(event);
    return event;
  }

  private Block bed() {
    return world.getBlockAt(0, 64, 0);
  }

  private PlayerBedEnterEvent bedEnter(
      SleepyPlayer player, Event.Result useBed, BedRuleResult canSleep, BedEnterProblem problem) {
    BedEnterAction action = mock(BedEnterAction.class);
    when(action.canSleep()).thenReturn(canSleep);
    when(action.problem()).thenReturn(problem);
    PlayerBedEnterEvent event = new PlayerBedEnterEvent(player, bed(), BedEnterResult.OK, action);
    event.setUseBed(useBed);
    return event;
  }

  // BedEnterProblem's constants need a server registry that MockBukkit lacks, and the listener only
  // asks whether there is a problem.
  private static BedEnterProblem problem() {
    return () -> Component.text("problem");
  }

  private PlayerBedEnterEvent bedEnter(SleepyPlayer player) {
    return bedEnter(player, Event.Result.DEFAULT, BedRuleResult.ALLOWED, null);
  }

  // Announces the player as a sleeper and returns the token the watcher read from the message.
  private String announced(SleepyPlayer player) {
    player.goToBed();
    module.queueSleepCheck(player);
    tick();
    return wakeToken(watcher.nextComponentMessage());
  }

  // Whether the button with the token still works. A working button wakes the sleepers and starts
  // the cooldown, so this is only asked once per test.
  private boolean buttonWorks(String token) {
    module.requestWakeUp(watcher, token);
    Component message = watcher.nextComponentMessage();
    assertNotNull(message);
    return !INACTIVE.equals(plainText(message));
  }

  // Keeps the night, which leaves the module cooling down.
  private void keepTheNight() {
    SleepyPlayer other = addPlayer(server, "Other");
    module.requestWakeUp(other, announced(sleeper));
    watcher.nextComponentMessage();
    assertTrue(module.isCoolingDown());
  }

  @Test
  void cancelsEnteringABedWhileTheNightIsKept() {
    keepTheNight();
    sleeper.nextComponentMessage();
    sleeper.nextComponentMessage();

    PlayerBedEnterEvent event = call(bedEnter(sleeper));

    assertTrue(event.isCancelled());
    assertEquals(PAUSED, plainText(sleeper.nextComponentMessage()));
    assertNull(sleeper.nextComponentMessage());
  }

  @Test
  void allowsEnteringABedWhenTheNightIsNotKept() {
    PlayerBedEnterEvent event = call(bedEnter(sleeper));

    assertFalse(event.isCancelled());
    assertNull(sleeper.nextComponentMessage());
  }

  @Test
  void allowsEnteringABedOnceTheCooldownEnded() {
    keepTheNight();
    ageCooldown(module, Duration.ofSeconds(11));
    sleeper.nextComponentMessage();
    sleeper.nextComponentMessage();

    PlayerBedEnterEvent event = call(bedEnter(sleeper));

    assertFalse(event.isCancelled());
    assertNull(sleeper.nextComponentMessage());
  }

  @Test
  void doesNotQueueABedEnteredWhileTheNightIsKept() {
    keepTheNight();
    SleepyPlayer late = addPlayer(server, "Late");
    late.goToBed();

    call(bedEnter(late));
    tick();

    assertEquals(0, late.wakeCalls());
  }

  @Test
  void queuesAnAllowedBedEntry() {
    call(bedEnter(sleeper));
    sleeper.goToBed();

    tick();

    assertEquals("Sleeper has gone to sleep [Wake up]", plainText(watcher.nextComponentMessage()));
  }

  @Test
  void queuesABedEntryTheEventExplicitlyAllows() {
    call(bedEnter(sleeper, Event.Result.ALLOW, BedRuleResult.NEVER, problem()));
    sleeper.goToBed();

    tick();

    assertEquals("Sleeper has gone to sleep [Wake up]", plainText(watcher.nextComponentMessage()));
  }

  @Test
  void doesNotQueueABedEntryTheEventDenies() {
    call(bedEnter(sleeper, Event.Result.DENY, BedRuleResult.ALLOWED, null));
    sleeper.goToBed();

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotQueueABedEntryWithAProblem() {
    call(bedEnter(sleeper, Event.Result.DEFAULT, BedRuleResult.ALLOWED, problem()));
    sleeper.goToBed();

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotQueueABedEntryTheRulesDoNotAllowToSleep() {
    call(bedEnter(sleeper, Event.Result.DEFAULT, BedRuleResult.TOO_MUCH_LIGHT, null));
    sleeper.goToBed();

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotQueueACancelledBedEntry() {
    PlayerBedEnterEvent event = bedEnter(sleeper);
    event.setCancelled(true);

    call(event);
    sleeper.goToBed();
    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void forgetsAPlayerWhoLeavesBed() {
    String token = announced(sleeper);

    call(new PlayerBedLeaveEvent(sleeper, bed(), true));

    assertFalse(buttonWorks(token));
  }

  @Test
  void leavingBedOnlyForgetsThatPlayer() {
    SleepyPlayer other = addPlayer(server, "Other");
    announced(sleeper);
    String token = announced(other);

    call(new PlayerBedLeaveEvent(sleeper, bed(), true));

    assertTrue(buttonWorks(token));
  }

  @Test
  void doesNotForgetAPlayerWhoseBedLeaveWasCancelled() {
    String token = announced(sleeper);
    PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(sleeper, bed(), true);
    event.setCancelled(true);

    call(event);

    assertTrue(buttonWorks(token));
  }

  @Test
  void forgetsAPlayerWhoQuits() {
    String token = announced(sleeper);

    call(
        new PlayerQuitEvent(
            sleeper, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertFalse(buttonWorks(token));
  }

  @Test
  void forgetsAPlayerWhoDies() {
    String token = announced(sleeper);

    call(
        new PlayerDeathEvent(
            sleeper,
            DamageSource.builder(DamageType.GENERIC).build(),
            new ArrayList<ItemStack>(),
            0,
            Component.text("died"),
            false));

    assertFalse(buttonWorks(token));
  }

  @Test
  void forgetsAPlayerWhoChangesWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    String token = announced(sleeper);

    call(new PlayerChangedWorldEvent(sleeper, other));

    assertFalse(buttonWorks(token));
  }

  @Test
  void forgetsTheSessionOfAWorldThatUnloads() {
    String token = announced(sleeper);

    call(new WorldUnloadEvent(world));

    assertFalse(buttonWorks(token));
  }

  @Test
  void unloadingAWorldKeepsTheSessionsOfOtherWorlds() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    announced(sleeper);
    String token = announced(traveller);

    call(new WorldUnloadEvent(world));

    assertTrue(buttonWorks(token));
  }

  @Test
  void doesNotForgetAWorldWhoseUnloadWasCancelled() {
    String token = announced(sleeper);
    WorldUnloadEvent event = new WorldUnloadEvent(world);
    event.setCancelled(true);

    call(event);

    assertTrue(buttonWorks(token));
  }

  @Test
  void cancelsTheNightSkipWhileSomeoneSleeps() {
    sleeper.goToBed();
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.NIGHT_SKIP, 11000);

    call(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void letsTheNightSkipHappenWhenNobodySleeps() {
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.NIGHT_SKIP, 11000);

    call(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void letsTheNightSkipHappenInAWorldWhereNobodySleeps() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    sleeper.goToBed();
    TimeSkipEvent event = new TimeSkipEvent(other, SkipReason.NIGHT_SKIP, 11000);

    call(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void cancelsTheNightSkipWhileTheNightIsKept() {
    keepTheNight();
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.NIGHT_SKIP, 11000);

    call(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void letsATimeCommandHappenWhileSomeoneSleeps() {
    sleeper.goToBed();
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.COMMAND, 5000);

    call(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void letsAPluginChangeTheTimeWhileSomeoneSleeps() {
    sleeper.goToBed();
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.CUSTOM, 5000);

    call(event);

    assertFalse(event.isCancelled());
  }

  // Runs the ticks that bring a sleeper's session to its steady ten ticks of clock per tick and
  // returns how far the world's clock moves in the next tick.
  private long steadyGain() {
    long before = world.getFullTime();
    tick();
    return world.getFullTime() - before;
  }

  private void settle() {
    announced(sleeper);
    tick();
  }

  @Test
  void startsTimingAgainAfterATimeCommand() {
    settle();
    assertEquals(10, steadyGain());
    world.setClock(world.getFullTime() + 5000);

    call(new TimeSkipEvent(world, SkipReason.COMMAND, 5000));

    assertEquals(1, steadyGain());
    assertEquals(10, steadyGain());
  }

  @Test
  void startsTimingAgainAfterAClockTimeChange() {
    settle();
    assertEquals(10, steadyGain());
    world.setClock(world.getFullTime() + 5000);

    call(new ClockTimeSkipEvent(SkipReason.CUSTOM, 5000));

    assertEquals(1, steadyGain());
    assertEquals(10, steadyGain());
  }

  @Test
  void ignoresACancelledClockTimeChange() {
    settle();
    assertEquals(10, steadyGain());
    world.setClock(world.getFullTime() + 100);
    ClockTimeSkipEvent event = new ClockTimeSkipEvent(SkipReason.COMMAND, 100);
    event.setCancelled(true);

    call(event);

    assertEquals(1 + 9 * 101, steadyGain());
  }

  @Test
  void ignoresACancelledTimeChange() {
    settle();
    assertEquals(10, steadyGain());
    world.setClock(world.getFullTime() + 100);
    TimeSkipEvent event = new TimeSkipEvent(world, SkipReason.COMMAND, 100);
    event.setCancelled(true);

    call(event);

    assertEquals(1 + 9 * 101, steadyGain());
  }
}
