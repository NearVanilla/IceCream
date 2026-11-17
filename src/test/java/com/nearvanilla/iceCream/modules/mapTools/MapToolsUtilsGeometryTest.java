package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MapToolsUtilsGeometryTest {
  private static final double DELTA = 1e-9;

  private static List<MapPoint> polygon(double... coordinates) {
    List<MapPoint> points = new ArrayList<>();
    for (int i = 0; i + 1 < coordinates.length; i += 2) {
      points.add(new MapPoint(coordinates[i], coordinates[i + 1]));
    }
    return points;
  }

  @Test
  void rectangleCoversTheBlocksOfBothCornersInclusive() {
    assertEquals(
        List.of(new MapPoint(2, 3), new MapPoint(6, 3), new MapPoint(6, 8), new MapPoint(2, 8)),
        MapToolsUtils.rectangle(2, 3, 5, 7));
  }

  @Test
  void rectangleOfTheSameBlockTwiceIsOneBlock() {
    assertEquals(
        List.of(new MapPoint(4, 4), new MapPoint(5, 4), new MapPoint(5, 5), new MapPoint(4, 5)),
        MapToolsUtils.rectangle(4, 4, 4, 4));
  }

  @Test
  void rectangleDoesNotCareWhichCornerComesFirst() {
    List<MapPoint> expected = MapToolsUtils.rectangle(2, 3, 5, 7);

    assertEquals(expected, MapToolsUtils.rectangle(5, 7, 2, 3));
    assertEquals(expected, MapToolsUtils.rectangle(5, 3, 2, 7));
    assertEquals(expected, MapToolsUtils.rectangle(2, 7, 5, 3));
  }

  @Test
  void rectangleWorksWithNegativeCoordinates() {
    assertEquals(
        List.of(new MapPoint(-5, -5), new MapPoint(0, -5), new MapPoint(0, 0), new MapPoint(-5, 0)),
        MapToolsUtils.rectangle(-1, -1, -5, -5));
  }

  @Test
  void rectangleAcrossTheOriginIncludesBlockZero() {
    assertEquals(
        List.of(new MapPoint(-2, -2), new MapPoint(3, -2), new MapPoint(3, 3), new MapPoint(-2, 3)),
        MapToolsUtils.rectangle(-2, -2, 2, 2));
  }

  @Test
  void rectangleTooLargeAllowsTheMostBlocksOnEachSide() {
    assertFalse(MapToolsUtils.rectangleTooLarge(0, 0, 2999, 0));
    assertFalse(MapToolsUtils.rectangleTooLarge(0, 0, 0, 2999));
    assertFalse(MapToolsUtils.rectangleTooLarge(0, 0, 2999, 2999));
  }

  @Test
  void rectangleTooLargeRejectsOneBlockMore() {
    assertTrue(MapToolsUtils.rectangleTooLarge(0, 0, 3000, 0));
    assertTrue(MapToolsUtils.rectangleTooLarge(0, 0, 0, 3000));
    assertTrue(MapToolsUtils.rectangleTooLarge(0, 0, 3000, 3000));
  }

  @Test
  void rectangleTooLargeDoesNotCareWhichCornerComesFirst() {
    assertFalse(MapToolsUtils.rectangleTooLarge(2999, 2999, 0, 0));
    assertTrue(MapToolsUtils.rectangleTooLarge(3000, 0, 0, 0));
    assertTrue(MapToolsUtils.rectangleTooLarge(0, 3000, 0, 0));
  }

  @Test
  void rectangleTooLargeCountsBlocksAcrossTheOrigin() {
    assertFalse(MapToolsUtils.rectangleTooLarge(-1500, -1500, 1499, 1499));
    assertTrue(MapToolsUtils.rectangleTooLarge(-1500, 0, 1500, 0));
    assertTrue(MapToolsUtils.rectangleTooLarge(0, -1500, 0, 1500));
  }

  @Test
  void rectangleTooLargeDoesNotOverflowOnTheLargestRange() {
    assertTrue(MapToolsUtils.rectangleTooLarge(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0));
    assertTrue(MapToolsUtils.rectangleTooLarge(0, Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
  }

  @Test
  void aSingleBlockIsNotTooLarge() {
    assertFalse(MapToolsUtils.rectangleTooLarge(7, 7, 7, 7));
  }

  @Test
  void rectangleBlocksGivesBackTheCornersTheRectangleWasMadeFrom() {
    assertArrayEquals(
        new int[] {2, 3, 5, 7}, MapToolsUtils.rectangleBlocks(MapToolsUtils.rectangle(5, 7, 2, 3)));
    assertArrayEquals(
        new int[] {-5, -9, -1, -2},
        MapToolsUtils.rectangleBlocks(MapToolsUtils.rectangle(-1, -9, -5, -2)));
    assertArrayEquals(
        new int[] {0, 0, 0, 0}, MapToolsUtils.rectangleBlocks(MapToolsUtils.rectangle(0, 0, 0, 0)));
  }

  @Test
  void rectangleBlocksOfAnEmptyListIsTheBlockAtTheOrigin() {
    assertArrayEquals(new int[] {0, 0, 0, 0}, MapToolsUtils.rectangleBlocks(List.of()));
  }

  @Test
  void blockCenterIsTheMiddleOfTheBlock() {
    assertEquals(new MapPoint(3.5, 4.5), MapToolsUtils.blockCenter(3, 4));
    assertEquals(new MapPoint(0.5, 0.5), MapToolsUtils.blockCenter(0, 0));
  }

  @Test
  void blockCenterOfNegativeBlocksStaysInsideTheBlock() {
    // Block -4 covers -4 to -3, so its middle is -3.5.
    assertEquals(new MapPoint(-3.5, -0.5), MapToolsUtils.blockCenter(-4, -1));
  }

  @ParameterizedTest
  @CsvSource({"0, 16", "1, 16", "10, 16", "11, 17", "20, 31", "80, 126", "81, 127", "82, 128"})
  void aCircleHasAboutOnePointPerFourBlocksOfCircumference(double radius, int points) {
    assertEquals(points, MapToolsUtils.circle(0, 0, radius).size());
  }

  @ParameterizedTest
  @ValueSource(doubles = {83, 100, 5000})
  void aCircleNeverHasMoreThan128Points(double radius) {
    assertEquals(128, MapToolsUtils.circle(0, 0, radius).size());
  }

  @Test
  void everyPointOfACircleIsOnItsRadius() {
    List<MapPoint> circle = MapToolsUtils.circle(100, -50, 25);

    for (MapPoint point : circle) {
      assertEquals(25, Math.hypot(point.x() - 100, point.z() + 50), DELTA);
    }
  }

  @Test
  void aCircleStartsToTheEastAndGoesTowardsPositiveZ() {
    List<MapPoint> circle = MapToolsUtils.circle(100, -50, 40);

    assertEquals(new MapPoint(140, -50), circle.getFirst());
    // 40 blocks have 63 points, so the second point is one step of 360 / 63 degrees further on.
    double step = 2 * Math.PI / circle.size();
    assertEquals(100 + 40 * Math.cos(step), circle.get(1).x(), DELTA);
    assertEquals(-50 + 40 * Math.sin(step), circle.get(1).z(), DELTA);
  }

  @Test
  void aCircleIsNotClosedByRepeatingItsFirstPoint() {
    List<MapPoint> circle = MapToolsUtils.circle(0, 0, 40);

    assertEquals(63, circle.size());
    assertEquals(63, new HashSet<>(circle).size());
  }

  @Test
  void aCircleOfRadiusZeroIsSixteenPointsAtTheCenter() {
    List<MapPoint> circle = MapToolsUtils.circle(7, 9, 0);

    assertEquals(16, circle.size());
    for (MapPoint point : circle) {
      assertEquals(7, point.x(), DELTA);
      assertEquals(9, point.z(), DELTA);
    }
  }

  @Test
  void circleShapeKeepsTheCenterAndRadiusAndBuildsItsOutline() {
    MapPoint center = new MapPoint(10.5, -20.5);

    AreaGeometry shape = MapToolsUtils.circleShape("minecraft:overworld", center, 12);

    assertEquals("minecraft:overworld", shape.world());
    assertEquals(MapArea.CIRCLE, shape.shape());
    assertEquals(center, shape.center());
    assertEquals(12.0, shape.radius());
    assertEquals(MapToolsUtils.circle(10.5, -20.5, 12), shape.points());
  }

  @Test
  void containsTheMiddleOfASquare() {
    assertTrue(MapToolsUtils.contains(polygon(0, 0, 10, 0, 10, 10, 0, 10), 5, 5));
  }

  @ParameterizedTest
  @CsvSource({"-1, 5", "11, 5", "5, -1", "5, 11", "-1, -1", "11, 11"})
  void doesNotContainSpotsOutsideASquare(double x, double z) {
    assertFalse(MapToolsUtils.contains(polygon(0, 0, 10, 0, 10, 10, 0, 10), x, z));
  }

  @ParameterizedTest
  @CsvSource({"0, 5", "5, 0", "0, 0"})
  void containsTheLowerEdgesOfASquare(double x, double z) {
    assertTrue(MapToolsUtils.contains(polygon(0, 0, 10, 0, 10, 10, 0, 10), x, z));
  }

  @ParameterizedTest
  @CsvSource({"10, 5", "5, 10", "10, 10"})
  void doesNotContainTheUpperEdgesOfASquare(double x, double z) {
    assertFalse(MapToolsUtils.contains(polygon(0, 0, 10, 0, 10, 10, 0, 10), x, z));
  }

  @Test
  void aSpotOnTheSharedEdgeOfTwoNeighboursBelongsToExactlyOne() {
    List<MapPoint> left = polygon(0, 0, 10, 0, 10, 10, 0, 10);
    List<MapPoint> right = polygon(10, 0, 20, 0, 20, 10, 10, 10);

    assertFalse(MapToolsUtils.contains(left, 10, 5));
    assertTrue(MapToolsUtils.contains(right, 10, 5));
  }

  @Test
  void containsWorksForNegativeCoordinates() {
    List<MapPoint> square = polygon(-10, -10, -5, -10, -5, -5, -10, -5);

    assertTrue(MapToolsUtils.contains(square, -7, -7));
    assertFalse(MapToolsUtils.contains(square, 7, 7));
    assertFalse(MapToolsUtils.contains(square, -7, -12));
  }

  @Test
  void containsFollowsTheShapeOfAConcavePolygon() {
    // A U shape open at the top: the notch in the middle is outside.
    List<MapPoint> u = polygon(0, 0, 30, 0, 30, 20, 20, 20, 20, 10, 10, 10, 10, 20, 0, 20);

    assertTrue(MapToolsUtils.contains(u, 5, 15));
    assertTrue(MapToolsUtils.contains(u, 15, 5));
    assertFalse(MapToolsUtils.contains(u, 15, 15));
  }

  @Test
  void containsFollowsATriangle() {
    List<MapPoint> triangle = polygon(0, 0, 10, 0, 0, 10);

    assertTrue(MapToolsUtils.contains(triangle, 2, 2));
    assertFalse(MapToolsUtils.contains(triangle, 8, 8));
  }

  @Test
  void aSelfIntersectingPolygonContainsOnlyTheSpotsCoveredAnOddNumberOfTimes() {
    // A bow tie: only the left and right triangles are inside, not the top and bottom.
    List<MapPoint> bowTie = polygon(0, 0, 10, 10, 10, 0, 0, 10);

    assertTrue(MapToolsUtils.contains(bowTie, 1, 5));
    assertTrue(MapToolsUtils.contains(bowTie, 9, 5));
    assertFalse(MapToolsUtils.contains(bowTie, 5, 1));
    assertFalse(MapToolsUtils.contains(bowTie, 5, 9));
  }

  @Test
  void aPolygonWithAHorizontalEdgeIsNotDividedByZero() {
    List<MapPoint> square = polygon(0, 0, 10, 0, 10, 10, 0, 10);

    assertTrue(MapToolsUtils.contains(square, 5, 5));
    assertFalse(MapToolsUtils.contains(square, 5, 0 - DELTA));
  }

  @Test
  void containsNothingWithoutAnArea() {
    assertFalse(MapToolsUtils.contains(List.of(), 0, 0));
    assertFalse(MapToolsUtils.contains(polygon(5, 5), 5, 5));
    assertFalse(MapToolsUtils.contains(polygon(0, 0, 10, 0), 5, 0));
    assertFalse(MapToolsUtils.contains(polygon(0, 0, 5, 0, 10, 0), 5, 0));
  }

  @Test
  void parsePointsBuildsAPolygonAroundTheBlockCenters() {
    AreaGeometry shape = MapToolsUtils.parsePoints("minecraft:overworld", "0 0; 10 0; 10 10");

    assertNotNull(shape);
    assertEquals("minecraft:overworld", shape.world());
    assertEquals(MapArea.POLYGON, shape.shape());
    assertEquals(
        List.of(new MapPoint(0.5, 0.5), new MapPoint(10.5, 0.5), new MapPoint(10.5, 10.5)),
        shape.points());
    assertNull(shape.center());
    assertNull(shape.radius());
  }

  @Test
  void parsePointsAcceptsNegativeCoordinates() {
    AreaGeometry shape = MapToolsUtils.parsePoints("w", "-5 -5; 5 -5; 0 5");

    assertNotNull(shape);
    assertEquals(
        List.of(new MapPoint(-4.5, -4.5), new MapPoint(5.5, -4.5), new MapPoint(0.5, 5.5)),
        shape.points());
  }

  @Test
  void parsePointsIgnoresExtraWhitespaceAndATrailingSemicolon() {
    AreaGeometry shape = MapToolsUtils.parsePoints("w", "  0   0 ;\t10 0;10\t10 ;");

    assertNotNull(shape);
    assertEquals(3, shape.points().size());
    assertEquals(new MapPoint(10.5, 10.5), shape.points().get(2));
  }

  @Test
  void parsePointsKeepsTheOrderOfThePoints() {
    AreaGeometry shape = MapToolsUtils.parsePoints("w", "0 0; 0 10; 10 10; 10 0");

    assertNotNull(shape);
    assertEquals(
        List.of(
            new MapPoint(0.5, 0.5),
            new MapPoint(0.5, 10.5),
            new MapPoint(10.5, 10.5),
            new MapPoint(10.5, 0.5)),
        shape.points());
  }

  @Test
  void parsePointsAcceptsAConcavePolygon() {
    assertNotNull(MapToolsUtils.parsePoints("w", "0 0; 30 0; 30 20; 20 20; 20 10; 10 10; 10 20"));
  }

  @Test
  void parsePointsAcceptsASelfIntersectingPolygonWhoseLobesDifferInSize() {
    // The diagonal from 0 0 to 10 10 is crossed by the edge from 10 0 to 0 5.
    AreaGeometry shape = MapToolsUtils.parsePoints("w", "0 0; 10 10; 10 0; 0 5");

    assertNotNull(shape);
    assertEquals(4, shape.points().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        ";",
        "0 0",
        "0 0; 10 0",
        "0 0; 10 0;",
        "a b; 10 0; 10 10",
        "0 0; 10 0; ten 10",
        "1.5 2; 10 0; 10 10",
        "1 2 3; 10 0; 10 10",
        "1; 10 0; 10 10",
        "0,0; 10,0; 10,10",
        "0 0;; 10 0; 10 10",
        "99999999999 0; 10 0; 10 10",
        "0 0; 10 0; 10 99999999999"
      })
  void parsePointsRejectsTextThatIsNotAListOfAtLeastThreePoints(String value) {
    assertNull(MapToolsUtils.parsePoints("w", value));
  }

  @Test
  void parsePointsRejectsNull() {
    assertNull(MapToolsUtils.parsePoints("w", null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0 0; 5 0; 10 0",
        "0 0; 0 5; 0 10",
        "0 0; 5 5; 10 10",
        "3 3; 3 3; 3 3",
        "0 0; 0 0; 10 0",
        "-4 -4; 0 0; 4 4"
      })
  void parsePointsRejectsPointsThatEncloseNoArea(String value) {
    assertNull(MapToolsUtils.parsePoints("w", value));
  }

  @Test
  void shapeTextOfAMarkerGivesItsBlock() {
    assertEquals("marker 5 -3", MapToolsUtils.shapeText(markerAt(5.5, -2.5)));
    assertEquals("marker -1 0", MapToolsUtils.shapeText(markerAt(-0.5, 0.5)));
  }

  @Test
  void shapeTextOfACircleGivesItsCenterBlockAndRoundedRadius() {
    assertEquals("circle 5 -3 10", MapToolsUtils.shapeText(circleAt(5.5, -2.5, 10)));
    assertEquals("circle 5 -3 11", MapToolsUtils.shapeText(circleAt(5.5, -2.5, 10.5)));
    assertEquals("circle 5 -3 10", MapToolsUtils.shapeText(circleAt(5.5, -2.5, 10.4)));
  }

  @Test
  void shapeTextOfARectangleGivesItsCornerBlocks() {
    MapArea rectangle = rectangle(-3, 4, 8, 20);

    assertEquals("rect -3 4 8 20", MapToolsUtils.shapeText(rectangle));
  }

  @Test
  void shapeTextOfAPolygonListsItsBlocks() {
    MapArea polygon = area(MapArea.POLYGON, polygon(0.5, 0.5, 10.5, 0.5, -3.5, 10.5), null, null);

    assertEquals("points 0 0; 10 0; -4 10", MapToolsUtils.shapeText(polygon));
  }

  @Test
  void shapeFieldsOfARectangleAreItsTwoCorners() {
    assertEquals(List.of("-3 4", "8 20"), MapToolsUtils.shapeFields(rectangle(-3, 4, 8, 20)));
  }

  @Test
  void shapeFieldsOfACircleAreItsCenterAndRadius() {
    assertEquals(List.of("5 -3", "10"), MapToolsUtils.shapeFields(circleAt(5.5, -2.5, 10)));
    assertEquals(List.of("5 -3", "11"), MapToolsUtils.shapeFields(circleAt(5.5, -2.5, 10.5)));
  }

  @Test
  void shapeFieldsOfAPolygonAMarkerOrAnUnknownShapeAreEmpty() {
    MapArea polygon = area(MapArea.POLYGON, polygon(0, 0, 10, 0, 10, 10), null, null);
    MapArea unknown = area("blob", polygon(0, 0, 10, 0, 10, 10), null, null);

    assertEquals(List.of(), MapToolsUtils.shapeFields(polygon));
    assertEquals(List.of(), MapToolsUtils.shapeFields(markerAt(5.5, 5.5)));
    assertEquals(List.of(), MapToolsUtils.shapeFields(unknown));
  }

  @Test
  void pointsTextWritesEachPointAsItsBlock() {
    MapArea polygon = area(MapArea.POLYGON, polygon(0.5, 0.5, 10.5, 0.5, -0.5, -0.5), null, null);

    assertEquals("0 0; 10 0; -1 -1", MapToolsUtils.pointsText(polygon));
  }

  @Test
  void pointsTextCanBeTypedBackIntoParsePoints() {
    AreaGeometry typed = MapToolsUtils.parsePoints("w", "0 0; 30 0; 30 20; -20 20; -20 -10");
    assertNotNull(typed);
    MapArea polygon = area(MapArea.POLYGON, typed.points(), null, null);

    AreaGeometry again = MapToolsUtils.parsePoints("w", MapToolsUtils.pointsText(polygon));

    assertNotNull(again);
    assertEquals(typed.points(), again.points());
  }

  private static MapArea rectangle(int x1, int z1, int x2, int z2) {
    return area(MapArea.RECT, MapToolsUtils.rectangle(x1, z1, x2, z2), null, null);
  }

  private static MapArea markerAt(double x, double z) {
    MapPoint center = new MapPoint(x, z);
    return area(MapArea.MARKER, List.of(center), center, null);
  }

  private static MapArea circleAt(double x, double z, double radius) {
    MapPoint center = new MapPoint(x, z);
    return area(MapArea.CIRCLE, MapToolsUtils.circle(x, z, radius), center, radius);
  }

  private static MapArea area(String shape, List<MapPoint> points, MapPoint center, Double radius) {
    return new MapArea(
        "id",
        "minecraft:overworld",
        points,
        "Label",
        null,
        List.of(),
        "gold",
        shape,
        center,
        radius,
        null,
        null,
        null);
  }
}
