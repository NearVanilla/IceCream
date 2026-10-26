package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.List;

/**
 * Distances between the polygons that claims are drawn as.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-25
 */
final class ShapeDistance {

  private ShapeDistance() {
    // Utility class; prevent instantiation.
  }

  /**
   * Whether the bounding boxes of two polygons are within a range, a cheap check to skip far ones.
   */
  static boolean boxesWithin(List<MapPoint> a, List<MapPoint> b, double range) {
    if (a.isEmpty() || b.isEmpty()) return false;
    double[] boxA = box(a);
    double[] boxB = box(b);
    return boxA[0] - range <= boxB[2]
        && boxB[0] - range <= boxA[2]
        && boxA[1] - range <= boxB[3]
        && boxB[1] - range <= boxA[3];
  }

  /** The smallest and largest x and z of a polygon: min x, min z, max x, max z. */
  private static double[] box(List<MapPoint> polygon) {
    double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
    for (MapPoint point : polygon) {
      box[0] = Math.min(box[0], point.x());
      box[1] = Math.min(box[1], point.z());
      box[2] = Math.max(box[2], point.x());
      box[3] = Math.max(box[3], point.z());
    }
    return box;
  }

  /** The shortest distance between two polygons, 0 if they touch, cross or contain each other. */
  static double between(List<MapPoint> a, List<MapPoint> b) {
    if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE;
    if (contains(b, a.getFirst()) || contains(a, b.getFirst()) || edgesCross(a, b)) return 0;
    double shortest = Double.MAX_VALUE;
    for (int i = 0; i < a.size(); i++) {
      shortest = Math.min(shortest, distanceToEdges(a.get(i), b));
    }
    for (int i = 0; i < b.size(); i++) {
      shortest = Math.min(shortest, distanceToEdges(b.get(i), a));
    }
    return shortest;
  }

  private static double distanceToEdges(MapPoint point, List<MapPoint> polygon) {
    double shortest = Double.MAX_VALUE;
    for (int i = 0; i < polygon.size(); i++) {
      MapPoint start = polygon.get(i);
      MapPoint end = polygon.get((i + 1) % polygon.size());
      double dx = end.x() - start.x();
      double dz = end.z() - start.z();
      double length = dx * dx + dz * dz;
      double t =
          length == 0
              ? 0
              : Math.clamp(
                  ((point.x() - start.x()) * dx + (point.z() - start.z()) * dz) / length, 0, 1);
      shortest =
          Math.min(
              shortest,
              Math.hypot(point.x() - (start.x() + t * dx), point.z() - (start.z() + t * dz)));
    }
    return shortest;
  }

  /** Whether a point is inside a polygon, by counting how many edges a ray from it crosses. */
  private static boolean contains(List<MapPoint> polygon, MapPoint point) {
    boolean inside = false;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      MapPoint a = polygon.get(i);
      MapPoint b = polygon.get(j);
      if ((a.z() > point.z()) != (b.z() > point.z())
          && point.x() < (b.x() - a.x()) * (point.z() - a.z()) / (b.z() - a.z()) + a.x()) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static boolean edgesCross(List<MapPoint> a, List<MapPoint> b) {
    for (int i = 0; i < a.size(); i++) {
      MapPoint a1 = a.get(i);
      MapPoint a2 = a.get((i + 1) % a.size());
      for (int j = 0; j < b.size(); j++) {
        MapPoint b1 = b.get(j);
        MapPoint b2 = b.get((j + 1) % b.size());
        if (side(a1, a2, b1) * side(a1, a2, b2) < 0 && side(b1, b2, a1) * side(b1, b2, a2) < 0) {
          return true;
        }
      }
    }
    return false;
  }

  /** Which side of the line from start to end a point is on: positive, negative or 0 on it. */
  private static double side(MapPoint start, MapPoint end, MapPoint point) {
    return Math.signum(
        (end.x() - start.x()) * (point.z() - start.z())
            - (end.z() - start.z()) * (point.x() - start.x()));
  }
}
