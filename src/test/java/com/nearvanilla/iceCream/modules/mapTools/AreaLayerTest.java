package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.jpenilla.squaremap.api.marker.Marker;

/** The squaremap layer: what it says about itself and the order it hands its markers out in. */
class AreaLayerTest {

  private static MapPoint point(double x, double z) {
    return new MapPoint(x, z);
  }

  /**
   * An axis aligned square outline of the given side, counterclockwise on the x and z plane. It is
   * not at the origin, so the side that closes the outline has an area too.
   */
  private static List<MapPoint> square(double side) {
    return List.of(
        point(10, 20), point(10 + side, 20), point(10 + side, 20 + side), point(10, 20 + side));
  }

  /** A counterclockwise square outline with its first corner at the given spot. */
  private static List<MapPoint> squareAt(double x, double z, double side) {
    return List.of(point(x, z), point(x + side, z), point(x + side, z + side), point(x, z + side));
  }

  private static Marker marker() {
    return mock(Marker.class);
  }

  // What the layer says about itself

  @Test
  void theLayerReportsItsLabel() {
    assertEquals("Claims", new AreaLayer("Claims", 10).getLabel());
  }

  @Test
  void theLayerReportsItsPriority() {
    assertEquals(10, new AreaLayer("Claims", 10).layerPriority());
    assertEquals(11, new AreaLayer("Markers", 11).layerPriority());
  }

  @Test
  void theDrawingOrderStartsAt240AndFollowsThePriority() {
    assertEquals(250, new AreaLayer("Claims", 10).zIndex());
    assertEquals(251, new AreaLayer("Markers", 11).zIndex());
    assertEquals(240, new AreaLayer("Zero", 0).zIndex());
  }

  @Test
  void theLayerShowsItsControls() {
    assertTrue(new AreaLayer("Claims", 10).showControls());
  }

  @Test
  void aNewLayerHasNoMarkers() {
    assertTrue(new AreaLayer("Claims", 10).getMarkers().isEmpty());
  }

  // Putting and removing

  @Test
  void aMarkerIsHandedOutAfterItIsPut() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker home = marker();

    layer.put("home", home, square(10));

    assertEquals(List.of(home), List.copyOf(layer.getMarkers()));
  }

  @Test
  void putAgainReplacesTheMarkerOfTheSameId() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker before = marker();
    Marker after = marker();
    layer.put("home", before, square(10));

    layer.put("home", after, square(10));

    assertEquals(List.of(after), List.copyOf(layer.getMarkers()));
  }

  @Test
  void removeTakesAMarkerOut() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker home = marker();
    Marker shop = marker();
    layer.put("home", home, square(10));
    layer.put("shop", shop, square(20));

    layer.remove("shop");

    assertEquals(List.of(home), List.copyOf(layer.getMarkers()));
  }

  @Test
  void removeOfAnIdThatWasNeverPutChangesNothing() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker home = marker();
    layer.put("home", home, square(10));

    layer.remove("nothing");

    assertEquals(List.of(home), List.copyOf(layer.getMarkers()));
  }

  @Test
  void removingTheLastMarkerEmptiesTheLayer() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    layer.put("home", marker(), square(10));

    layer.remove("home");

    assertTrue(layer.getMarkers().isEmpty());
  }

  // Ordering

  @Test
  void largerAreasAreHandedOutBeforeSmallerOnesSoSmallerOnesAreDrawnOnTop() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker small = marker();
    Marker large = marker();
    Marker medium = marker();
    layer.put("small", small, square(5));
    layer.put("large", large, square(50));
    layer.put("medium", medium, square(20));

    assertEquals(List.of(large, medium, small), List.copyOf(layer.getMarkers()));
  }

  @Test
  void theOrderDoesNotDependOnTheOrderTheAreasWereAdded() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker small = marker();
    Marker large = marker();
    layer.put("a", large, square(50));
    layer.put("b", small, square(5));

    assertEquals(List.of(large, small), List.copyOf(layer.getMarkers()));
  }

  @Test
  void replacingAMarkerWithABiggerAreaMovesItToTheFront() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker grown = marker();
    Marker other = marker();
    layer.put("a", marker(), square(5));
    layer.put("b", other, square(20));

    layer.put("a", grown, square(100));

    assertEquals(List.of(grown, other), List.copyOf(layer.getMarkers()));
  }

  @Test
  void theSizeIsTheAreaInsideTheOutlineNotItsLengthOrItsBoundingBox() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker triangle = marker();
    Marker thinSquare = marker();
    // The triangle has the longer sides and the bigger bounding box (20 x 20 = 400), but covers
    // 200 square blocks. The square covers 15 x 15 = 225.
    layer.put("triangle", triangle, List.of(point(10, 20), point(30, 20), point(10, 40)));
    layer.put("square", thinSquare, square(15));

    assertEquals(List.of(thinSquare, triangle), List.copyOf(layer.getMarkers()));
  }

  @Test
  void whereTheAreaLiesDoesNotChangeHowBigItIs() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker farAndSmall = marker();
    Marker nearAndLarge = marker();
    layer.put("far", farAndSmall, squareAt(100, 100, 10));
    layer.put("near", nearAndLarge, squareAt(0, 0, 20));

    // 100 square blocks against 400. Leaving the side that closes the outline out of the sum makes
    // the far square count as 600.
    assertEquals(List.of(nearAndLarge, farAndSmall), List.copyOf(layer.getMarkers()));
  }

  @Test
  void anOutlineGoingClockwiseCountsTheSameAsOneGoingCounterclockwise() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker clockwise = marker();
    Marker counterclockwise = marker();
    Marker smaller = marker();
    layer.put("small", smaller, square(5));
    layer.put(
        "clockwise",
        clockwise,
        List.of(point(10, 20), point(10, 50), point(40, 50), point(40, 20)));
    layer.put("counter", counterclockwise, square(10));

    // Without the absolute value the clockwise square would have a negative size and come last.
    assertEquals(List.of(clockwise, counterclockwise, smaller), List.copyOf(layer.getMarkers()));
  }

  @Test
  void anOutlineWithoutAnAreaGoesLast() {
    AreaLayer layer = new AreaLayer("Markers", 11);
    Marker dot = marker();
    Marker line = marker();
    Marker nothing = marker();
    Marker claim = marker();
    layer.put("dot", dot, List.of(point(5, 5)));
    layer.put("line", line, List.of(point(0, 0), point(10, 10)));
    layer.put("empty", nothing, List.of());
    layer.put("claim", claim, square(2));

    List<Marker> handedOut = List.copyOf(layer.getMarkers());

    assertEquals(claim, handedOut.getFirst());
    assertEquals(4, handedOut.size());
    assertTrue(handedOut.containsAll(List.of(dot, line, nothing)));
  }

  @Test
  void aPointsOutlineMarkerCanBePutAndRemoved() {
    AreaLayer layer = new AreaLayer("Markers", 11);
    Marker spot = marker();

    layer.put("spot", spot, List.of(point(3.5, 3.5)));
    assertEquals(List.of(spot), List.copyOf(layer.getMarkers()));

    layer.remove("spot");
    assertTrue(layer.getMarkers().isEmpty());
  }

  @Test
  void aListHandedOutEarlierDoesNotChangeWhenTheLayerDoes() {
    AreaLayer layer = new AreaLayer("Claims", 10);
    Marker home = marker();
    layer.put("home", home, square(10));
    Collection<Marker> earlier = layer.getMarkers();

    layer.put("shop", marker(), square(20));
    layer.remove("home");

    assertEquals(List.of(home), List.copyOf(earlier));
  }
}
