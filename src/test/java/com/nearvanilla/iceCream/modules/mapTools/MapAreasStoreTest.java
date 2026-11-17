package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The areas of the store in memory, and what an area is made of. */
class MapAreasStoreTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private static MapArea full() {
    return new MapArea(
        "full",
        WORLD,
        List.of(new MapPoint(1, 2), new MapPoint(3, 4), new MapPoint(5, 6)),
        "Full",
        "A description",
        List.of("Alex", "Sam"),
        "red",
        MapArea.POLYGON,
        new MapPoint(7, 8),
        9.0,
        0.5,
        "pin",
        true);
  }

  @BeforeEach
  void setUp() {
    IceCreamFixture.install(MockBukkit.mock(), "");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  @Test
  void anAreaWithoutOwnersGetsAnEmptyList() {
    MapArea area = full().withOwners(null);

    assertEquals(List.of(), area.owners());
  }

  @Test
  void anAreaKeepsTheOwnersItWasGiven() {
    assertEquals(List.of("Alex", "Sam"), full().owners());
  }

  @ParameterizedTest
  @CsvSource({
    "gold, gold",
    "GOLD, gold",
    "'  Dark_Red ', dark_red",
    "ff8800, #ff8800",
    "#FF8800, #ff8800",
    "AbCdEf, #abcdef"
  })
  void anAreaKeepsItsColorInTheStoredForm(String given, String stored) {
    assertEquals(stored, full().withColor(given).color());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"banana", "#12345", "ff88000"})
  void anAreaKeepsAColorThatCannotBeParsedAsItWas(String given) {
    assertEquals(given, full().withColor(given).color());
  }

  @Test
  void aShapeIsOnlyOneKind() {
    MapArea circle = full().withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(0, 0), 5));
    MapArea marker = MapToolsTestUtils.marker("m", WORLD, 0, 0);
    MapArea rect = MapToolsTestUtils.rect("r", WORLD, 0, 0, 1, 1);

    assertTrue(circle.isCircle());
    assertFalse(circle.isMarker());
    assertTrue(marker.isMarker());
    assertFalse(marker.isCircle());
    assertFalse(rect.isCircle());
    assertFalse(rect.isMarker());
  }

  @Test
  void anAreaWithoutAShapeIsNeitherACircleNorAMarker() {
    MapArea unknown =
        new MapArea(
            "a", WORLD, List.of(), "A", null, null, "gold", null, null, null, null, null, null);

    assertFalse(unknown.isCircle());
    assertFalse(unknown.isMarker());
  }

  @Test
  void anAreaIsHiddenOnlyWhenTheFlagIsTrue() {
    assertTrue(full().isHidden());
    assertFalse(full().withHidden(false).isHidden());
    assertFalse(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1).isHidden());
  }

  @Test
  void hidingAnAreaSetsTheFlagAndShowingItClearsTheFlag() {
    MapArea area = MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1);

    assertEquals(Boolean.TRUE, area.withHidden(true).hidden());
    assertNull(area.withHidden(true).withHidden(false).hidden());
  }

  @Test
  void withIdChangesOnlyTheId() {
    MapArea area = full().withId("renamed");

    assertEquals("renamed", area.id());
    assertEquals(
        full(),
        new MapArea(
            "full",
            area.world(),
            area.points(),
            area.label(),
            area.description(),
            area.owners(),
            area.color(),
            area.shape(),
            area.center(),
            area.radius(),
            area.opacity(),
            area.icon(),
            area.hidden()));
  }

  @Test
  void withLabelChangesOnlyTheLabel() {
    MapArea area = full().withLabel("New label");

    assertEquals("New label", area.label());
    assertEquals(full(), area.withLabel("Full"));
  }

  @Test
  void withDescriptionChangesOnlyTheDescription() {
    MapArea area = full().withDescription("Other");

    assertEquals("Other", area.description());
    assertNull(full().withDescription(null).description());
    assertEquals(full(), area.withDescription("A description"));
  }

  @Test
  void withOwnersChangesOnlyTheOwners() {
    MapArea area = full().withOwners(List.of("Kim"));

    assertEquals(List.of("Kim"), area.owners());
    assertEquals(full(), area.withOwners(List.of("Alex", "Sam")));
  }

  @Test
  void withColorChangesOnlyTheColor() {
    MapArea area = full().withColor("blue");

    assertEquals("blue", area.color());
    assertEquals(full(), area.withColor("red"));
  }

  @Test
  void withOpacityChangesOnlyTheOpacity() {
    MapArea area = full().withOpacity(0.9);

    assertEquals(0.9, area.opacity());
    assertNull(full().withOpacity(null).opacity());
    assertEquals(full(), area.withOpacity(0.5));
  }

  @Test
  void withIconChangesOnlyTheIcon() {
    MapArea area = full().withIcon("home");

    assertEquals("home", area.icon());
    assertEquals(full(), area.withIcon("pin"));
  }

  @Test
  void withGeometryReplacesTheShapeAndKeepsTheDetails() {
    AreaGeometry geometry =
        new AreaGeometry(
            "minecraft:the_nether",
            List.of(new MapPoint(0, 0), new MapPoint(4, 0), new MapPoint(4, 4)),
            MapArea.RECT,
            null,
            null);

    MapArea area = full().withGeometry(geometry);

    assertEquals("minecraft:the_nether", area.world());
    assertEquals(geometry.points(), area.points());
    assertEquals(MapArea.RECT, area.shape());
    assertNull(area.center());
    assertNull(area.radius());
    assertEquals("full", area.id());
    assertEquals("Full", area.label());
    assertEquals("A description", area.description());
    assertEquals(List.of("Alex", "Sam"), area.owners());
    assertEquals("red", area.color());
    assertEquals(0.5, area.opacity());
    assertEquals("pin", area.icon());
    assertTrue(area.isHidden());
  }

  @Test
  void withGeometryCanTurnAnAreaIntoACircle() {
    MapPoint center = new MapPoint(10.5, 10.5);

    MapArea area = full().withGeometry(MapToolsUtils.circleShape(WORLD, center, 7));

    assertTrue(area.isCircle());
    assertEquals(center, area.center());
    assertEquals(7.0, area.radius());
    assertEquals(MapToolsUtils.circle(10.5, 10.5, 7), area.points());
  }

  @Test
  void parseOwnersSplitsOnCommasAndTrimsEachName() {
    assertEquals(List.of("Dynant", "Alex", "Sam"), MapAreasStore.parseOwners("Dynant, Alex ,Sam"));
  }

  @Test
  void parseOwnersKeepsSpacesInsideANameAndTheOrder() {
    assertEquals(List.of("Mary Jane", "Bob"), MapAreasStore.parseOwners("Mary Jane, Bob"));
  }

  @Test
  void parseOwnersDropsEmptyNames() {
    assertEquals(List.of("a", "b"), MapAreasStore.parseOwners(",a,, ,\t,b,"));
  }

  @Test
  void parseOwnersOfASingleNameIsThatName() {
    assertEquals(List.of("Alex"), MapAreasStore.parseOwners("  Alex  "));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", ",", " , , ", "\n"})
  void parseOwnersOfNothingIsEmpty(String text) {
    assertEquals(List.of(), MapAreasStore.parseOwners(text));
  }

  @Test
  void aNewStoreHasNoAreas() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());

    assertTrue(store.getAreas().isEmpty());
    assertNull(store.getArea("anything"));
  }

  @Test
  void anAddedAreaCanBeFoundById() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    MapArea area = MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9);

    store.putArea(area);

    assertSame(area, store.getArea("home"));
    assertNull(store.getArea("Home"));
  }

  @Test
  void puttingAnAreaWithTheSameIdReplacesItAndKeepsItsPlace() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));
    MapArea changed = MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1).withLabel("Changed");

    store.putArea(changed);

    assertEquals(List.of("a", "b"), store.getAreas().stream().map(MapArea::id).toList());
    assertSame(changed, store.getArea("a"));
  }

  @Test
  void areasAreListedInTheOrderTheyWereAdded() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());

    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    assertEquals(List.of("c", "a", "b"), store.getAreas().stream().map(MapArea::id).toList());
  }

  @Test
  void theListOfAreasCannotBeChangedFromOutside() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    MapArea area = MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1);
    store.putArea(area);

    assertThrows(UnsupportedOperationException.class, () -> store.getAreas().clear());
    assertThrows(UnsupportedOperationException.class, () -> store.getAreas().add(area));
    assertEquals(1, store.getAreas().size());
  }

  @Test
  void theListOfAreasFollowsLaterChanges() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    var view = store.getAreas();

    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertEquals(1, view.size());
  }

  @Test
  void renamingMovesTheAreaToTheEndUnderItsNewId() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    MapArea first = MapToolsTestUtils.rect("first", WORLD, 0, 0, 1, 1);
    store.putArea(first);
    store.putArea(MapToolsTestUtils.rect("second", WORLD, 0, 0, 1, 1));

    store.renameArea("first", first.withId("renamed"));

    assertNull(store.getArea("first"));
    assertEquals(first.withId("renamed"), store.getArea("renamed"));
    assertEquals(List.of("second", "renamed"), store.getAreas().stream().map(MapArea::id).toList());
  }

  @Test
  void renamingAnAreaOverAnotherIdReplacesThatArea() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    MapArea first = MapToolsTestUtils.rect("first", WORLD, 0, 0, 1, 1);
    store.putArea(first);
    store.putArea(MapToolsTestUtils.rect("second", WORLD, 0, 0, 1, 1));

    store.renameArea("first", first.withId("second"));

    assertEquals(List.of("second"), store.getAreas().stream().map(MapArea::id).toList());
    assertEquals("Label first", store.getArea("second").label());
  }

  @Test
  void renamingAnAreaThatIsNotThereStillAddsTheNewOne() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());

    store.renameArea("ghost", MapToolsTestUtils.rect("real", WORLD, 0, 0, 1, 1));

    assertEquals(List.of("real"), store.getAreas().stream().map(MapArea::id).toList());
  }

  @Test
  void removingAnAreaReturnsItAndForgetsIt() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    MapArea area = MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1);
    store.putArea(area);

    assertSame(area, store.removeArea("a"));

    assertNull(store.getArea("a"));
    assertTrue(store.getAreas().isEmpty());
  }

  @Test
  void removingAnAreaThatIsNotThereReturnsNull() {
    MapAreasStore store = new MapAreasStore(MapToolsTestUtils.storeFile());
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertNull(store.removeArea("b"));

    assertEquals(1, store.getAreas().size());
  }
}
