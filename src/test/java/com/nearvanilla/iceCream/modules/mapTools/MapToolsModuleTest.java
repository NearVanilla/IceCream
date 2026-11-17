package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.IntegrationTestUtils;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentCaptor;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;

/** The map tools module: when it turns on, what it sets up, and how it takes everything down. */
class MapToolsModuleTest {
  private static final String ENABLED = "modules:\n  maptools:\n    enabled: true\n";
  private static final Key CLAIMS = Key.of("icecream_areas");
  private static final Key MARKERS = Key.of("icecream_markers");

  private DisplayScenario drawing;
  private ServerMock server;
  private Squaremap api;
  private Registry<LayerProvider> layers;
  private LogCapture log;
  private final MapToolsModule module = new MapToolsModule();

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open(ENABLED);
    server = drawing.server();
    log = LogCapture.of(IceCream.logger);
    api = mock(Squaremap.class);
    layers = mock(Registry.class);
    MapWorld overworld = mock(MapWorld.class);
    when(overworld.layerRegistry()).thenReturn(layers);
    when(api.iconRegistry()).thenReturn(mock(Registry.class));
    when(api.getWorldIfEnabled(any(WorldIdentifier.class)))
        .thenAnswer(
            invocation ->
                invocation
                        .<WorldIdentifier>getArgument(0)
                        .asString()
                        .equals(MapToolsTestUtils.OVERWORLD)
                    ? Optional.of(overworld)
                    : Optional.empty());
  }

  @AfterEach
  void tearDown() {
    module.unregister();
    log.close();
    IntegrationTestUtils.unregisterSquaremap();
    drawing.close();
  }

  /** Squaremap is installed and has loaded its API. */
  private void squaremapPresent() {
    IntegrationTestUtils.installPlugin("squaremap");
    IntegrationTestUtils.registerSquaremap(api);
  }

  /** Replaces the configuration, which also replaces the logger the capture listens to. */
  private void configure(String yaml) {
    log.close();
    drawing.scenario().configure(yaml);
    log = LogCapture.of(IceCream.logger);
  }

  // Whether the module is on

  @Test
  void enabledWhenTheConfigurationSaysSo() {
    assertTrue(module.shouldEnable());
  }

  @Test
  void disabledWhenTheKeyIsMissing() {
    configure("");

    assertFalse(module.shouldEnable());
  }

  @Test
  void disabledWhenTheConfigurationSaysSo() {
    configure("modules:\n  maptools:\n    enabled: false\n");

    assertFalse(module.shouldEnable());
  }

  @Test
  void notEnabledBeforeItIsRegistered() {
    assertFalse(module.isEnabled());
  }

  // Registering

  @Test
  void registeringADisabledModuleDoesNothing() {
    configure("");
    squaremapPresent();
    MapAreasStore store = MapToolsModule.store;

    module.register();

    assertFalse(module.isEnabled());
    assertSame(store, MapToolsModule.store);
    assertEquals(List.of("MapTools module is disabled."), log.messages(Level.INFO));
    verifyNoInteractions(api);
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertTrue(server.getScheduler().getPendingTasks().isEmpty());
  }

  @Test
  void registeringWithoutSquaremapLeavesTheModuleOff() {
    MapAreasStore store = MapToolsModule.store;

    module.register();

    assertFalse(module.isEnabled());
    assertSame(store, MapToolsModule.store, "no store is loaded without squaremap");
    assertEquals(
        List.of("MapTools module requires squaremap, disabling."), log.messages(Level.WARNING));
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertTrue(server.getScheduler().getPendingTasks().isEmpty());
  }

  @Test
  void registeringWhileSquaremapHasNotLoadedItsApiLeavesTheModuleOff() {
    IntegrationTestUtils.installPlugin("squaremap");

    module.register();

    assertFalse(module.isEnabled());
    assertEquals(
        List.of(
            "squaremap found but its API is not loaded.",
            "MapTools module requires squaremap, disabling."),
        log.messages(Level.WARNING));
  }

  @Test
  void registeringWithSquaremapEnablesTheModule() {
    squaremapPresent();

    module.register();

    assertTrue(module.isEnabled());
    assertTrue(log.messages(Level.INFO).contains("MapTools module has been enabled."));
    assertTrue(log.messages(Level.WARNING).isEmpty());
  }

  @Test
  void theStoredAreasAreLoadedFromTheDataFolder() {
    squaremapPresent();
    MapAreasStore saved =
        new MapAreasStore(new File(IceCream.instance.getDataFolder(), "map-areas.json"));
    saved.putArea(MapToolsTestUtils.rect("home", MapToolsTestUtils.OVERWORLD, 0, 0, 9, 9));

    module.register();

    assertNotSame(saved, MapToolsModule.store);
    assertNotNull(MapToolsModule.store.getArea("home"));
    assertEquals(1, MapToolsModule.store.getAreas().size());
  }

  @Test
  void aMissingAreasFileGivesAnEmptyStore() {
    squaremapPresent();

    module.register();

    assertTrue(MapToolsModule.store.getAreas().isEmpty());
  }

  @Test
  void theLayersAreAddedToTheWorldsSquaremapMapsWithTheStoredAreas() {
    squaremapPresent();
    new MapAreasStore(new File(IceCream.instance.getDataFolder(), "map-areas.json"))
        .putArea(MapToolsTestUtils.rect("home", MapToolsTestUtils.OVERWORLD, 0, 0, 9, 9));

    module.register();

    ArgumentCaptor<Key> keys = ArgumentCaptor.forClass(Key.class);
    ArgumentCaptor<LayerProvider> provided = ArgumentCaptor.forClass(LayerProvider.class);
    verify(layers, times(2)).register(keys.capture(), provided.capture());
    assertEquals(List.of(CLAIMS, MARKERS), keys.getAllValues());
    assertEquals(1, provided.getAllValues().getFirst().getMarkers().size());
  }

  @Test
  void theCommandsAreRegistered() {
    squaremapPresent();

    module.register();

    ArgumentCaptor<Object> parsed = ArgumentCaptor.forClass(Object.class);
    verify(IceCream.annotationParser).parse(parsed.capture());
    assertInstanceOf(MapToolsCommand.class, parsed.getValue());
  }

  @Test
  void theListenerIsRegisteredSoLeavingTheServerEndsEditMode() {
    squaremapPresent();
    module.register();
    Viewer alex = drawing.viewer();
    MapToolsUtils.startEditing(alex, EditSession.view());

    server.getPluginManager().callEvent(new PlayerQuitEvent(alex, "left"));

    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void registerCommandsAloneRegistersTheCommandsOnly() {
    module.registerCommands();

    verify(IceCream.annotationParser).parse(any(MapToolsCommand.class));
    assertFalse(module.isEnabled());
  }

  @Test
  void registerEventsAloneRegistersTheListener() {
    Viewer alex = drawing.viewer();
    MapToolsModule.sessions.put(alex.getUniqueId(), EditSession.view());

    module.registerEvents();
    server.getPluginManager().callEvent(new PlayerQuitEvent(alex, "left"));

    assertFalse(MapToolsUtils.isEditing(alex));
  }

  // The task that keeps the displays and the action bar up to date

  @Test
  void theBorderTaskRunsEveryHalfSecondStartingAfterTheFirst() {
    squaremapPresent();
    module.register();
    Viewer alex = drawing.viewer();
    MapToolsUtils.startEditing(alex, EditSession.view());

    server.getScheduler().performTicks(9);
    assertNull(alex.nextActionBar(), "not before 10 ticks");

    server.getScheduler().performTicks(1);
    assertEquals("Edit mode", MessageTestUtils.plainText(alex.nextActionBar()));
    assertNull(alex.nextActionBar());

    server.getScheduler().performTicks(9);
    assertNull(alex.nextActionBar());
    server.getScheduler().performTicks(1);
    assertEquals("Edit mode", MessageTestUtils.plainText(alex.nextActionBar()));
  }

  @Test
  void theBorderTaskDoesNotRunUntilTheModuleIsRegistered() {
    Viewer alex = drawing.viewer();
    MapToolsUtils.startEditing(alex, EditSession.view());

    server.getScheduler().performTicks(40);

    assertNull(alex.nextActionBar());
  }

  // Failing to start

  @Test
  void aFailureWhileStartingIsLoggedAndTheModuleStaysOff() {
    squaremapPresent();
    when(api.getWorldIfEnabled(any(WorldIdentifier.class)))
        .thenThrow(new IllegalStateException("squaremap went away"));

    module.register();

    assertFalse(module.isEnabled());
    List<LogRecord> severe =
        log.records().stream().filter(r -> r.getLevel() == Level.SEVERE).toList();
    assertEquals(1, severe.size());
    assertEquals("Failed to register MapTools module", severe.getFirst().getMessage());
    assertEquals("squaremap went away", severe.getFirst().getThrown().getMessage());
    assertFalse(log.messages(Level.INFO).contains("MapTools module has been enabled."));
  }

  @Test
  void aFailedStartRegistersNoCommandsAndNoTask() {
    squaremapPresent();
    when(api.getWorldIfEnabled(any(WorldIdentifier.class)))
        .thenThrow(new IllegalStateException("squaremap went away"));
    Viewer alex = drawing.viewer();

    module.register();
    MapToolsUtils.startEditing(alex, EditSession.view());
    server.getScheduler().performTicks(40);

    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertNull(alex.nextActionBar());
  }

  @Test
  void aFailureAfterTheLayersWereAddedTakesThemOutAgain() {
    squaremapPresent();
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no commands"));

    module.register();

    assertFalse(module.isEnabled());
    verify(layers, times(2)).register(any(Key.class), any(LayerProvider.class));
    verify(layers).unregister(CLAIMS);
    verify(layers).unregister(MARKERS);
    assertEquals(
        "Failed to register MapTools module",
        log.records().stream()
            .filter(r -> r.getLevel() == Level.SEVERE)
            .findFirst()
            .orElseThrow()
            .getMessage());
  }

  // Taking it down

  @Test
  void unregisterRemovesTheLayersFromSquaremap() {
    squaremapPresent();
    module.register();

    module.unregister();

    verify(layers).unregister(CLAIMS);
    verify(layers).unregister(MARKERS);
  }

  @Test
  void unregisterStopsTheBorderTask() {
    squaremapPresent();
    module.register();
    Viewer alex = drawing.viewer();
    MapToolsUtils.startEditing(alex, EditSession.view());
    server.getScheduler().performTicks(10);
    assertNotNull(alex.nextActionBar());

    module.unregister();
    MapToolsModule.sessions.put(alex.getUniqueId(), EditSession.view());
    server.getScheduler().performTicks(40);

    assertNull(alex.nextActionBar());
  }

  @Test
  void unregisterDisablesTheModuleAndForgetsEverySession() {
    squaremapPresent();
    module.register();
    MapToolsUtils.startEditing(drawing.viewer(), EditSession.view());

    module.unregister();

    assertFalse(module.isEnabled());
    assertTrue(MapToolsModule.sessions.isEmpty());
  }

  @Test
  void unregisterRemovesWhatWasDrawnInGame() {
    squaremapPresent();
    module.register();
    MapToolsModule.store.putArea(
        MapToolsTestUtils.rect("home", DisplayScenario.DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);
    MapToolsUtils.startEditing(drawing.viewer(), EditSession.view());
    assertFalse(drawing.world().displays().isEmpty());

    module.unregister();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void unregisterBeforeTheModuleWasEverEnabledIsHarmless() {
    assertDoesNotThrow(new MapToolsModule()::unregister);
    assertDoesNotThrow(module::unregister);
    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterTwiceIsHarmless() {
    squaremapPresent();
    module.register();

    module.unregister();

    assertDoesNotThrow(module::unregister);
    verify(layers).unregister(CLAIMS);
  }

  @Test
  void theModuleCanBeRegisteredAgainAfterUnregistering() {
    squaremapPresent();
    module.register();
    module.unregister();

    module.register();

    assertTrue(module.isEnabled());
  }
}
