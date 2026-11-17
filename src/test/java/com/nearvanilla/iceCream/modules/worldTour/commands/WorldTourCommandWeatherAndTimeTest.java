package com.nearvanilla.iceCream.modules.worldTour.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Tests the commands that change the weather and the time of every world during a tour. */
class WorldTourCommandWeatherAndTimeTest {
  // The times differ from the vanilla ones, so a test can tell that the configuration is used.
  private static final String CONFIG =
      """
      modules:
        worldtour:
          enabled: true
          start-time-ticks: 1000
          times:
            day: 2000
            noon: 7000
            night: 14000
            midnight: 19000
      """;
  private static final String PLAYERS_ONLY = "✗ This command can only be used by players.";
  private static final String NO_HOST = "✗ There is no World Tour host set.";

  private ServerMock server;
  private WorldMock overworld;
  private WorldMock nether;
  private WorldTourModule module;
  private WorldTourCommand command;
  private TourPlayer host;
  private TourPlayer guest;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, CONFIG);
    overworld = server.addSimpleWorld("world");
    nether = server.addSimpleWorld("world_nether");
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    command = new WorldTourCommand(module);
    host = addPlayer(server, "Host");
    guest = addPlayer(server, "Guest");
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void startTourAs(TourPlayer player) {
    command.startCommand(source(player));
    nextMessage(player);
  }

  private void weather(boolean storm, boolean thundering) {
    for (WorldMock world : new WorldMock[] {overworld, nether}) {
      world.setStorm(storm);
      world.setThundering(thundering);
    }
  }

  // Weather

  @Test
  void weatherCanBeSetToRainInEveryWorld() {
    startTourAs(host);
    weather(false, false);

    command.weatherCommand(source(host), "rain");

    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertTrue(nether.hasStorm());
    assertFalse(nether.isThundering());
  }

  @Test
  void weatherCanBeSetToThunderInEveryWorld() {
    startTourAs(host);
    weather(false, false);

    command.weatherCommand(source(host), "thunder");

    assertTrue(overworld.hasStorm());
    assertTrue(overworld.isThundering());
    assertTrue(nether.hasStorm());
    assertTrue(nether.isThundering());
  }

  @Test
  void weatherCanBeSetToClearInEveryWorld() {
    startTourAs(host);
    weather(true, true);

    command.weatherCommand(source(host), "clear");

    assertFalse(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertFalse(nether.hasStorm());
    assertFalse(nether.isThundering());
  }

  @Test
  void changingTheWeatherTellsThePlayer() {
    startTourAs(host);

    command.weatherCommand(source(host), "rain");

    assertEquals("✓ Weather has been updated.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void weatherTypesIgnoreCase() {
    startTourAs(host);
    weather(false, false);

    command.weatherCommand(source(host), "THUNDER");

    assertTrue(overworld.isThundering());

    command.weatherCommand(source(host), "Rain");

    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
  }

  @Test
  void anUnknownWeatherTypeIsRefusedAndChangesNothing() {
    startTourAs(host);
    weather(true, false);

    command.weatherCommand(source(host), "snow");

    assertEquals("✗ Unknown weather type. Use: clear, rain, or thunder.", nextMessage(host));
    assertNull(nextMessage(host));
    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
  }

  @Test
  void anEmptyWeatherTypeIsRefused() {
    startTourAs(host);

    command.weatherCommand(source(host), "");

    assertEquals("✗ Unknown weather type. Use: clear, rain, or thunder.", nextMessage(host));
  }

  @Test
  void weatherNeedsATourToBeRunning() {
    weather(false, false);

    command.weatherCommand(source(guest), "rain");

    assertEquals(NO_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertFalse(overworld.hasStorm());
  }

  @Test
  void weatherCannotBeChangedWhileTheHostIsOffline() {
    startTourAs(host);
    host.disconnect();
    weather(false, false);

    command.weatherCommand(source(guest), "rain");

    assertEquals(NO_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertFalse(overworld.hasStorm());
    assertFalse(nether.hasStorm());
  }

  @Test
  void anyPlayerCanChangeTheWeatherWhileTheHostIsOnline() {
    startTourAs(host);
    weather(false, false);

    command.weatherCommand(source(guest), "rain");

    assertEquals("✓ Weather has been updated.", nextMessage(guest));
    assertTrue(overworld.hasStorm());
  }

  @Test
  void theWeatherTheTourChangedIsRestoredWhenItEnds() {
    weather(true, false);
    overworld.setWeatherDuration(500);
    startTourAs(host);
    command.weatherCommand(source(host), "thunder");

    command.endCommand(source(host));

    assertTrue(overworld.hasStorm());
    assertFalse(overworld.isThundering());
    assertEquals(500, overworld.getWeatherDuration());
  }

  @Test
  void changingTheWeatherIsRefusedForTheConsole() {
    startTourAs(host);
    weather(false, false);
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.weatherCommand(source(console), "rain");

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertFalse(overworld.hasStorm());
  }

  // Time

  @Test
  void timeCanBeSetToTheConfiguredDay() {
    startTourAs(host);

    command.timeCommand(source(host), "day");

    assertEquals(2000L, overworld.getTime());
    assertEquals(2000L, nether.getTime());
  }

  @Test
  void timeCanBeSetToTheConfiguredNoon() {
    startTourAs(host);

    command.timeCommand(source(host), "noon");

    assertEquals(7000L, overworld.getTime());
    assertEquals(7000L, nether.getTime());
  }

  @Test
  void timeCanBeSetToTheConfiguredNight() {
    startTourAs(host);

    command.timeCommand(source(host), "night");

    assertEquals(14000L, overworld.getTime());
    assertEquals(14000L, nether.getTime());
  }

  @Test
  void timeCanBeSetToTheConfiguredMidnight() {
    startTourAs(host);

    command.timeCommand(source(host), "midnight");

    assertEquals(19000L, overworld.getTime());
    assertEquals(19000L, nether.getTime());
  }

  @Test
  void changingTheTimeTellsThePlayer() {
    startTourAs(host);

    command.timeCommand(source(host), "noon");

    assertEquals("✓ Time has been updated.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void changingTheTimeKeepsTheDay() {
    overworld.setFullTime(50000);
    startTourAs(host);

    command.timeCommand(source(host), "night");

    assertEquals(48000L + 14000L, overworld.getFullTime());
  }

  @Test
  void timeTypesIgnoreCase() {
    startTourAs(host);

    command.timeCommand(source(host), "NOON");

    assertEquals(7000L, overworld.getTime());

    command.timeCommand(source(host), "Midnight");

    assertEquals(19000L, overworld.getTime());
  }

  @Test
  void anUnknownTimeTypeIsRefusedAndChangesNothing() {
    startTourAs(host);
    long before = overworld.getFullTime();

    command.timeCommand(source(host), "dusk");

    assertEquals("✗ Unknown time type. Use: day, night, noon, or midnight.", nextMessage(host));
    assertNull(nextMessage(host));
    assertEquals(before, overworld.getFullTime());
  }

  @Test
  void anEmptyTimeTypeIsRefused() {
    startTourAs(host);

    command.timeCommand(source(host), "");

    assertEquals("✗ Unknown time type. Use: day, night, noon, or midnight.", nextMessage(host));
  }

  @Test
  void timeNeedsATourToBeRunning() {
    overworld.setFullTime(50000);

    command.timeCommand(source(guest), "night");

    assertEquals(NO_HOST, nextMessage(guest));
    assertNull(nextMessage(guest));
    assertEquals(50000L, overworld.getFullTime());
  }

  @Test
  void timeCannotBeChangedWhileTheHostIsOffline() {
    startTourAs(host);
    host.disconnect();
    long before = overworld.getFullTime();

    command.timeCommand(source(guest), "night");

    assertEquals(before, overworld.getFullTime());
    assertEquals(before, nether.getFullTime());
  }

  @Test
  void anyPlayerCanChangeTheTimeWhileTheHostIsOnline() {
    startTourAs(host);

    command.timeCommand(source(guest), "night");

    assertEquals("✓ Time has been updated.", nextMessage(guest));
    assertEquals(14000L, overworld.getTime());
  }

  @Test
  void theTimeTheTourChangedIsRestoredWhenItEnds() {
    overworld.setFullTime(50000);
    nether.setFullTime(100500);
    startTourAs(host);
    command.timeCommand(source(host), "midnight");

    command.endCommand(source(host));

    assertEquals(50000L, overworld.getFullTime());
    assertEquals(100500L, nether.getFullTime());
  }

  @Test
  void changingTheTimeIsRefusedForTheConsole() {
    startTourAs(host);
    long before = overworld.getFullTime();
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.timeCommand(source(console), "night");

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertEquals(before, overworld.getFullTime());
  }
}
