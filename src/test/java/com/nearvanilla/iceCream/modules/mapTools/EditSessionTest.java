package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.KeyedWorld;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The state of an edit session: its mode, the blocks that define its shape, and the shape. */
class EditSessionTest {
  private KeyedWorld overworld;
  private KeyedWorld nether;

  @BeforeEach
  void setUp() {
    var server = MockBukkit.mock();
    overworld = MapToolsTestUtils.addWorld(server, MapToolsTestUtils.OVERWORLD);
    nether = MapToolsTestUtils.addWorld(server, MapToolsTestUtils.NETHER);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private Location at(int x, int y, int z) {
    return new Location(overworld, x, y, z);
  }

  @Test
  void aViewSessionOnlyViews() {
    EditSession session = EditSession.view();

    assertTrue(session.isViewing());
    assertFalse(session.isNew());
    assertFalse(session.isEditingArea());
    assertNull(session.getMode());
    assertNull(session.getAreaId());
    assertNull(session.getWorld());
    assertFalse(session.hasChanges());
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void aCreateSessionDrawsANewShapeOfItsMode(Mode mode) {
    EditSession session = EditSession.create(mode);

    assertTrue(session.isNew());
    assertFalse(session.isViewing());
    assertFalse(session.isEditingArea());
    assertEquals(mode, session.getMode());
    assertNull(session.getAreaId());
    assertNull(session.getWorld());
    assertFalse(session.hasChanges());
    assertNull(session.getPicked());
    assertTrue(session.definingBlocks().isEmpty());
    assertNull(session.toGeometry());
    assertTrue(session.previewOutline().isEmpty());
  }

  @Test
  void theModesHaveALabelForPlayers() {
    assertEquals("Rectangle", Mode.RECT.label());
    assertEquals("Polygon", Mode.POLYGON.label());
    assertEquals("Circle", Mode.CIRCLE.label());
    assertEquals("Marker", Mode.MARKER.label());
  }

  @Test
  void aModeIsFoundFromTheShapeOfAnArea() {
    assertEquals(Mode.RECT, Mode.of(MapToolsTestUtils.rect("a", "w", 0, 0, 1, 1)));
    assertEquals(Mode.POLYGON, Mode.of(MapToolsTestUtils.polygon("a", "w", 0, 0, 1, 0, 1, 1)));
    assertEquals(Mode.CIRCLE, Mode.of(MapToolsTestUtils.circle("a", "w", 0, 0, 5)));
    assertEquals(Mode.MARKER, Mode.of(MapToolsTestUtils.marker("a", "w", 0, 0)));
  }

  @Test
  void aModeCannotBeFoundFromAnUnknownShape() {
    MapArea unknown =
        MapToolsTestUtils.rect("a", "w", 0, 0, 1, 1)
            .withGeometry(new AreaGeometry("w", List.of(), "blob", null, null));

    assertThrows(IllegalArgumentException.class, () -> Mode.of(unknown));
  }

  @Test
  void theSaveHintIsShownOnlyOnce() {
    EditSession session = EditSession.create(Mode.RECT);

    assertTrue(session.markHinted());
    assertFalse(session.markHinted());
    assertFalse(session.markHinted());
  }

  @Test
  void changesAndThePickedCornerCanBeSetAndCleared() {
    EditSession session = EditSession.create(Mode.POLYGON);

    session.setChanged(true);
    session.setPicked(2);
    assertTrue(session.hasChanges());
    assertEquals(2, session.getPicked());

    session.setChanged(false);
    session.setPicked(null);
    assertFalse(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void aCornerIsStoredAsTheBlockItIsIn() {
    EditSession session = EditSession.create(Mode.RECT);

    session.setCorner1(new Location(overworld, 1.9, 64.9, -0.1));
    session.setCorner2(new Location(overworld, -3.5, 70.2, 8.99));

    List<Location> corners = session.definingBlocks();
    assertEquals(new Location(overworld, 1, 64, -1), corners.get(0));
    assertEquals(new Location(overworld, -4, 70, 8), corners.get(2));
  }

  @Test
  void settingTheFirstCornerRemembersTheWorld() {
    EditSession session = EditSession.create(Mode.RECT);

    session.setCorner1(at(0, 64, 0));

    assertEquals(MapToolsTestUtils.OVERWORLD, session.getWorld());
  }

  @Test
  void aRectangleNeedsBothCorners() {
    EditSession session = EditSession.create(Mode.RECT);
    assertNull(session.toGeometry());

    session.setCorner1(at(2, 64, 3));
    assertNull(session.toGeometry());

    session.setCorner2(at(5, 64, 7));
    AreaGeometry geometry = session.toGeometry();
    assertNotNull(geometry);
    assertEquals(MapArea.RECT, geometry.shape());
    assertEquals(MapToolsTestUtils.OVERWORLD, geometry.world());
    assertEquals(MapToolsUtils.rectangle(2, 3, 5, 7), geometry.points());
    assertNull(geometry.center());
    assertNull(geometry.radius());
  }

  @Test
  void aRectangleWithOnlyTheSecondCornerHasNoShape() {
    EditSession session = EditSession.create(Mode.RECT);

    session.setCorner2(at(5, 64, 7));

    assertNull(session.toGeometry());
  }

  @Test
  void aRectangleCoversBothCornerBlocksWhicheverWayRound() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(5, 64, 7));
    session.setCorner2(at(-2, 64, -3));

    assertEquals(MapToolsUtils.rectangle(-2, -3, 5, 7), session.toGeometry().points());
  }

  @Test
  void aRectangleOfOneBlockIsOneBlock() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(4, 64, 4));
    session.setCorner2(at(4, 70, 4));

    assertEquals(MapToolsUtils.rectangle(4, 4, 4, 4), session.toGeometry().points());
  }

  @Test
  void theCornersOfARectangleAreItsFourBlocksAtTheLowerHeight() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(2, 80, 3));
    session.setCorner2(at(5, 64, 7));

    assertEquals(
        List.of(at(2, 80, 3), at(5, 64, 3), at(5, 64, 7), at(2, 64, 7)), session.definingBlocks());
  }

  @Test
  void aRectangleWithOneCornerHasOnlyThatCornerAsDefiningBlock() {
    EditSession first = EditSession.create(Mode.RECT);
    first.setCorner1(at(2, 64, 3));
    EditSession second = EditSession.create(Mode.RECT);
    second.setCorner2(at(5, 64, 7));

    assertEquals(List.of(at(2, 64, 3)), first.definingBlocks());
    assertEquals(List.of(at(5, 64, 7)), second.definingBlocks());
  }

  @Test
  void aRectangleCornerCanBeMovedWhileTheOppositeCornerStays() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(10, 64, 10));

    // Corner 0 is the first block; the opposite corner is the second block.
    session.moveRectCorner(0, at(-5, 64, -5));

    assertEquals(MapToolsUtils.rectangle(-5, -5, 10, 10), session.toGeometry().points());
  }

  @Test
  void movingTheOtherCornersKeepsTheirOppositeCorners() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(10, 64, 10));

    // Corner 1 is (10, 0) and sits opposite (0, 10); moving it keeps (0, 10).
    session.moveRectCorner(1, at(20, 64, -4));
    assertEquals(MapToolsUtils.rectangle(0, 10, 20, -4), session.toGeometry().points());
  }

  @Test
  void movingCornerTwoOrThreeKeepsTheCornerOppositeIt() {
    EditSession first = EditSession.create(Mode.RECT);
    first.setCorner1(at(0, 64, 0));
    first.setCorner2(at(10, 64, 10));
    EditSession second = EditSession.create(Mode.RECT);
    second.setCorner1(at(0, 64, 0));
    second.setCorner2(at(10, 64, 10));

    first.moveRectCorner(2, at(30, 64, 30));
    second.moveRectCorner(3, at(-7, 64, 30));

    // Corner 2 sits opposite the first block, corner 3 at (0, 10) opposite (10, 0).
    assertEquals(MapToolsUtils.rectangle(0, 0, 30, 30), first.toGeometry().points());
    assertEquals(MapToolsUtils.rectangle(10, 0, -7, 30), second.toGeometry().points());
  }

  @Test
  void aPolygonNeedsThreePoints() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));
    assertNull(session.toGeometry());

    session.addPoint(at(10, 64, 10));
    AreaGeometry geometry = session.toGeometry();

    assertNotNull(geometry);
    assertEquals(MapArea.POLYGON, geometry.shape());
    assertEquals(MapToolsTestUtils.OVERWORLD, geometry.world());
    assertEquals(
        List.of(new MapPoint(0.5, 0.5), new MapPoint(10.5, 0.5), new MapPoint(10.5, 10.5)),
        geometry.points());
    assertNull(geometry.center());
  }

  @Test
  void aPolygonUsesTheCentersOfNegativeBlocksToo() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(-1, 64, -1));
    session.addPoint(at(-10, 64, -1));
    session.addPoint(at(-10, 64, -10));

    assertEquals(
        List.of(new MapPoint(-0.5, -0.5), new MapPoint(-9.5, -0.5), new MapPoint(-9.5, -9.5)),
        session.toGeometry().points());
  }

  @Test
  void aPolygonCanTakeAPointInTheMiddleOfItsList() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));
    session.addPoint(at(10, 64, 10));

    session.insertPoint(1, at(5, 64, -3));

    assertEquals(
        List.of(at(0, 64, 0), at(5, 64, -3), at(10, 64, 0), at(10, 64, 10)),
        session.definingBlocks());
    assertEquals(4, session.getPointCount());
  }

  @Test
  void aPolygonPointCanBeMovedAndRemoved() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));
    session.addPoint(at(10, 64, 10));

    session.setPoint(1, at(20, 65, 0));
    assertEquals(List.of(at(0, 64, 0), at(20, 65, 0), at(10, 64, 10)), session.definingBlocks());

    session.removePoint(0);
    assertEquals(List.of(at(20, 65, 0), at(10, 64, 10)), session.definingBlocks());
    assertEquals(2, session.getPointCount());
  }

  @Test
  void theDefiningBlocksOfAPolygonAreACopy() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));

    assertThrows(UnsupportedOperationException.class, () -> session.definingBlocks().clear());
    assertEquals(1, session.getPointCount());
  }

  @Test
  void aCircleNeedsACenterAndARadius() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    assertNull(session.toGeometry());

    session.setCenter(at(10, 64, -20));
    assertNull(session.toGeometry());
    assertNull(session.getRadius());

    session.setRadius(8);
    AreaGeometry geometry = session.toGeometry();

    assertNotNull(geometry);
    assertEquals(MapArea.CIRCLE, geometry.shape());
    assertEquals(MapToolsTestUtils.OVERWORLD, geometry.world());
    assertEquals(new MapPoint(10.5, -19.5), geometry.center());
    assertEquals(8.0, geometry.radius());
    assertEquals(MapToolsUtils.circle(10.5, -19.5, 8), geometry.points());
    assertEquals(8, session.getRadius());
    assertEquals(at(10, 64, -20), session.getCenter());
  }

  @Test
  void aCircleWithARadiusButNoCenterHasNoShape() {
    EditSession session = EditSession.create(Mode.CIRCLE);

    session.setRadius(8);

    assertNull(session.toGeometry());
  }

  @Test
  void aCircleHasItsCenterAsTheDefiningBlock() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    assertEquals(List.of(), session.definingBlocks());

    session.setCenter(at(1, 64, 2));

    assertEquals(List.of(at(1, 64, 2)), session.definingBlocks());
  }

  @Test
  void aMarkerNeedsOnlyACenter() {
    EditSession session = EditSession.create(Mode.MARKER);
    assertNull(session.toGeometry());

    session.setCenter(at(-3, 70, 8));
    AreaGeometry geometry = session.toGeometry();

    assertNotNull(geometry);
    assertEquals(MapArea.MARKER, geometry.shape());
    assertEquals(MapToolsTestUtils.OVERWORLD, geometry.world());
    assertEquals(new MapPoint(-2.5, 8.5), geometry.center());
    assertEquals(List.of(new MapPoint(-2.5, 8.5)), geometry.points());
    assertNull(geometry.radius());
    assertEquals(List.of(at(-3, 70, 8)), session.definingBlocks());
  }

  @Test
  void aPolygonWithTwoPointsPreviewsAsALine() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    assertEquals(List.of(), session.previewOutline());

    session.addPoint(at(10, 64, 4));

    assertEquals(
        List.of(new MapPoint(0.5, 0.5), new MapPoint(10.5, 4.5)), session.previewOutline());
  }

  @Test
  void aFinishedShapePreviewsAsItsOutline() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(3, 64, 3));

    assertEquals(MapToolsUtils.rectangle(0, 0, 3, 3), session.previewOutline());
  }

  @Test
  void aRectangleWithOneCornerHasNothingToPreview() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));

    assertEquals(List.of(), session.previewOutline());
  }

  @Test
  void aCircleWithoutARadiusHasNothingToPreview() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    session.setCenter(at(0, 64, 0));

    assertEquals(List.of(), session.previewOutline());
  }

  @Test
  void aMarkerPreviewsAsItsSpot() {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(at(0, 64, 0));

    assertEquals(List.of(new MapPoint(0.5, 0.5)), session.previewOutline());
  }

  @Test
  void indexAtFindsTheDefiningBlockAtTheSameXAndZWhateverTheHeight() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));
    session.addPoint(at(10, 64, 10));

    assertEquals(0, session.indexAt(at(0, 10, 0)));
    assertEquals(1, session.indexAt(at(10, 200, 0)));
    assertEquals(2, session.indexAt(new Location(overworld, 10.7, 64, 10.2)));
  }

  @Test
  void indexAtIsNullAwayFromTheDefiningBlocks() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));

    assertNull(session.indexAt(at(1, 64, 0)));
    assertNull(session.indexAt(at(0, 64, 1)));
    assertNull(session.indexAt(at(-1, 64, 0)));
  }

  @Test
  void indexAtIsNullWhenThereAreNoDefiningBlocks() {
    assertNull(EditSession.create(Mode.CIRCLE).indexAt(at(0, 64, 0)));
    assertNull(EditSession.create(Mode.POLYGON).indexAt(at(0, 64, 0)));
  }

  @Test
  void indexAtGivesTheFirstOfTwoBlocksOnTheSameSpot() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(5, 64, 5));
    session.addPoint(at(9, 64, 9));
    session.addPoint(at(5, 70, 5));

    assertEquals(0, session.indexAt(at(5, 64, 5)));
  }

  @Test
  void indexAtOfARectangleCountsItsFourCorners() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(10, 64, 20));

    assertEquals(0, session.indexAt(at(0, 64, 0)));
    assertEquals(1, session.indexAt(at(10, 64, 0)));
    assertEquals(2, session.indexAt(at(10, 64, 20)));
    assertEquals(3, session.indexAt(at(0, 64, 20)));
    assertNull(session.indexAt(at(5, 64, 10)));
  }

  @Test
  void aPointInAnotherWorldStartsThePolygonOverThere() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 64, 0));
    session.addPoint(at(10, 64, 0));
    session.setPicked(1);

    session.addPoint(new Location(nether, 3, 64, 3));

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(List.of(new Location(nether, 3, 64, 3)), session.definingBlocks());
    assertNull(session.getPicked());
  }

  @Test
  void aCornerInAnotherWorldStartsTheRectangleOver() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(10, 64, 10));

    session.setCorner1(new Location(nether, 1, 64, 1));

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(List.of(new Location(nether, 1, 64, 1)), session.definingBlocks());
    assertNull(session.toGeometry());
  }

  @Test
  void theSecondCornerInAnotherWorldStartsTheRectangleOverWithThatCorner() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));

    session.setCorner2(new Location(nether, 1, 64, 1));

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(List.of(new Location(nether, 1, 64, 1)), session.definingBlocks());
  }

  @Test
  void aCenterInAnotherWorldMovesTheCircleThere() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    session.setCenter(at(0, 64, 0));
    session.setRadius(5);

    session.setCenter(new Location(nether, 8, 64, 8));

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(new Location(nether, 8, 64, 8), session.getCenter());
    assertEquals(MapToolsTestUtils.NETHER, session.toGeometry().world());
    assertEquals(new MapPoint(8.5, 8.5), session.toGeometry().center());
  }

  @Test
  void aBlockInTheSameWorldKeepsTheRestOfTheShape() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(0, 64, 0));
    session.setCorner2(at(10, 64, 10));
    session.setPicked(2);

    session.setCorner1(at(3, 64, 3));

    assertEquals(MapToolsUtils.rectangle(3, 3, 10, 10), session.toGeometry().points());
    assertEquals(2, session.getPicked());
  }
}
