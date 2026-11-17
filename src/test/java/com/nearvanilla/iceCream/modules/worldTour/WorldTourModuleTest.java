package com.nearvanilla.iceCream.modules.worldTour;

import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.commands.WorldTourCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class WorldTourModuleTest {
  private static final String ENABLED = "modules:\n  worldtour:\n    enabled: true\n";
  private static final long ONE_MINUTE = 60L * 20L;

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private WorldTourModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new WorldTourModule();
  }

  private WorldTourModule registered(String extraConfig) {
    WorldTourModule module = module(ENABLED + extraConfig);
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    return module;
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  worldtour:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    WorldTourModule module = module("");

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }

    assertFalse(module.isEnabled());
    assertNull(WorldTourModule.JOINED_KEY);
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenDisabledRegistersNoListener() {
    WorldTourModule module = module("");
    WorldTourModule.JOINED_KEY = new NamespacedKey(IceCream.instance, "worldtour.joined");
    TourPlayer guest = addPlayer(server, "Guest");
    setJoined(guest, true);

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    guest.disconnect();
    server.getScheduler().performTicks(24000);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void registerWhenDisabledLogsThatItIsDisabled() {
    WorldTourModule module = module("");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("World Tour module is disabled."), log.messages(Level.INFO));
    }
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    WorldTourModule module = module(ENABLED);

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }

    assertTrue(module.isEnabled());
  }

  @Test
  void isNotEnabledBeforeRegister() {
    assertFalse(module(ENABLED).isEnabled());
  }

  @Test
  void registerWhenEnabledLogsThatItIsEnabled() {
    WorldTourModule module = module(ENABLED);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("World Tour module has been enabled."), log.messages(Level.INFO));
      assertTrue(log.messages(Level.SEVERE).isEmpty());
    }
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    registered("");

    verify(IceCream.annotationParser).parse(any(WorldTourCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    WorldTourModule module = registered("    failsafe-timeout-minutes: 1\n");
    TourPlayer guest = addPlayer(server, "Guest");
    setJoined(guest, true);

    guest.disconnect();
    server.getScheduler().performTicks(ONE_MINUTE);

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertTrue(module.isEnabled());
  }

  @Test
  void joinedKeyIsInThePluginNamespace() {
    registered("");

    assertEquals("icecream:worldtour.joined", WorldTourModule.JOINED_KEY.asString());
  }

  @Test
  void registeringAgainKeepsTheSameKey() {
    WorldTourModule module = registered("");
    NamespacedKey first = WorldTourModule.JOINED_KEY;

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }

    assertSame(first, WorldTourModule.JOINED_KEY);
  }

  @Test
  void failsafeTimeoutDefaultsToFiveMinutes() {
    assertEquals(5, registered("").getFailsafeTimeoutMinutes());
  }

  @Test
  void failsafeTimeoutReadsTheConfiguredMinutes() {
    assertEquals(12, registered("    failsafe-timeout-minutes: 12\n").getFailsafeTimeoutMinutes());
  }

  @Test
  void failsafeTimeoutOfZeroBecomesOneMinute() {
    assertEquals(1, registered("    failsafe-timeout-minutes: 0\n").getFailsafeTimeoutMinutes());
  }

  @Test
  void failsafeTimeoutOfOneMinuteIsKept() {
    assertEquals(1, registered("    failsafe-timeout-minutes: 1\n").getFailsafeTimeoutMinutes());
  }

  @Test
  void negativeFailsafeTimeoutBecomesOneMinute() {
    assertEquals(1, registered("    failsafe-timeout-minutes: -7\n").getFailsafeTimeoutMinutes());
  }

  @Test
  void startTimeDefaultsToTheDayTime() {
    assertEquals(1000L, registered("").getStartTimeTicks());
  }

  @Test
  void startTimeFollowsAConfiguredDayTime() {
    assertEquals(3000L, registered("    times:\n      day: 3000\n").getStartTimeTicks());
  }

  @Test
  void startTimeReadsTheConfiguredTicks() {
    assertEquals(
        5000L,
        registered("    start-time-ticks: 5000\n    times:\n      day: 3000\n")
            .getStartTimeTicks());
  }

  @Test
  void startTimeWrapsAroundADay() {
    assertEquals(1000L, registered("    start-time-ticks: 25000\n").getStartTimeTicks());
  }

  @Test
  void startTimeOfExactlyADayWrapsToZero() {
    assertEquals(0L, registered("    start-time-ticks: 24000\n").getStartTimeTicks());
  }

  @Test
  void negativeStartTimeWrapsBackwardsFromTheEndOfTheDay() {
    assertEquals(23000L, registered("    start-time-ticks: -1000\n").getStartTimeTicks());
  }

  @Test
  void timeAliasesDefaultToTheVanillaTimes() {
    WorldTourModule module = registered("");

    assertEquals(1000L, module.getTimeTicks("day"));
    assertEquals(6000L, module.getTimeTicks("noon"));
    assertEquals(13000L, module.getTimeTicks("night"));
    assertEquals(18000L, module.getTimeTicks("midnight"));
  }

  @Test
  void timeAliasesReadTheConfiguredTicks() {
    WorldTourModule module =
        registered(
            """
                times:
                  day: 2000
                  noon: 7000
                  night: 14000
                  midnight: 19000
            """);

    assertEquals(2000L, module.getTimeTicks("day"));
    assertEquals(7000L, module.getTimeTicks("noon"));
    assertEquals(14000L, module.getTimeTicks("night"));
    assertEquals(19000L, module.getTimeTicks("midnight"));
  }

  @Test
  void aTimeAliasLeftOutKeepsItsDefault() {
    WorldTourModule module = registered("    times:\n      noon: 7000\n");

    assertEquals(7000L, module.getTimeTicks("noon"));
    assertEquals(1000L, module.getTimeTicks("day"));
    assertEquals(13000L, module.getTimeTicks("night"));
    assertEquals(18000L, module.getTimeTicks("midnight"));
  }

  @Test
  void timeAliasesWrapAroundADay() {
    WorldTourModule module = registered("    times:\n      night: 38000\n      midnight: -6000\n");

    assertEquals(14000L, module.getTimeTicks("night"));
    assertEquals(18000L, module.getTimeTicks("midnight"));
  }

  @Test
  void unknownTimeAliasIsRejected() {
    WorldTourModule module = registered("");

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> module.getTimeTicks("dusk"));

    assertEquals("Unknown time type: dusk", thrown.getMessage());
  }

  @Test
  void registeringAgainReadsTheConfigurationAgain() {
    WorldTourModule module = registered("    times:\n      day: 2000\n");
    IceCream.config.set("modules.worldtour.times.day", 4000);
    IceCream.config.set("modules.worldtour.failsafe-timeout-minutes", 9);

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }

    assertEquals(4000L, module.getTimeTicks("day"));
    assertEquals(9, module.getFailsafeTimeoutMinutes());
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    WorldTourModule module = module(ENABLED);
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no parser"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register World Tour module: no parser"), log.messages(Level.SEVERE));
      assertTrue(log.messages(Level.INFO).isEmpty());
    }

    assertFalse(module.isEnabled());
  }

  @Test
  void registerWhenCommandRegistrationFailsRegistersNoListener() {
    WorldTourModule module = module(ENABLED);
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no parser"));
    TourPlayer guest = addPlayer(server, "Guest");

    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    setJoined(guest, true);
    guest.disconnect();
    server.getScheduler().performTicks(24000);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
  }

  @Test
  void registerWhenEventRegistrationFailsStaysDisabled() {
    WorldTourModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register World Tour module: no server"), log.messages(Level.SEVERE));
      assertTrue(log.messages(Level.INFO).isEmpty());
    }

    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterMarksTheModuleDisabled() {
    WorldTourModule module = registered("");

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterBeforeRegisterIsSafe() {
    WorldTourModule module = module(ENABLED);

    module.unregister();

    assertFalse(module.isEnabled());
    assertNull(module.getCurrentHost());
  }

  @Test
  void unregisterEndsTheHostsTour() {
    server.addSimpleWorld("world");
    WorldTourModule module = registered("");
    TourPlayer host = addPlayer(server, "Host");
    module.setCurrentHost(host);
    module.setGlowing(host);
    module.applyTourEnvironment();

    module.unregister();

    assertNull(module.getCurrentHost());
    assertNull(module.getCurrentGlowing());
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
  }

  @Test
  void unregisterRestoresTheEnvironmentTheTourChanged() {
    WorldMock world = server.addSimpleWorld("world");
    world.setFullTime(30500);
    world.setStorm(true);
    WorldTourModule module = registered("");
    module.setCurrentHost(addPlayer(server, "Host"));
    module.applyTourEnvironment();
    assertFalse(world.hasStorm());

    module.unregister();

    assertTrue(world.hasStorm());
    assertEquals(30500L, world.getFullTime());
  }

  @Test
  void unregisterCancelsThePendingFailsafeTasks() {
    WorldTourModule module = registered("    failsafe-timeout-minutes: 1\n");
    TourPlayer guest = addPlayer(server, "Guest");
    setJoined(guest, true);
    guest.disconnect();

    module.unregister();
    server.getScheduler().performTicks(ONE_MINUTE * 2);

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals(0, guest.saveCount());
    assertTrue(module.disconnectTasks.isEmpty());
  }
}
