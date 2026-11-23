package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MapToolsUtilsTest {

  /** Typed corners come back as the same blocks, whatever their sign or order. */
  @ParameterizedTest
  @CsvSource({
    "0, 0, 10, 10, 0, 0, 10, 10",
    "10, 10, 0, 0, 0, 0, 10, 10",
    "-94, -13, -28, 20, -94, -13, -28, 20",
    "-28, 20, -94, -13, -94, -13, -28, 20",
    "-10, -10, -1, -1, -10, -10, -1, -1",
    "-1, -1, 0, 0, -1, -1, 0, 0",
    "5, 5, 5, 5, 5, 5, 5, 5",
    "-5, -5, -5, -5, -5, -5, -5, -5"
  })
  void rectangleRoundTrips(int x1, int z1, int x2, int z2, int minX, int minZ, int maxX, int maxZ) {
    assertArrayEquals(
        new int[] {minX, minZ, maxX, maxZ},
        MapToolsUtils.rectangleBlocks(MapToolsUtils.rectangle(x1, z1, x2, z2)));
  }

  /** Polygons have at most {@link MapToolsUtils#MAX_POINTS} points. */
  @Test
  void polygonsHaveAtMostMaxPoints() {
    assertNotNull(
        MapToolsUtils.parsePoints("minecraft:overworld", polygon(MapToolsUtils.MAX_POINTS)));
    assertNull(
        MapToolsUtils.parsePoints("minecraft:overworld", polygon(MapToolsUtils.MAX_POINTS + 1)));
  }

  /** Points on a circle, so none are in one line. */
  private static String polygon(int count) {
    StringBuilder points = new StringBuilder();
    for (int i = 0; i < count; i++) {
      double angle = 2 * Math.PI * i / count;
      if (i > 0) points.append(";");
      points
          .append(Math.round(1000 * Math.cos(angle)))
          .append(" ")
          .append(Math.round(1000 * Math.sin(angle)));
    }
    return points.toString();
  }
}
