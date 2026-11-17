package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShapeDistanceTest {
  private static final double DELTA = 1e-9;

  private static List<MapPoint> square(double x, double z, double size) {
    return polygon(x, z, x + size, z, x + size, z + size, x, z + size);
  }

  private static List<MapPoint> polygon(double... coordinates) {
    List<MapPoint> points = new ArrayList<>();
    for (int i = 0; i + 1 < coordinates.length; i += 2) {
      points.add(new MapPoint(coordinates[i], coordinates[i + 1]));
    }
    return points;
  }

  @Test
  void boxesWithinIsFalseWhenEitherPolygonIsEmpty() {
    assertFalse(ShapeDistance.boxesWithin(List.of(), square(0, 0, 10), 1000));
    assertFalse(ShapeDistance.boxesWithin(square(0, 0, 10), List.of(), 1000));
    assertFalse(ShapeDistance.boxesWithin(List.of(), List.of(), 1000));
  }

  @Test
  void boxesThatOverlapAreWithinWithoutAnyRange() {
    assertTrue(ShapeDistance.boxesWithin(square(0, 0, 10), square(5, 5, 10), 0));
  }

  @Test
  void aBoxInsideAnotherIsWithin() {
    assertTrue(ShapeDistance.boxesWithin(square(0, 0, 100), square(40, 40, 5), 0));
    assertTrue(ShapeDistance.boxesWithin(square(40, 40, 5), square(0, 0, 100), 0));
  }

  @Test
  void boxesThatTouchAreWithinWithoutAnyRange() {
    assertTrue(ShapeDistance.boxesWithin(square(0, 0, 10), square(10, 0, 10), 0));
  }

  // The second square sits 5 blocks away from the first on one side. Each side is a different
  // comparison inside boxesWithin.
  @ParameterizedTest
  @CsvSource({"15, 0", "-15, 0", "0, 15", "0, -15"})
  void aGapEqualToTheRangeIsWithinOnEverySide(double x, double z) {
    assertTrue(ShapeDistance.boxesWithin(square(0, 0, 10), square(x, z, 10), 5));
    assertTrue(ShapeDistance.boxesWithin(square(x, z, 10), square(0, 0, 10), 5));
  }

  @ParameterizedTest
  @CsvSource({"15, 0", "-15, 0", "0, 15", "0, -15"})
  void aGapLargerThanTheRangeIsNotWithinOnEverySide(double x, double z) {
    assertFalse(ShapeDistance.boxesWithin(square(0, 0, 10), square(x, z, 10), 4.99));
    assertFalse(ShapeDistance.boxesWithin(square(x, z, 10), square(0, 0, 10), 4.99));
  }

  @Test
  void boxesAreComparedOnEachAxisSeparately() {
    // The corners are 3 and 4 blocks apart on the two axes, 5 apart in a straight line, but the
    // check is only on the boxes, so a range of 4 is enough.
    assertTrue(ShapeDistance.boxesWithin(square(0, 0, 10), square(13, 14, 10), 4));
    assertFalse(ShapeDistance.boxesWithin(square(0, 0, 10), square(13, 14, 10), 3.9));
  }

  @Test
  void boxesWithinWorkWithNegativeCoordinates() {
    assertTrue(ShapeDistance.boxesWithin(square(-30, -30, 10), square(-15, -30, 10), 5));
    assertFalse(ShapeDistance.boxesWithin(square(-30, -30, 10), square(-14, -30, 10), 5));
  }

  @Test
  void boxesOfASinglePointAreWithinWhenThePointIsInRange() {
    List<MapPoint> point = polygon(5, 5);

    assertTrue(ShapeDistance.boxesWithin(point, square(5, 5, 10), 0));
    assertTrue(ShapeDistance.boxesWithin(point, square(10, 5, 10), 5));
    assertFalse(ShapeDistance.boxesWithin(point, square(11, 5, 10), 5));
  }

  @Test
  void aPolygonWithoutPointsIsInfinitelyFarAway() {
    assertEquals(Double.MAX_VALUE, ShapeDistance.between(List.of(), square(0, 0, 10)));
    assertEquals(Double.MAX_VALUE, ShapeDistance.between(square(0, 0, 10), List.of()));
    assertEquals(Double.MAX_VALUE, ShapeDistance.between(List.of(), List.of()));
  }

  @Test
  void squaresSideBySideAreTheGapApart() {
    assertEquals(5, ShapeDistance.between(square(0, 0, 10), square(15, 0, 10)), DELTA);
  }

  @Test
  void squaresAboveEachOtherAreTheGapApart() {
    assertEquals(7, ShapeDistance.between(square(0, 0, 10), square(0, 17, 10)), DELTA);
  }

  @Test
  void squaresOffsetOnBothAxesAreMeasuredCornerToCorner() {
    assertEquals(5, ShapeDistance.between(square(0, 0, 10), square(13, 14, 10)), DELTA);
  }

  @Test
  void squaresInTheNegativeQuadrantAreMeasuredTheSameWay() {
    assertEquals(5, ShapeDistance.between(square(-25, -10, 10), square(-10, -10, 10)), DELTA);
    assertEquals(5, ShapeDistance.between(square(-13, -14, 10), square(0, 0, 10)), DELTA);
  }

  @Test
  void theDistanceIsTheSameInBothDirections() {
    List<MapPoint> triangle = polygon(40, 0, 60, 0, 50, 15);
    List<MapPoint> square = square(0, 0, 10);

    assertEquals(
        ShapeDistance.between(square, triangle), ShapeDistance.between(triangle, square), DELTA);
  }

  @Test
  void theNearestSpotCanBeAPointOfTheFirstPolygonAgainstAnEdgeOfTheSecond() {
    // The triangle points at the middle of the left edge of the square, 4 blocks away.
    List<MapPoint> square = square(10, 0, 10);
    List<MapPoint> triangle = polygon(0, -2, 0, 12, 6, 5);

    assertEquals(4, ShapeDistance.between(triangle, square), DELTA);
  }

  @Test
  void theNearestSpotCanBeAPointOfTheSecondPolygonAgainstAnEdgeOfTheFirst() {
    List<MapPoint> square = square(10, 0, 10);
    List<MapPoint> triangle = polygon(0, -2, 0, 12, 6, 5);

    assertEquals(4, ShapeDistance.between(square, triangle), DELTA);
  }

  @Test
  void edgesThatCrossAreZeroApartEvenWithoutAPointInsideTheOther() {
    // A wide bar and a tall bar form a plus: no corner of either lies inside the other.
    List<MapPoint> wide = polygon(0, 4, 20, 4, 20, 6, 0, 6);
    List<MapPoint> tall = polygon(9, 0, 11, 0, 11, 10, 9, 10);

    assertEquals(0, ShapeDistance.between(wide, tall));
    assertEquals(0, ShapeDistance.between(tall, wide));
  }

  @Test
  void aPolygonInsideAnotherIsZeroApart() {
    List<MapPoint> big = square(0, 0, 100);
    List<MapPoint> small = square(40, 40, 5);

    assertEquals(0, ShapeDistance.between(big, small));
  }

  @Test
  void aPolygonThatContainsTheOtherIsZeroApart() {
    List<MapPoint> big = square(0, 0, 100);
    List<MapPoint> small = square(40, 40, 5);

    assertEquals(0, ShapeDistance.between(small, big));
  }

  @Test
  void identicalPolygonsAreZeroApart() {
    assertEquals(0, ShapeDistance.between(square(3, 3, 8), square(3, 3, 8)), DELTA);
  }

  @Test
  void squaresThatShareAnEdgeAreZeroApart() {
    assertEquals(0, ShapeDistance.between(square(0, 0, 10), square(10, 0, 10)), DELTA);
  }

  @Test
  void squaresThatShareOnlyACornerAreZeroApart() {
    assertEquals(0, ShapeDistance.between(square(0, 0, 10), square(10, 10, 10)), DELTA);
  }

  @Test
  void squaresThatOverlapPartlyAreZeroApart() {
    assertEquals(0, ShapeDistance.between(square(0, 0, 10), square(5, 5, 10)), DELTA);
  }

  @Test
  void edgesOnTheSameLineAreNotMistakenForCrossingEdges() {
    // The bottom edges of both squares lie on z = 0, so every side test comes out as zero.
    assertEquals(5, ShapeDistance.between(square(0, 0, 10), square(15, 0, 10)), DELTA);
    assertEquals(5, ShapeDistance.between(square(0, 0, 10), square(0, 15, 10)), DELTA);
  }

  @Test
  void aPolygonInTheNotchOfAConcavePolygonIsNotInsideIt() {
    // A U shape open at the top, and a square resting in the notch without touching the U.
    List<MapPoint> u = polygon(0, 0, 30, 0, 30, 20, 20, 20, 20, 10, 10, 10, 10, 20, 0, 20);
    List<MapPoint> notch = square(12, 12, 6);

    assertEquals(2, ShapeDistance.between(u, notch), DELTA);
    assertEquals(2, ShapeDistance.between(notch, u), DELTA);
  }

  @Test
  void aSinglePointIsTheDistanceToTheNearestEdge() {
    assertEquals(3, ShapeDistance.between(polygon(13, 5), square(0, 0, 10)), DELTA);
    assertEquals(3, ShapeDistance.between(square(0, 0, 10), polygon(13, 5)), DELTA);
  }

  @Test
  void aSinglePointInsideAPolygonIsZeroApart() {
    assertEquals(0, ShapeDistance.between(polygon(5, 5), square(0, 0, 10)));
    assertEquals(0, ShapeDistance.between(square(0, 0, 10), polygon(5, 5)));
  }

  @Test
  void twoSinglePointsAreTheStraightLineApart() {
    assertEquals(5, ShapeDistance.between(polygon(0, 0), polygon(3, 4)), DELTA);
    assertEquals(0, ShapeDistance.between(polygon(2, 2), polygon(2, 2)), DELTA);
  }

  @Test
  void aTwoPointPolygonIsALineSegment() {
    List<MapPoint> segment = polygon(0, 0, 10, 0);

    assertEquals(4, ShapeDistance.between(segment, polygon(5, 4)), DELTA);
    // Past the end of the segment the nearest spot is its end point, not the line behind it.
    assertEquals(5, ShapeDistance.between(segment, polygon(13, 4)), DELTA);
  }

  @Test
  void aPolygonWithARepeatedPointStillHasADistance() {
    // The zero length edge between the repeated points would divide by zero if it was not skipped.
    List<MapPoint> repeated = polygon(0, 0, 0, 0, 10, 0, 10, 10, 0, 10);

    assertEquals(5, ShapeDistance.between(repeated, square(15, 0, 10)), DELTA);
    assertEquals(5, ShapeDistance.between(square(15, 0, 10), repeated), DELTA);
  }

  @Test
  void aPolygonMadeOfOneRepeatedPointIsThatPoint() {
    List<MapPoint> collapsed = polygon(20, 20, 20, 20, 20, 20);

    assertEquals(10, ShapeDistance.between(collapsed, square(0, 20, 10)), DELTA);
  }

  @Test
  void collinearPointsAreASegmentWithNoInside() {
    List<MapPoint> line = polygon(0, 0, 5, 0, 10, 0);

    assertEquals(3, ShapeDistance.between(line, polygon(5, 3)), DELTA);
  }

  @Test
  void aSelfIntersectingPolygonIsMeasuredToItsEdges() {
    // A bow tie: the two diagonals cross in the middle.
    List<MapPoint> bowTie = polygon(0, 0, 10, 10, 10, 0, 0, 10);

    assertEquals(4, ShapeDistance.between(bowTie, polygon(14, 5)), DELTA);
    assertEquals(0, ShapeDistance.between(bowTie, polygon(5, 5)), DELTA);
  }

  @Test
  void distancesAreMeasuredBetweenTheNearestPointsOfTheShapes() {
    List<MapPoint> left = polygon(-100, -100, -90, -100, -90, -90, -100, -90);
    List<MapPoint> right = polygon(-60, -60, -50, -60, -50, -50, -60, -50);

    assertEquals(Math.hypot(30, 30), ShapeDistance.between(left, right), DELTA);
  }
}
