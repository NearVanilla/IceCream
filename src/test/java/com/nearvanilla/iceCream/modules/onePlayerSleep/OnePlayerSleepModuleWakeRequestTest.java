package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.ageCooldown;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.ClockWorld;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** What happens when a player clicks the wake button of an announced sleeper. */
class OnePlayerSleepModuleWakeRequestTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final long NIGHT = 13000L;
  private static final String INACTIVE = "That sleep request is no longer active.";
  private static final String KEEP_THE_NIGHT = "Watcher wants to keep the night";

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

  // Puts the player to bed, runs the heartbeat that announces them and returns the token that the
  // watcher read from the announcement.
  private String announced(SleepyPlayer player) {
    player.goToBed();
    module.queueSleepCheck(player);
    tick();
    return wakeToken(watcher.nextComponentMessage());
  }

  private void assertRejected() {
    assertEquals(INACTIVE, plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
    assertFalse(module.isCoolingDown());
  }

  @Test
  void wakesTheSleeper() {
    String token = announced(sleeper);

    module.requestWakeUp(watcher, token);

    assertFalse(sleeper.isSleeping());
    assertEquals(1, sleeper.wakeCalls());
  }

  @Test
  void tellsEveryoneWhoWantsToKeepTheNight() {
    String token = announced(sleeper);
    sleeper.nextComponentMessage();

    module.requestWakeUp(watcher, token);

    assertEquals(KEEP_THE_NIGHT, plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
    assertEquals(KEEP_THE_NIGHT, plainText(sleeper.nextComponentMessage()));
  }

  @Test
  void wakesEveryPlayerInBedWhetherOrNotTheyWereAnnounced() {
    SleepyPlayer late = addPlayer(server, "Late");
    String token = announced(sleeper);
    late.goToBed();
    module.queueSleepCheck(late);

    module.requestWakeUp(watcher, token);

    assertFalse(late.isSleeping());
    assertEquals(1, late.wakeCalls());
  }

  @Test
  void wakesSleepersInEveryWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    String token = announced(sleeper);
    announced(traveller);

    module.requestWakeUp(watcher, token);

    assertFalse(sleeper.isSleeping());
    assertFalse(traveller.isSleeping());
  }

  @Test
  void anySleepersTokenWorks() {
    SleepyPlayer other = addPlayer(server, "Other");
    announced(sleeper);
    String token = announced(other);

    module.requestWakeUp(watcher, token);

    assertFalse(sleeper.isSleeping());
    assertFalse(other.isSleeping());
  }

  @Test
  void startsTheCooldown() {
    String token = announced(sleeper);
    assertFalse(module.isCoolingDown());

    module.requestWakeUp(watcher, token);

    assertTrue(module.isCoolingDown());
  }

  @Test
  void cooldownLastsMoreThanNineSeconds() {
    module.requestWakeUp(watcher, announced(sleeper));

    ageCooldown(module, Duration.ofSeconds(9));

    assertTrue(module.isCoolingDown());
  }

  @Test
  void cooldownEndsAfterTenSeconds() {
    module.requestWakeUp(watcher, announced(sleeper));

    ageCooldown(module, Duration.ofSeconds(11));

    assertFalse(module.isCoolingDown());
  }

  @Test
  void rejectsATokenNobodyWasGiven() {
    announced(sleeper);

    module.requestWakeUp(watcher, "not-a-token");

    assertRejected();
    assertTrue(sleeper.isSleeping());
    assertEquals(0, sleeper.wakeCalls());
  }

  @Test
  void rejectsATokenWhenNobodyIsAsleep() {
    module.requestWakeUp(watcher, "not-a-token");

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAPlayerWhoLeftBed() {
    String token = announced(sleeper);
    sleeper.setSleeping(false);

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAPlayerWhoDied() {
    String token = announced(sleeper);
    sleeper.setHealth(0);

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAPlayerWhoLeftTheServer() {
    String token = announced(sleeper);
    sleeper.disconnect();

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAPlayerWhoChangedWorld() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    String token = announced(sleeper);
    sleeper.teleport(new Location(other, 0, 64, 0));

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAForgottenPlayer() {
    String token = announced(sleeper);
    module.forgetPlayer(sleeper.getUniqueId());

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAForgottenWorld() {
    String token = announced(sleeper);
    module.forgetWorld(world.getUID());

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void rejectsTheTokenOfAWorldThatWasUnloaded() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    String token = announced(traveller);
    server.removeWorld(other);

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void aWorldThatWasUnloadedDoesNotStopAnotherSessionsToken() {
    ClockWorld other = addWorld(server, "other", NIGHT);
    SleepyPlayer traveller = addPlayer(server, "Traveller");
    traveller.teleport(new Location(other, 0, 64, 0));
    String token = announced(sleeper);
    announced(traveller);
    server.removeWorld(other);

    module.requestWakeUp(watcher, token);

    assertFalse(sleeper.isSleeping());
    assertTrue(module.isCoolingDown());
  }

  @Test
  void rejectsRequestsWhileTheModuleIsDisabled() {
    String token = announced(sleeper);
    module.unregister();
    sleeper.goToBed();

    module.requestWakeUp(watcher, token);

    assertRejected();
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void rejectsRequestsFromAPlayerWhoLeft() {
    String token = announced(sleeper);
    watcher.disconnect();

    module.requestWakeUp(watcher, token);

    assertEquals(INACTIVE, plainText(watcher.nextComponentMessage()));
    assertTrue(sleeper.isSleeping());
    assertFalse(module.isCoolingDown());
  }

  @Test
  void rejectsRequestsWhileTheNightIsKept() {
    SleepyPlayer other = addPlayer(server, "Other");
    String first = announced(sleeper);
    String second = announced(other);
    module.requestWakeUp(watcher, first);
    watcher.nextComponentMessage();

    module.requestWakeUp(watcher, second);

    assertEquals(INACTIVE, plainText(watcher.nextComponentMessage()));
    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void acceptsRequestsAgainOnceTheCooldownEnded() {
    module.requestWakeUp(watcher, announced(sleeper));
    ageCooldown(module, Duration.ofSeconds(11));
    SleepyPlayer other = addPlayer(server, "Other");
    watcher.nextComponentMessage();
    String token = announced(other);

    module.requestWakeUp(watcher, token);

    assertFalse(other.isSleeping());
    assertTrue(module.isCoolingDown());
  }

  @Test
  void aTokenCannotBeUsedAgainAfterTheCooldown() {
    String token = announced(sleeper);
    module.requestWakeUp(watcher, token);
    ageCooldown(module, Duration.ofSeconds(11));
    sleeper.goToBed();
    watcher.nextComponentMessage();

    module.requestWakeUp(watcher, token);

    assertRejected();
  }

  @Test
  void forgetsEveryAnnouncedSleeper() {
    SleepyPlayer stubborn = addPlayer(server, "Stubborn");
    stubborn.refuseToWake(true);
    String token = announced(sleeper);
    String stubbornToken = announced(stubborn);
    // The stubborn sleeper stays in bed, which logs a warning that is not what this test is about.
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.requestWakeUp(watcher, token);
    }
    ageCooldown(module, Duration.ofSeconds(11));
    watcher.nextComponentMessage();

    module.requestWakeUp(watcher, stubbornToken);

    assertRejected();
  }

  @Test
  void forgetsPlayersWhoHaveNotBeenAnnouncedYet() {
    SleepyPlayer stubborn = addPlayer(server, "Stubborn");
    stubborn.refuseToWake(true);
    String token = announced(sleeper);
    stubborn.goToBed();
    module.queueSleepCheck(stubborn);
    // The stubborn sleeper stays in bed, which logs a warning that is not what this test is about.
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.requestWakeUp(watcher, token);
    }
    ageCooldown(module, Duration.ofSeconds(11));
    watcher.nextComponentMessage();

    tick();

    assertNull(watcher.nextComponentMessage());
  }

  @Test
  void stopsAcceleratingTheNightForPlayersWhoStayInBed() {
    sleeper.refuseToWake(true);
    String token = announced(sleeper);
    tick();
    // The sleeper stays in bed, which logs a warning that is not what this test is about.
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.requestWakeUp(watcher, token);
    }
    ageCooldown(module, Duration.ofSeconds(11));

    long before = world.getFullTime();
    tick();

    assertTrue(sleeper.isSleeping());
    assertEquals(before + 1, world.getFullTime());
  }

  @Test
  void reportsAPlayerWhoStaysInBed() {
    SleepyPlayer stubborn = addPlayer(server, "Stubborn");
    stubborn.refuseToWake(true);
    String token = announced(sleeper);
    stubborn.goToBed();

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.requestWakeUp(watcher, token);

      assertEquals(
          List.of("Could not wake Stubborn: another plugin prevented bed leaving."),
          log.messages(Level.WARNING));
    }
    assertTrue(stubborn.isSleeping());
    assertFalse(sleeper.isSleeping());
  }

  @Test
  void aPlayerNotInBedIsNotToldToWake() {
    String token = announced(sleeper);

    module.requestWakeUp(watcher, token);

    assertEquals(0, watcher.wakeCalls());
  }

  @Test
  void rejectsTheTokenOnceTheNightEnded() {
    String token = announced(sleeper);
    world.setClock(1000);
    module.requestWakeUp(watcher, token);
    assertRejected();
    world.setClock(NIGHT);

    module.requestWakeUp(watcher, token);

    assertRejected();
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void wakesTheSleeperOfASessionThatEndedWithTheNight() {
    String token = announced(sleeper);
    world.setClock(1000);
    module.requestWakeUp(watcher, token);
    watcher.nextComponentMessage();
    world.setClock(NIGHT);

    tick();

    assertFalse(sleeper.isSleeping());
    assertEquals(1, sleeper.wakeCalls());
  }

  @Test
  void wakesANewSleeperInsteadOfAnnouncingThemToASessionThatEnded() {
    SleepyPlayer late = addPlayer(server, "Late");
    String token = announced(sleeper);
    world.setClock(1000);
    module.requestWakeUp(watcher, token);
    watcher.nextComponentMessage();
    world.setClock(NIGHT);
    late.goToBed();
    module.queueSleepCheck(late);

    tick();

    assertFalse(late.isSleeping());
    assertEquals(1, late.wakeCalls());
    assertNull(watcher.nextComponentMessage());
  }
}
