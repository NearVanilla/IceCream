package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.ageCooldown;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeButton;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The part of the module that decides when a player counts as a sleeper and announces them. */
class OnePlayerSleepModuleVoteTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long DAY_TICKS = 24000L;
  private static final long NIGHT = 13000L;
  private static final String ANNOUNCEMENT = "Sleeper has gone to sleep [Wake up]";

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
    // The module's own state is under test here. The listener that would also clear it when a
    // player leaves, dies or changes world is covered by its own tests.
    HandlerList.unregisterAll(IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void tick() {
    server.getScheduler().performOneTick();
  }

  private void bed(SleepyPlayer player) {
    player.goToBed();
    module.queueSleepCheck(player);
  }

  // Announces the sleeper and returns the token of their announcement.
  private String announced(SleepyPlayer player) {
    bed(player);
    tick();
    return wakeToken(watcher.nextComponentMessage());
  }

  @Test
  void announcesASleeperToEveryone() {
    bed(sleeper);

    tick();

    assertEquals(ANNOUNCEMENT, plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
    assertEquals(ANNOUNCEMENT, plainText(sleeper.nextComponentMessage()));
  }

  @Test
  void announcesNothingBeforeTheHeartbeat() {
    bed(sleeper);

    assertNull(watcher.nextComponentMessage());
    assertNull(sleeper.nextComponentMessage());
  }

  @Test
  void announcementOffersAWakeButtonWithAHint() {
    bed(sleeper);
    tick();

    Component button = wakeButton(watcher.nextComponentMessage());

    assertNotNull(button);
    assertEquals("[Wake up]", plainText(button));
    assertEquals(NamedTextColor.RED, button.color());
    HoverEvent<?> hover = button.hoverEvent();
    assertNotNull(hover);
    assertEquals("Wake all sleepers and keep the night.", plainText((Component) hover.value()));
  }

  @Test
  void announcementTokenIsAUuid() {
    String token = announced(sleeper);

    assertNotNull(token);
    assertEquals(token, UUID.fromString(token).toString());
  }

  @Test
  void announcesEachSleeperWithTheirOwnToken() {
    SleepyPlayer other = addPlayer(server, "Other");
    bed(sleeper);
    bed(other);

    tick();

    Component first = watcher.nextComponentMessage();
    Component second = watcher.nextComponentMessage();
    assertNotNull(wakeToken(first));
    assertNotNull(wakeToken(second));
    assertNotEquals(wakeToken(first), wakeToken(second));
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void announcesASleeperOnlyOnce() {
    bed(sleeper);
    tick();
    watcher.nextComponentMessage();

    module.queueSleepCheck(sleeper);
    tick();
    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void announcesSleepersInDifferentWorlds() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    bed(sleeper);
    bed(traveller);

    tick();

    // The module announces its sleepers in no particular order.
    assertEquals(
        Set.of(ANNOUNCEMENT, "Traveller has gone to sleep [Wake up]"),
        Set.of(
            plainText(watcher.nextComponentMessage()), plainText(watcher.nextComponentMessage())));
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void ignoresAPlayerWhoIsNotInBedWhenTheHeartbeatRuns() {
    module.queueSleepCheck(sleeper);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void ignoresAPlayerWhoLeftBedBeforeTheHeartbeat() {
    bed(sleeper);
    sleeper.setSleeping(false);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void ignoresAPlayerWhoDiedBeforeTheHeartbeat() {
    bed(sleeper);
    sleeper.setHealth(0);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void ignoresAPlayerWhoLeftTheServerBeforeTheHeartbeat() {
    bed(sleeper);
    sleeper.disconnect();

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void ignoresAPlayerWhoChangedWorldBeforeTheHeartbeat() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    bed(sleeper);
    sleeper.teleport(new Location(other, 0, 64, 0));

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void announcesAtTheStartOfTheNight() {
    world.setClock(12000);
    bed(sleeper);

    tick();

    assertNotNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotAnnounceJustBeforeTheNight() {
    world.setClock(11998);
    bed(sleeper);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void announcesOnALaterNight() {
    world.setClock(5 * DAY_TICKS + NIGHT);
    bed(sleeper);

    tick();

    assertNotNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotAnnounceDuringALaterDay() {
    world.setClock(5 * DAY_TICKS + 6000);
    bed(sleeper);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void treatsNegativeTimeAsTheMatchingTimeOfDay() {
    world.setClock(-11000);
    bed(sleeper);

    tick();

    assertNotNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotAnnounceInTheNether() {
    world.setEnvironment(World.Environment.NETHER);
    bed(sleeper);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void doesNotAnnounceWhenTimeDoesNotAdvance() {
    world.setGameRule(GameRules.ADVANCE_TIME, false);
    bed(sleeper);

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void queuedEntryIsDroppedWhenThePlayerIsForgotten() {
    bed(sleeper);

    module.forgetPlayer(sleeper.getUniqueId());
    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void queuedEntryOnlyForgetsThatPlayer() {
    SleepyPlayer other = addPlayer(server, "Other");
    bed(sleeper);
    bed(other);

    module.forgetPlayer(sleeper.getUniqueId());
    tick();

    assertEquals("Other has gone to sleep [Wake up]", plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void queuedEntryIsDroppedWhenItsWorldIsForgotten() {
    bed(sleeper);

    module.forgetWorld(world.getUID());
    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void forgettingAWorldKeepsEntriesInOtherWorlds() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    bed(sleeper);
    bed(traveller);

    module.forgetWorld(world.getUID());
    tick();

    assertEquals(
        "Traveller has gone to sleep [Wake up]", plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void wakesInsteadOfAnnouncingWhileTheNightIsKept() {
    String token = announced(sleeper);
    module.requestWakeUp(watcher, token);
    watcher.nextComponentMessage();
    SleepyPlayer late = addPlayer(server, "Late");
    bed(late);

    tick();

    assertFalse(late.isSleeping());
    assertEquals(1, late.wakeCalls());
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void announcesAgainOnceTheCooldownHasPassed() {
    String token = announced(sleeper);
    module.requestWakeUp(watcher, token);
    watcher.nextComponentMessage();
    ageCooldown(module, Duration.ofSeconds(11));
    SleepyPlayer late = addPlayer(server, "Late");
    bed(late);

    tick();

    assertTrue(late.isSleeping());
    assertEquals("Late has gone to sleep [Wake up]", plainText(watcher.nextComponentMessage()));
  }
}
