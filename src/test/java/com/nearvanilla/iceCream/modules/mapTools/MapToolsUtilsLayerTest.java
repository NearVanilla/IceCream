package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Circle;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.marker.Polygon;

/** Drawing areas on the squaremap layers, which are Mockito mocks of the squaremap API. */
class MapToolsUtilsLayerTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String NETHER = MapToolsTestUtils.NETHER;
  private static final Key CLAIMS = Key.of("icecream_areas");
  private static final Key MARKERS = Key.of("icecream_markers");
  private static final double DELTA = 1e-9;

  private Scenario scenario;
  private Squaremap api;
  private MapWorld overworldMap;
  private MapWorld netherMap;
  private Registry<LayerProvider> overworldLayers;
  private Registry<LayerProvider> netherLayers;
  private Registry<BufferedImage> icons;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    api = mock(Squaremap.class);
    overworldLayers = mock(Registry.class);
    netherLayers = mock(Registry.class);
    icons = mock(Registry.class);
    overworldMap = mapWorld(overworldLayers);
    netherMap = mapWorld(netherLayers);
    when(api.iconRegistry()).thenReturn(icons);
    when(api.getWorldIfEnabled(any(WorldIdentifier.class)))
        .thenAnswer(
            invocation -> {
              String id = invocation.<WorldIdentifier>getArgument(0).asString();
              if (id.equals(WORLD)) return Optional.of(overworldMap);
              if (id.equals(NETHER)) return Optional.of(netherMap);
              return Optional.empty();
            });
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private static MapWorld mapWorld(Registry<LayerProvider> layers) {
    MapWorld world = mock(MapWorld.class);
    when(world.layerRegistry()).thenReturn(layers);
    return world;
  }

  private LayerProvider registered(Registry<LayerProvider> registry, Key key) {
    ArgumentCaptor<Key> keys = ArgumentCaptor.forClass(Key.class);
    ArgumentCaptor<LayerProvider> layers = ArgumentCaptor.forClass(LayerProvider.class);
    verify(registry, atLeastOnce()).register(keys.capture(), layers.capture());
    int index = keys.getAllValues().lastIndexOf(key);
    assertTrue(index >= 0, "no layer registered under " + key);
    return layers.getAllValues().get(index);
  }

  private LayerProvider claims() {
    return registered(overworldLayers, CLAIMS);
  }

  private LayerProvider markers() {
    return registered(overworldLayers, MARKERS);
  }

  private MapArea stored(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private static MapPoint point(double x, double z) {
    return new MapPoint(x, z);
  }

  private void writeIcon(String name, int width, int height) throws IOException {
    File folder = new File(IceCreamFixture.dataFolder(), "map-icons");
    assertTrue(folder.mkdirs() || folder.isDirectory());
    ImageIO.write(
        new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB),
        "png",
        new File(folder, name + ".png"));
  }

  @Test
  void initRegistersTheClaimAndMarkerLayersOfEveryWorldSquaremapMaps() {
    MapToolsUtils.init(api);

    verify(overworldLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
    verify(netherLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
    assertNotNull(claims());
    assertNotNull(markers());
  }

  @Test
  void theLayersHaveTheDefaultLabelsAndPriorities() {
    MapToolsUtils.init(api);

    assertEquals("Claims", claims().getLabel());
    assertEquals(10, claims().layerPriority());
    assertEquals("Markers", markers().getLabel());
    assertEquals(11, markers().layerPriority());
  }

  @Test
  void theLayersTakeTheirLabelsFromTheConfiguration() {
    scenario.configure(
        "modules:\n  maptools:\n    layer-label: Plots\n    marker-layer-label: Pins\n");

    MapToolsUtils.init(api);

    assertEquals("Plots", claims().getLabel());
    assertEquals("Pins", markers().getLabel());
  }

  @Test
  void aWorldSquaremapDoesNotMapGetsNoLayers() {
    MapToolsUtils.init(api);

    // The limbo world is not mapped, so only the two mapped worlds got layers.
    verify(overworldLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
    verify(netherLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
    verify(api, times(3)).getWorldIfEnabled(any(WorldIdentifier.class));
  }

  @Test
  void aWorldIsOnlyRegisteredOnce() {
    MapToolsUtils.init(api);

    MapToolsUtils.registerWorld(scenario.overworld());
    MapToolsUtils.registerWorld(scenario.overworld());

    verify(overworldLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
  }

  @Test
  void registeringAWorldBeforeInitDoesNothing() {
    MapToolsUtils.registerWorld(scenario.overworld());

    verifyNoInteractions(api);
    verifyNoInteractions(overworldLayers);
  }

  @Test
  void registerWorldDrawsTheStoredAreasOfThatWorldOnly() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("fort", NETHER, 0, 0, 9, 9));
    stored(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    MapToolsUtils.init(api);

    assertEquals(1, claims().getMarkers().size());
    assertEquals(1, markers().getMarkers().size());
    assertEquals(1, registered(netherLayers, CLAIMS).getMarkers().size());
    assertEquals(0, registered(netherLayers, MARKERS).getMarkers().size());
  }

  @Test
  void aWorldRegisteredLaterIsDrawnWithItsAreas() {
    stored(MapToolsTestUtils.rect("fort", NETHER, 0, 0, 9, 9));
    when(api.getWorldIfEnabled(any(WorldIdentifier.class))).thenReturn(Optional.empty());
    MapToolsUtils.init(api);
    verify(netherLayers, never()).register(any(Key.class), any(LayerProvider.class));
    when(api.getWorldIfEnabled(any(WorldIdentifier.class))).thenReturn(Optional.of(netherMap));

    MapToolsUtils.registerWorld(scenario.nether());

    assertEquals(1, registered(netherLayers, CLAIMS).getMarkers().size());
  }

  @Test
  void shutdownRemovesTheLayersOfEveryRegisteredWorld() {
    MapToolsUtils.init(api);

    MapToolsUtils.shutdown();

    verify(overworldLayers).unregister(CLAIMS);
    verify(overworldLayers).unregister(MARKERS);
    verify(netherLayers).unregister(CLAIMS);
    verify(netherLayers).unregister(MARKERS);
  }

  @Test
  void shutdownRemovesTheIconsToo() throws IOException {
    writeIcon("pin", 16, 24);
    MapToolsUtils.init(api);
    assertTrue(MapToolsUtils.iconExists("pin"));

    MapToolsUtils.shutdown();

    verify(icons).unregister(Key.of("icecream_icon_pin"));
    assertFalse(MapToolsUtils.iconExists("pin"));
  }

  @Test
  void shutdownTwiceOrBeforeInitDoesNothing() {
    MapToolsUtils.shutdown();
    verifyNoInteractions(api);

    MapToolsUtils.init(api);
    MapToolsUtils.shutdown();
    MapToolsUtils.shutdown();

    verify(overworldLayers, times(1)).unregister(CLAIMS);
  }

  @Test
  void afterShutdownNothingIsDrawnAndWorldsCanBeRegisteredAgain() {
    MapToolsUtils.init(api);
    MapToolsUtils.shutdown();

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.registerWorld(scenario.overworld());

    verify(overworldLayers, times(2)).register(any(Key.class), any(LayerProvider.class));
  }

  @Test
  void aClaimIsDrawnAsAPolygonThroughItsPoints() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    Marker marker = claims().getMarkers().iterator().next();
    Polygon polygon = assertInstanceOf(Polygon.class, marker);
    assertEquals(
        List.of(Point.of(0, 0), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10)),
        polygon.mainPolygon());
  }

  @Test
  void aPolygonClaimIsDrawnAsAPolygon() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(
        MapToolsTestUtils.polygon("shape", WORLD, 0.5, 0.5, 8.5, 0.5, 4.5, 6.5));

    Polygon polygon = assertInstanceOf(Polygon.class, claims().getMarkers().iterator().next());
    assertEquals(
        List.of(Point.of(0.5, 0.5), Point.of(8.5, 0.5), Point.of(4.5, 6.5)), polygon.mainPolygon());
  }

  @Test
  void aCircleIsDrawnAsATrueCircleNotAsItsPoints() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.circle("pond", WORLD, 10, -20, 12));

    Circle circle = assertInstanceOf(Circle.class, claims().getMarkers().iterator().next());
    assertEquals(Point.of(10.5, -19.5), circle.center());
    assertEquals(12, circle.radius(), DELTA);
  }

  @Test
  void aCircleWithoutACenterOrRadiusFallsBackToItsPoints() {
    MapToolsUtils.init(api);
    MapArea broken =
        MapToolsTestUtils.polygon("broken", WORLD, 0, 0, 10, 0, 10, 10)
            .withGeometry(
                new AreaGeometry(
                    WORLD,
                    List.of(point(0, 0), point(10, 0), point(10, 10)),
                    MapArea.CIRCLE,
                    null,
                    null));

    MapToolsUtils.renderArea(broken);

    assertInstanceOf(Polygon.class, claims().getMarkers().iterator().next());
  }

  @Test
  void aMarkerWithoutAnIconIsASmallCircle() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.marker("spot", WORLD, 4, -7));

    Circle circle = assertInstanceOf(Circle.class, markers().getMarkers().iterator().next());
    assertEquals(Point.of(4.5, -6.5), circle.center());
    assertEquals(3, circle.radius(), DELTA);
    assertTrue(claims().getMarkers().isEmpty());
  }

  @Test
  void aMarkerWithAnIconThatDoesNotExistIsASmallCircle() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.marker("spot", WORLD, 4, -7).withIcon("missing"));

    assertInstanceOf(Circle.class, markers().getMarkers().iterator().next());
  }

  @Test
  void aMarkerWithAnIconIsDrawnWithThatIconAtItsOwnSize() throws IOException {
    writeIcon("pin", 16, 24);
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.marker("spot", WORLD, 4, -7).withIcon("pin"));

    Icon icon = assertInstanceOf(Icon.class, markers().getMarkers().iterator().next());
    assertEquals(Point.of(4.5, -6.5), icon.point());
    assertEquals(Key.of("icecream_icon_pin"), icon.image());
    assertEquals(16, icon.sizeX());
    assertEquals(24, icon.sizeZ());
  }

  @Test
  void anAreaInAWorldWithoutLayersIsNotDrawn() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", MapToolsTestUtils.LIMBO, 0, 0, 9, 9));

    assertTrue(claims().getMarkers().isEmpty());
    assertTrue(registered(netherLayers, CLAIMS).getMarkers().isEmpty());
  }

  @Test
  void aClaimIsDrawnInItsOwnColorAndFill() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withColor("#336699").withOpacity(0.7));

    MarkerOptions options = claims().getMarkers().iterator().next().markerOptions();
    assertEquals(new Color(0x336699), options.strokeColor());
    assertEquals(new Color(0x336699), options.fillColor());
    assertEquals(2, options.strokeWeight());
    assertEquals(0.7, options.fillOpacity(), DELTA);
  }

  @Test
  void aNamedColorIsDrawnAsItsColor() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withColor("red"));

    MarkerOptions options = claims().getMarkers().iterator().next().markerOptions();
    assertEquals(new Color(NamedTextColor.RED.value()), options.strokeColor());
  }

  @Test
  void aClaimWithoutAnOpacityUsesTheConfiguredFill() {
    scenario.configure("modules:\n  maptools:\n    claim-fill-opacity: 0.55\n");
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertEquals(
        0.55, claims().getMarkers().iterator().next().markerOptions().fillOpacity(), DELTA);
  }

  @Test
  void aClaimWithAnUnparseableColorIsDrawnInTheDefaultColor() {
    scenario.configure("modules:\n  maptools:\n    claim-color: blue\n");
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withColor("banana"));

    assertEquals(
        new Color(NamedTextColor.BLUE.value()),
        claims().getMarkers().iterator().next().markerOptions().fillColor());
  }

  @Test
  void theHoverTooltipIsTheEscapedLabel() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withLabel("Tom & <Jerry's> \"house\""));

    assertEquals(
        "Tom &amp; &lt;Jerry&#39;s&gt; &quot;house&quot;",
        claims().getMarkers().iterator().next().markerOptions().hoverTooltip());
  }

  @Test
  void theClickTooltipShowsOnlyTheLabelWithoutOwnersOrDescription() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withLabel("Home"));

    assertEquals(
        "<b>Home</b>", claims().getMarkers().iterator().next().markerOptions().clickTooltip());
  }

  @Test
  void theClickTooltipListsTheOwnersAndTheDescriptionEscaped() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)
            .withLabel("Home")
            .withOwners(List.of("Alex", "R&B"))
            .withDescription("Line one\r\nLine <two>\nLine three"));

    assertEquals(
        "<b>Home</b><br>Owner(s): Alex, R&amp;B<br>Line one<br>Line &lt;two&gt;<br>Line three",
        claims().getMarkers().iterator().next().markerOptions().clickTooltip());
  }

  @Test
  void aBlankDescriptionIsLeftOutOfTheClickTooltip() {
    MapToolsUtils.init(api);

    MapToolsUtils.renderArea(
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withDescription("   \n  "));

    assertEquals(
        "<b>Label home</b>",
        claims().getMarkers().iterator().next().markerOptions().clickTooltip());
  }

  @Test
  void drawingAnAreaAgainReplacesItsMarker() {
    MapToolsUtils.init(api);
    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    MapToolsUtils.renderArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withLabel("New"));

    assertEquals(1, claims().getMarkers().size());
    assertEquals("New", claims().getMarkers().iterator().next().markerOptions().hoverTooltip());
  }

  @Test
  void smallerClaimsAreHandedToTheMapAfterLargerOnes() {
    MapToolsUtils.init(api);
    MapToolsUtils.renderArea(MapToolsTestUtils.rect("small", WORLD, 10, 10, 14, 14));
    MapToolsUtils.renderArea(MapToolsTestUtils.rect("large", WORLD, 0, 0, 99, 99));
    MapToolsUtils.renderArea(MapToolsTestUtils.rect("medium", WORLD, 0, 0, 19, 19));

    List<String> hover =
        claims().getMarkers().stream().map(m -> m.markerOptions().hoverTooltip()).toList();

    assertEquals(List.of("Label large", "Label medium", "Label small"), hover);
  }

  @Test
  void savingAnAreaDrawsIt() {
    MapToolsUtils.init(api);

    MapToolsUtils.saveArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertEquals(1, claims().getMarkers().size());
  }

  @Test
  void creatingAnAreaDrawsItAndDoesNotWarnWhenTheWorldIsMapped() {
    MapToolsUtils.init(api);
    DialogPlayer alex = scenario.editor("Alex");

    MapToolsUtils.createArea(
        alex,
        "home",
        new AreaGeometry(WORLD, MapToolsUtils.rectangle(0, 0, 9, 9), MapArea.RECT, null, null),
        "gold",
        "Home",
        List.of(),
        null,
        null,
        null);

    assertEquals(1, claims().getMarkers().size());
    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void creatingAnAreaInAWorldThatIsNotMappedStillWarns() {
    MapToolsUtils.init(api);
    DialogPlayer alex = scenario.editor("Alex");

    MapToolsUtils.createArea(
        alex,
        "home",
        new AreaGeometry(
            MapToolsTestUtils.LIMBO, MapToolsUtils.rectangle(0, 0, 9, 9), MapArea.RECT, null, null),
        "gold",
        "Home",
        List.of(),
        null,
        null,
        null);

    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
    assertEquals(
        "squaremap does not render this world, so it will not show on the map.",
        MessageTestUtils.nextMessage(alex));
  }

  @Test
  void deletingAnAreaRemovesItFromTheMap() {
    MapToolsUtils.init(api);
    MapToolsUtils.saveArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.saveArea(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    MapToolsUtils.deleteArea("home");
    MapToolsUtils.deleteArea("spot");

    assertTrue(claims().getMarkers().isEmpty());
    assertTrue(markers().getMarkers().isEmpty());
  }

  @Test
  void renamingAnAreaMovesItsMarkerToTheNewId() {
    MapToolsUtils.init(api);
    MapArea home = MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9);
    MapToolsUtils.saveArea(home);

    MapToolsUtils.renameArea(home, "house");

    assertEquals(1, claims().getMarkers().size());
  }

  @Test
  void renamingAnAreaRemovesTheOldMarkerFromBothLayers() {
    MapToolsUtils.init(api);
    MapArea spot = MapToolsTestUtils.marker("spot", WORLD, 3, 3);
    MapToolsUtils.saveArea(spot);

    MapToolsUtils.renameArea(spot, "pin");

    assertEquals(1, markers().getMarkers().size());
  }
}
