package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.KeyedWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Finding what a player points at: the nearest edge, the distance to a shape, the corner. */
class EditSessionAimTest {
  private static final double DELTA = 1e-9;

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

  private EditSession rect(int x1, int z1, int x2, int z2) {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(x1, 64, z1));
    session.setCorner2(at(x2, 64, z2));
    return session;
  }

  private EditSession polygon(int... coordinates) {
    EditSession session = EditSession.create(Mode.POLYGON);
    for (int i = 0; i + 1 < coordinates.length; i += 2) {
      session.addPoint(at(coordinates[i], 64, coordinates[i + 1]));
    }
    return session;
  }

  // The eye looks along positive z (yaw 0, pitch 0) from the middle of block 0, 64, 0.
  private Location eye() {
    return new Location(overworld, 0.5, 64.5, 0.5, 0f, 0f);
  }

  private EditSession markerAt(int x, int y, int z) {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(at(x, y, z));
    return session;
  }

  @Test
  void aBlockInsideTheShapeIsZeroAway() {
    EditSession session = rect(0, 0, 9, 9);

    assertEquals(0, session.distanceToOutline(at(5, 64, 5)));
    assertEquals(0, session.distanceToOutline(at(0, 64, 0)));
    assertEquals(0, session.distanceToOutline(at(9, 64, 9)));
  }

  @ParameterizedTest
  @CsvSource({"10, 5, 0.5", "15, 5, 5.5", "-1, 5, 0.5", "-6, 5, 5.5", "5, 10, 0.5", "5, -6, 5.5"})
  void aBlockBesideARectangleIsMeasuredFromItsCenterToTheEdge(int x, int z, double distance) {
    assertEquals(distance, rect(0, 0, 9, 9).distanceToOutline(at(x, 64, z)), DELTA);
  }

  @Test
  void aBlockPastACornerIsMeasuredToTheCorner() {
    // The block center is 3.5 past the edge on x and 4.5 on z.
    assertEquals(Math.hypot(3.5, 4.5), rect(0, 0, 9, 9).distanceToOutline(at(13, 64, 14)), DELTA);
    assertEquals(Math.hypot(3.5, 4.5), rect(0, 0, 9, 9).distanceToOutline(at(-4, 64, -5)), DELTA);
  }

  @Test
  void theHeightOfTheBlockDoesNotMatter() {
    assertEquals(5.5, rect(0, 0, 9, 9).distanceToOutline(at(15, 200, 5)), DELTA);
    assertEquals(0, rect(0, 0, 9, 9).distanceToOutline(at(5, -30, 5)));
  }

  @Test
  void aBlockPastASlantedEdgeIsMeasuredStraightToIt() {
    // The edge from block center 10.5, 0.5 to 0.5, 10.5 lies on x + z = 11.
    EditSession triangle = polygon(0, 0, 10, 0, 0, 10);

    assertEquals(10 / Math.sqrt(2), triangle.distanceToOutline(at(10, 64, 10)), DELTA);
  }

  @Test
  void aBlockInsideAConcavePolygonsNotchIsOutsideIt() {
    EditSession u = polygon(0, 0, 30, 0, 30, 20, 20, 20, 20, 10, 10, 10, 10, 20, 0, 20);

    assertEquals(0, u.distanceToOutline(at(5, 64, 15)));
    assertEquals(5, u.distanceToOutline(at(15, 64, 15)), DELTA);
  }

  @Test
  void aShapeThatIsNotFinishedIsZeroAway() {
    assertEquals(0, EditSession.create(Mode.RECT).distanceToOutline(at(500, 64, 500)));
    assertEquals(0, polygon(0, 0, 10, 0).distanceToOutline(at(500, 64, 500)));
    assertEquals(0, EditSession.create(Mode.CIRCLE).distanceToOutline(at(500, 64, 500)));
  }

  @Test
  void aBlockAwayFromACircleIsMeasuredToItsOutline() {
    EditSession circle = EditSession.create(Mode.CIRCLE);
    circle.setCenter(at(0, 64, 0));
    circle.setRadius(10);

    // The outline has a point on the same row, 10 blocks east of the center.
    assertEquals(20, circle.distanceToOutline(at(30, 64, 0)), DELTA);
    assertEquals(0, circle.distanceToOutline(at(3, 64, 3)));
  }

  @Test
  void aBlockAwayFromAMarkerIsMeasuredToTheSpot() {
    // The outline of a marker is a single point, an edge of no length.
    assertEquals(5, markerAt(0, 64, 0).distanceToOutline(at(5, 64, 0)), DELTA);
    assertEquals(5, markerAt(0, 64, 0).distanceToOutline(at(3, 64, 4)), DELTA);
    assertEquals(0, markerAt(0, 64, 0).distanceToOutline(at(0, 64, 0)), DELTA);
  }

  @Test
  void aPolygonWithARepeatedPointStillHasADistance() {
    EditSession repeated = polygon(0, 0, 0, 0, 10, 0, 10, 10, 0, 10);

    assertEquals(6, repeated.distanceToOutline(at(16, 64, 5)), DELTA);
    assertEquals(Math.hypot(2, 2), repeated.distanceToOutline(at(-2, 64, -2)), DELTA);
  }

  @Test
  void theNearestEdgeStartsAtTheIndexOfItsFirstPoint() {
    EditSession triangle = polygon(0, 0, 10, 0, 10, 10);

    assertEquals(0, triangle.nearestEdge(at(5, 64, -3)));
    assertEquals(1, triangle.nearestEdge(at(14, 64, 5)));
  }

  @Test
  void theEdgeBackToTheFirstPointIsTheLastOne() {
    EditSession triangle = polygon(0, 0, 10, 0, 10, 10);

    // The closing edge runs from the last point back to the first.
    assertEquals(2, triangle.nearestEdge(at(2, 64, 8)));
  }

  @Test
  void aBlockEquallyNearTwoEdgesTakesTheFirst() {
    EditSession triangle = polygon(0, 0, 10, 0, 10, 10);

    // Both the first and the second edge are 4 blocks from the shared point at 10, 0.
    assertEquals(0, triangle.nearestEdge(at(14, 64, -4)));
  }

  @Test
  void anEdgeBetweenTwoRepeatedPointsCanStillBeTheNearest() {
    // The first edge has no length and ties with the second one at the shared point.
    EditSession repeated = polygon(0, 0, 0, 0, 10, 0, 10, 10);

    assertEquals(0, repeated.nearestEdge(at(-5, 64, 0)));
  }

  @Test
  void theNearestEdgeIsMeasuredToTheWholeEdgeNotJustItsPoints() {
    // The block is next to the middle of the long first edge, far from both of its points.
    EditSession longEdge = polygon(0, 0, 100, 0, 100, 4);

    assertEquals(0, longEdge.nearestEdge(at(50, 64, -2)));
  }

  @Test
  void aBlockInFrontOfTheEyeIsInSight() {
    EditSession session = markerAt(0, 64, 10);

    assertEquals(at(0, 64, 10), session.cornerInSight(eye(), 20));
  }

  @Test
  void aBlockBeyondTheReachIsOutOfSight() {
    EditSession session = markerAt(0, 64, 10);

    assertEquals(at(0, 64, 10), session.cornerInSight(eye(), 10));
    assertNull(session.cornerInSight(eye(), 9.99));
  }

  @Test
  void aBlockBehindTheEyeIsOutOfSight() {
    assertNull(markerAt(0, 64, -10).cornerInSight(eye(), 50));
  }

  @Test
  void aBlockWithinOneBlockOfTheLineOfSightIsInSight() {
    // The center of block 1 is exactly one block off the line, which still counts.
    assertEquals(at(1, 64, 10), markerAt(1, 64, 10).cornerInSight(eye(), 50));
    assertEquals(at(-1, 64, 10), markerAt(-1, 64, 10).cornerInSight(eye(), 50));
    assertEquals(at(0, 65, 10), markerAt(0, 65, 10).cornerInSight(eye(), 50));
  }

  @Test
  void aBlockMoreThanOneBlockOffTheLineOfSightIsOutOfSight() {
    assertNull(markerAt(2, 64, 10).cornerInSight(eye(), 50));
    assertNull(markerAt(-2, 64, 10).cornerInSight(eye(), 50));
    assertNull(markerAt(0, 66, 10).cornerInSight(eye(), 50));
  }

  @Test
  void theNearestBlockInSightWins() {
    EditSession near = polygon(0, 15, 0, 5, 0, 30);

    assertEquals(at(0, 64, 5), near.cornerInSight(eye(), 50));
  }

  @Test
  void theNearestBlockWinsWhateverTheOrderOfThePoints() {
    assertEquals(at(0, 64, 5), polygon(0, 5, 0, 15, 0, 30).cornerInSight(eye(), 50));
    assertEquals(at(0, 64, 5), polygon(0, 30, 0, 15, 0, 5).cornerInSight(eye(), 50));
  }

  @Test
  void twoBlocksAtTheSameDistanceGiveTheFirst() {
    assertEquals(at(0, 64, 8), polygon(0, 8, 1, 8, 5, 40).cornerInSight(eye(), 50));
    assertEquals(at(1, 64, 8), polygon(1, 8, 0, 8, 5, 40).cornerInSight(eye(), 50));
  }

  @Test
  void aBlockInAnotherWorldIsNeverInSight() {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(new Location(nether, 0, 64, 10));

    assertNull(session.cornerInSight(eye(), 50));
  }

  @Test
  void theEyeCanLookUpAndDown() {
    Location down = new Location(overworld, 0.5, 74.5, 0.5, 0f, 90f);
    Location up = new Location(overworld, 0.5, 54.5, 0.5, 0f, -90f);

    assertEquals(at(0, 64, 0), markerAt(0, 64, 0).cornerInSight(down, 20));
    assertEquals(at(0, 64, 0), markerAt(0, 64, 0).cornerInSight(up, 20));
    assertNull(
        markerAt(0, 64, 0).cornerInSight(new Location(overworld, 0.5, 74.5, 0.5, 0f, -90f), 20));
  }

  @Test
  void theEyeCanLookInAnyDirection() {
    // Yaw 90 looks west, towards negative x; yaw 180 looks north, towards negative z.
    Location west = new Location(overworld, 0.5, 64.5, 0.5, 90f, 0f);
    Location north = new Location(overworld, 0.5, 64.5, 0.5, 180f, 0f);

    assertEquals(at(-10, 64, 0), markerAt(-10, 64, 0).cornerInSight(west, 20));
    assertNull(markerAt(10, 64, 0).cornerInSight(west, 20));
    assertEquals(at(0, 64, -10), markerAt(0, 64, -10).cornerInSight(north, 20));
    assertNull(markerAt(0, 64, 10).cornerInSight(north, 20));
  }

  @Test
  void aSessionWithoutDefiningBlocksHasNothingInSight() {
    assertNull(EditSession.create(Mode.POLYGON).cornerInSight(eye(), 50));
    assertNull(EditSession.create(Mode.RECT).cornerInSight(eye(), 50));
  }

  @Test
  void aCornerIsAimedAtWhereItIsDrawnOnTheGround() {
    overworld.loadChunk(0, 0);
    overworld.getBlockAt(0, 70, 10).setType(Material.STONE);
    // The corner was saved at height 64, but its outline is drawn on the ground at height 70.
    EditSession session = markerAt(0, 64, 10);
    Location level = new Location(overworld, 0.5, 70.5, 0.5, 0f, 0f);

    assertEquals(at(0, 64, 10), session.cornerInSight(level, 50));
    assertNull(session.cornerInSight(eye(), 50));
  }

  @Test
  void aCornerOfAnEditedAreaCanBeAimedAtAtSeaLevelWhenItsChunkIsNotLoaded() {
    EditSession edited =
        EditSession.edit(MapToolsTestUtils.marker("m", MapToolsTestUtils.OVERWORLD, 0, 10));

    assertEquals(at(0, 63, 10), edited.cornerInSight(eye(), 50));
  }
}
