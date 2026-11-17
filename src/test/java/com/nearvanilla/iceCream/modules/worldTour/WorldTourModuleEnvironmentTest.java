package com.nearvanilla.iceCream.modules.worldTour;

import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import org.bukkit.GameRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Tests how the tour changes the weather, the time and the keepInventory rule of every loaded
 * world, and how it puts them back.
 */
class WorldTourModuleEnvironmentTest {
  private ServerMock server;
  private WorldMock overworld;
  private WorldMock nether;
  private WorldTourModule module;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    overworld = server.addSimpleWorld("world");
    nether = server.addSimpleWorld("world_nether");
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void weather(WorldMock world, boolean storm, boolean thundering, int rain, int thunder) {
    world.setStorm(storm);
    world.setThundering(thundering);
    world.setWeatherDuration(rain);
    world.setThunderDuration(thunder);
  }

  @Test
  void clearWeatherStopsTheStormInEveryWorld() {
    weather(overworld, true, true, 100, 200);
    weather(nether, true, false, 300, 400);

    module.setClearWeather();

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertFalse(nether.hasStorm());
    assertFalse(nether.isThundering());
  }

  @Test
  void clearWeatherNameClearsTheWeather() {
    weather(overworld, true, true, 100, 200);

    module.setWeather("clear");

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
  }

  @Test
  void rainNameStartsRainWithoutThunderInEveryWorld() {
    weather(overworld, false, true, 100, 200);

    module.setWeather("rain");

    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertTrue(nether.hasStorm());
    assertFalse(nether.isThundering());
  }

  @Test
  void thunderNameStartsAThunderstormInEveryWorld() {
    module.setWeather("thunder");

    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertTrue(nether.hasStorm());
    assertTrue(nether.isThundering());
  }

  @Test
  void unknownWeatherNameIsRejectedAndChangesNothing() {
    weather(overworld, true, false, 100, 200);

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> module.setWeather("snow"));

    assertEquals("Unknown weather type: snow", thrown.getMessage());
    assertTrue(overworld.hasStorm());
  }

  @Test
  void weatherNamesAreCaseSensitive() {
    assertThrows(IllegalArgumentException.class, () -> module.setWeather("RAIN"));
  }

  @Test
  void restoringWeatherPutsEveryWorldBackToItsOwnState() {
    weather(overworld, true, true, 100, 200);
    weather(nether, false, false, 300, 400);
    module.setWeather("clear");
    module.setWeather("thunder");

    module.restoreWeather();

    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertEquals(100, overworld.getWeatherDuration());
    assertEquals(200, overworld.getThunderDuration());
    assertFalse(nether.hasStorm());
    assertFalse(nether.isThundering());
    assertEquals(300, nether.getWeatherDuration());
    assertEquals(400, nether.getThunderDuration());
  }

  @Test
  void theWeatherSavedIsTheOneFromBeforeTheFirstChange() {
    weather(overworld, true, false, 100, 200);

    module.setWeather("thunder");
    module.setWeather("clear");
    module.restoreWeather();

    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
  }

  @Test
  void restoringWeatherWhenNothingWasChangedLeavesTheWeatherAlone() {
    weather(overworld, true, true, 100, 200);

    module.restoreWeather();

    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertEquals(100, overworld.getWeatherDuration());
  }

  @Test
  void restoringWeatherTwiceDoesNotUndoLaterChanges() {
    weather(overworld, true, false, 100, 200);
    module.setWeather("clear");
    module.restoreWeather();
    overworld.setStorm(false);

    module.restoreWeather();

    assertFalse(overworld.hasStorm());
  }

  @Test
  void aSecondTourSavesTheWeatherAgain() {
    weather(overworld, true, false, 100, 200);
    module.setWeather("clear");
    module.restoreWeather();
    overworld.setStorm(false);

    module.setWeather("thunder");
    module.restoreWeather();

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
  }

  @Test
  void aWorldAddedAfterTheWeatherWasSavedIsLeftAlone() {
    module.setWeather("clear");
    WorldMock end = server.addSimpleWorld("world_the_end");
    end.setStorm(true);

    module.restoreWeather();

    assertTrue(end.hasStorm());
  }

  @Test
  void settingTheTimeSetsTheTimeOfDayInEveryWorldAndKeepsTheDay() {
    overworld.setFullTime(50000);
    nether.setFullTime(100500);

    module.setTime(13000);

    assertEquals(13000L, overworld.getTime());
    assertEquals(48000L + 13000L, overworld.getFullTime());
    assertEquals(13000L, nether.getTime());
    assertEquals(96000L + 13000L, nether.getFullTime());
  }

  @Test
  void restoringTimePutsEveryWorldBackToItsFullTime() {
    overworld.setFullTime(50000);
    nether.setFullTime(100500);
    module.setTime(13000);

    module.restoreTime();

    assertEquals(50000L, overworld.getFullTime());
    assertEquals(100500L, nether.getFullTime());
  }

  @Test
  void theTimeSavedIsTheOneFromBeforeTheFirstChange() {
    overworld.setFullTime(50000);

    module.setTime(13000);
    module.setTime(6000);
    module.restoreTime();

    assertEquals(50000L, overworld.getFullTime());
  }

  @Test
  void restoringTimeWhenNothingWasChangedLeavesTheTimeAlone() {
    overworld.setFullTime(50000);

    module.restoreTime();

    assertEquals(50000L, overworld.getFullTime());
  }

  @Test
  void restoringTimeTwiceDoesNotUndoLaterChanges() {
    overworld.setFullTime(50000);
    module.setTime(13000);
    module.restoreTime();
    overworld.setFullTime(70000);

    module.restoreTime();

    assertEquals(70000L, overworld.getFullTime());
  }

  @Test
  void aWorldAddedAfterTheTimeWasSavedIsLeftAlone() {
    module.setTime(13000);
    WorldMock end = server.addSimpleWorld("world_the_end");
    end.setFullTime(777);

    module.restoreTime();

    assertEquals(777L, end.getFullTime());
  }

  @Test
  void keepInventoryIsEnabledInEveryWorld() {
    module.enableKeepInventory();

    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void restoringKeepInventoryPutsEveryWorldBackToItsOwnValue() {
    overworld.setGameRule(GameRules.KEEP_INVENTORY, true);
    nether.setGameRule(GameRules.KEEP_INVENTORY, false);
    module.saveKeepInventory();
    module.enableKeepInventory();

    module.restoreKeepInventory();

    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.FALSE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void theKeepInventoryValueSavedIsTheOneFromBeforeTheFirstSave() {
    module.saveKeepInventory();
    module.enableKeepInventory();

    module.saveKeepInventory();
    module.restoreKeepInventory();

    assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void restoringKeepInventoryWhenNothingWasSavedLeavesTheRuleAlone() {
    module.enableKeepInventory();

    module.restoreKeepInventory();

    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void restoringKeepInventoryTwiceDoesNotUndoLaterChanges() {
    module.saveKeepInventory();
    module.enableKeepInventory();
    module.restoreKeepInventory();
    overworld.setGameRule(GameRules.KEEP_INVENTORY, true);

    module.restoreKeepInventory();

    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void aWorldAddedAfterKeepInventoryWasSavedIsLeftAlone() {
    module.saveKeepInventory();
    WorldMock end = server.addSimpleWorld("world_the_end");
    end.setGameRule(GameRules.KEEP_INVENTORY, true);

    module.restoreKeepInventory();

    assertEquals(Boolean.TRUE, end.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void theTourEnvironmentIsClearWeatherTheStartTimeAndKeepInventoryInEveryWorld() {
    weather(overworld, true, true, 100, 200);
    weather(nether, true, false, 300, 400);
    overworld.setFullTime(50000);
    nether.setFullTime(100500);

    module.applyTourEnvironment();

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertFalse(nether.hasStorm());
    assertEquals(1000L, overworld.getTime());
    assertEquals(1000L, nether.getTime());
    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void theTourEnvironmentUsesTheConfiguredStartTime() {
    IceCream.config.set("modules.worldtour.start-time-ticks", 18000);
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }

    module.applyTourEnvironment();

    assertEquals(18000L, overworld.getTime());
    assertEquals(18000L, nether.getTime());
  }

  @Test
  void theTourEnvironmentCanBeRestoredInFull() {
    weather(overworld, true, true, 100, 200);
    weather(nether, false, false, 300, 400);
    overworld.setFullTime(50000);
    nether.setFullTime(100500);
    overworld.setGameRule(GameRules.KEEP_INVENTORY, false);
    nether.setGameRule(GameRules.KEEP_INVENTORY, true);
    module.setCurrentHost(addPlayer(server, "Host"));
    module.applyTourEnvironment();

    module.clearHost(true);

    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertEquals(100, overworld.getWeatherDuration());
    assertEquals(200, overworld.getThunderDuration());
    assertFalse(nether.hasStorm());
    assertEquals(300, nether.getWeatherDuration());
    assertEquals(50000L, overworld.getFullTime());
    assertEquals(100500L, nether.getFullTime());
    assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
    assertEquals(Boolean.TRUE, nether.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void applyingTheTourEnvironmentAgainKeepsTheOriginalState() {
    overworld.setStorm(true);
    overworld.setFullTime(50000);
    module.setCurrentHost(addPlayer(server, "Host"));
    module.applyTourEnvironment();

    module.applyTourEnvironment();
    module.clearHost(true);

    assertTrue(overworld.hasStorm());
    assertEquals(50000L, overworld.getFullTime());
    assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }
}
