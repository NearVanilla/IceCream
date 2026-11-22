package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeDistanceTest {

  private static List<MapPoint> rect(int x1, int z1, int x2, int z2) {
    return MapToolsUtils.rectangle(x1, z1, x2, z2);
  }

  /** Neighbouring typed rectangles share an edge, 0 blocks apart, but no blocks. */
  @Test
  void neighboursTouchWithoutOverlapping() {
    List<MapPoint> a = rect(0, 0, 9, 9);
    List<MapPoint> b = rect(10, 0, 19, 9);
    assertEquals(0, ShapeDistance.between(a, b));
    assertFalse(ShapeDistance.overlaps(a, b));
  }

  @Test
  void cornersTouchingDoNotOverlap() {
    assertFalse(ShapeDistance.overlaps(rect(0, 0, 9, 9), rect(10, 10, 19, 19)));
  }

  @Test
  void sharedBlocksOverlap() {
    assertTrue(ShapeDistance.overlaps(rect(0, 0, 9, 9), rect(9, 0, 19, 9)));
  }

  /** Overlap along a shared side line, where no edges cross. */
  @Test
  void sameRowOverlap() {
    assertTrue(ShapeDistance.overlaps(rect(0, 0, 10, 10), rect(5, 0, 15, 10)));
  }

  @Test
  void identicalShapesOverlap() {
    assertTrue(ShapeDistance.overlaps(rect(0, 0, 9, 9), rect(0, 0, 9, 9)));
  }

  @Test
  void containedShapeOverlaps() {
    assertTrue(ShapeDistance.overlaps(rect(0, 0, 20, 20), rect(5, 5, 6, 6)));
    assertTrue(ShapeDistance.overlaps(rect(5, 5, 6, 6), rect(0, 0, 20, 20)));
  }

  @Test
  void crossingShapesOverlap() {
    assertTrue(ShapeDistance.overlaps(rect(0, 5, 20, 6), rect(5, 0, 6, 20)));
  }

  @Test
  void farShapesDoNotOverlap() {
    assertFalse(ShapeDistance.overlaps(rect(0, 0, 9, 9), rect(50, 50, 59, 59)));
  }

  @Test
  void circleTouchingRectangleDoesNotOverlap() {
    List<MapPoint> circle = MapToolsUtils.circle(0, 0, 10);
    assertFalse(ShapeDistance.overlaps(circle, rect(10, -5, 20, 5)));
    assertTrue(ShapeDistance.overlaps(circle, rect(5, -5, 20, 5)));
  }
}
