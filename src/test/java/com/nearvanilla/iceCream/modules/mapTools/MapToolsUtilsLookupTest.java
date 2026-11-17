package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils.NearbyClaim;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Finding the claim at a spot and the claims near a new shape. */
class MapToolsUtilsLookupTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String NETHER = MapToolsTestUtils.NETHER;
  private static final double DELTA = 1e-9;

  private Scenario scenario;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("modules:\n  maptools:\n    nearby-claim-distance: 10\n");
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private void store(MapArea... areas) {
    for (MapArea area : areas) scenario.store().putArea(area);
  }

  private static AreaGeometry rectShape(String world, int x1, int z1, int x2, int z2) {
    return new AreaGeometry(
        world, MapToolsUtils.rectangle(x1, z1, x2, z2), MapArea.RECT, null, null);
  }

  @Test
  void areaAtFindsTheClaimAroundASpot() {
    MapArea home = MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9);
    store(home);

    assertSame(home, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
  }

  @Test
  void areaAtIsNullWhereThereIsNoClaim() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertNull(MapToolsUtils.areaAt(WORLD, 10.5, 5.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, -0.5, 5.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 10.5, null));
  }

  @Test
  void areaAtIsNullWithoutAnyAreas() {
    assertNull(MapToolsUtils.areaAt(WORLD, 0, 0, null));
  }

  @Test
  void areaAtOnlyLooksInTheGivenWorld() {
    MapArea home = MapToolsTestUtils.rect("home", NETHER, 0, 0, 9, 9);
    store(home);

    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
    assertSame(home, MapToolsUtils.areaAt(NETHER, 5.5, 5.5, null));
  }

  @Test
  void areaAtWorksInTheNegativeQuadrant() {
    MapArea home = MapToolsTestUtils.rect("home", WORLD, -20, -20, -11, -11);
    store(home);

    assertSame(home, MapToolsUtils.areaAt(WORLD, -15.5, -15.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 15.5, 15.5, null));
  }

  @Test
  void areaAtSkipsTheGivenId() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 5.5, "home"));
  }

  @Test
  void areaAtFindsAnotherClaimUnderTheSkippedOne() {
    MapArea big = MapToolsTestUtils.rect("big", WORLD, 0, 0, 99, 99);
    store(MapToolsTestUtils.rect("small", WORLD, 10, 10, 20, 20), big);

    assertSame(big, MapToolsUtils.areaAt(WORLD, 15.5, 15.5, "small"));
  }

  @Test
  void areaAtGivesTheFirstStoredOfTwoOverlappingClaims() {
    MapArea first = MapToolsTestUtils.rect("first", WORLD, 0, 0, 9, 9);
    MapArea second = MapToolsTestUtils.rect("second", WORLD, 5, 5, 15, 15);
    store(first, second);

    assertSame(first, MapToolsUtils.areaAt(WORLD, 7.5, 7.5, null));
    assertSame(second, MapToolsUtils.areaAt(WORLD, 12.5, 12.5, null));
  }

  @Test
  void areaAtSkipsHiddenClaims() {
    MapArea hidden = MapToolsTestUtils.rect("hidden", WORLD, 0, 0, 9, 9).withHidden(true);
    MapArea shown = MapToolsTestUtils.rect("shown", WORLD, 0, 0, 20, 20);
    store(hidden, shown);

    assertSame(shown, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
  }

  @Test
  void areaAtSkipsHiddenMarkers() {
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3).withHidden(true));

    assertNull(MapToolsUtils.areaAt(WORLD, 3.5, 3.5, null));
  }

  @Test
  void areaAtFindsACircleInsideItsOutline() {
    MapArea pond = MapToolsTestUtils.circle("pond", WORLD, 0, 0, 20);
    store(pond);

    assertSame(pond, MapToolsUtils.areaAt(WORLD, 10, 10, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 18, 18, null));
  }

  @Test
  void areaAtFindsAMarkerOnItsOwnBlockOnly() {
    MapArea spot = MapToolsTestUtils.marker("spot", WORLD, 5, 5);
    store(spot);

    assertSame(spot, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
    assertSame(spot, MapToolsUtils.areaAt(WORLD, 5.0, 5.0, null));
    assertSame(spot, MapToolsUtils.areaAt(WORLD, 5.99, 5.99, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 6.0, 5.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 6.0, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 4.99, 5.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 4.99, null));
  }

  @Test
  void areaAtFindsAMarkerOnANegativeBlock() {
    MapArea spot = MapToolsTestUtils.marker("spot", WORLD, -1, -1);
    store(spot);

    // Block -1 covers -1 up to 0, so -0.9 is inside it and 0.1 is not.
    assertSame(spot, MapToolsUtils.areaAt(WORLD, -0.9, -0.1, null));
    assertSame(spot, MapToolsUtils.areaAt(WORLD, -1.0, -1.0, null));
    assertNull(MapToolsUtils.areaAt(WORLD, 0.1, -0.5, null));
    assertNull(MapToolsUtils.areaAt(WORLD, -0.5, 0.1, null));
    assertNull(MapToolsUtils.areaAt(WORLD, -1.1, -0.5, null));
  }

  @Test
  void areaAtIgnoresAMarkerInAnotherWorld() {
    store(MapToolsTestUtils.marker("spot", NETHER, 5, 5));

    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
  }

  @Test
  void areaAtSkipsTheGivenMarker() {
    store(MapToolsTestUtils.marker("spot", WORLD, 5, 5));

    assertNull(MapToolsUtils.areaAt(WORLD, 5.5, 5.5, "spot"));
  }

  @Test
  void areaAtPrefersAMarkerOverTheClaimAroundIt() {
    MapArea claim = MapToolsTestUtils.rect("claim", WORLD, 0, 0, 20, 20);
    MapArea spot = MapToolsTestUtils.marker("spot", WORLD, 5, 5);
    store(claim, spot);

    assertSame(spot, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
    assertSame(claim, MapToolsUtils.areaAt(WORLD, 7.5, 7.5, null));
  }

  @Test
  void areaAtPrefersAMarkerWhateverTheStoreOrder() {
    MapArea spot = MapToolsTestUtils.marker("spot", WORLD, 5, 5);
    MapArea claim = MapToolsTestUtils.rect("claim", WORLD, 0, 0, 20, 20);
    store(spot, claim);

    assertSame(spot, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
  }

  @Test
  void areaAtGivesTheFirstOfTwoMarkersOnTheSameBlock() {
    MapArea first = MapToolsTestUtils.marker("first", WORLD, 5, 5);
    store(first, MapToolsTestUtils.marker("second", WORLD, 5, 5));

    assertSame(first, MapToolsUtils.areaAt(WORLD, 5.5, 5.5, null));
  }

  @Test
  void nearbyClaimsFindsClaimsWithinTheConfiguredDistanceClosestFirst() {
    MapArea far = MapToolsTestUtils.rect("far", WORLD, 19, 0, 28, 9);
    MapArea near = MapToolsTestUtils.rect("near", WORLD, 14, 0, 23, 9);
    store(far, near);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(2, found.size());
    assertSame(near, found.get(0).claim());
    assertEquals(4, found.get(0).distance(), DELTA);
    assertSame(far, found.get(1).claim());
    assertEquals(9, found.get(1).distance(), DELTA);
  }

  @Test
  void nearbyClaimsIncludesAClaimExactlyAtTheDistance() {
    MapArea edge = MapToolsTestUtils.rect("edge", WORLD, 20, 0, 29, 9);
    store(edge);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(1, found.size());
    assertEquals(10, found.getFirst().distance(), DELTA);
  }

  @Test
  void nearbyClaimsLeavesOutAClaimJustBeyondTheDistance() {
    store(MapToolsTestUtils.rect("beyond", WORLD, 21, 0, 30, 9));

    assertTrue(MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void nearbyClaimsMeasuresTheStraightDistanceNotTheBoxes() {
    // The boxes are 8 apart on each axis, which is in range, but the corners are 11.3 apart.
    store(MapToolsTestUtils.rect("diagonal", WORLD, 18, 18, 27, 27));

    assertTrue(MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void nearbyClaimsFindsAnOverlappingClaimAtDistanceZeroFirst() {
    MapArea overlap = MapToolsTestUtils.rect("overlap", WORLD, 5, 5, 14, 14);
    MapArea near = MapToolsTestUtils.rect("near", WORLD, 14, 0, 23, 9);
    store(near, overlap);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(List.of(overlap, near), found.stream().map(NearbyClaim::claim).toList());
    assertEquals(0, found.getFirst().distance(), DELTA);
  }

  @Test
  void nearbyClaimsFindsAClaimThatContainsTheShape() {
    MapArea around = MapToolsTestUtils.rect("around", WORLD, -50, -50, 50, 50);
    store(around);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(1, found.size());
    assertEquals(0, found.getFirst().distance());
  }

  @Test
  void nearbyClaimsLeavesOutMarkers() {
    store(MapToolsTestUtils.marker("spot", WORLD, 5, 5));

    assertTrue(MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void nearbyClaimsLeavesOutClaimsInAnotherWorld() {
    store(MapToolsTestUtils.rect("other", NETHER, 5, 5, 14, 14));

    assertTrue(MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void nearbyClaimsLeavesOutTheClaimBeingEdited() {
    MapArea editing = MapToolsTestUtils.rect("editing", WORLD, 0, 0, 9, 9);
    MapArea other = MapToolsTestUtils.rect("other", WORLD, 14, 0, 23, 9);
    store(editing, other);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), "editing");

    assertEquals(List.of(other), found.stream().map(NearbyClaim::claim).toList());
  }

  @Test
  void nearbyClaimsOfANewClaimIncludesEveryClaim() {
    MapArea same = MapToolsTestUtils.rect("same", WORLD, 0, 0, 9, 9);
    store(same);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(List.of(same), found.stream().map(NearbyClaim::claim).toList());
  }

  @Test
  void nearbyClaimsIncludesHiddenClaims() {
    MapArea hidden = MapToolsTestUtils.rect("hidden", WORLD, 14, 0, 23, 9).withHidden(true);
    store(hidden);

    assertEquals(1, MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).size());
  }

  @Test
  void nearbyClaimsMeasuresACircleBySizeOfItsOutline() {
    MapArea pond = MapToolsTestUtils.circle("pond", WORLD, 40, 5, 10);
    store(pond);

    // The outline of the circle reaches 10 blocks west of its center, 20.5 blocks from the claim.
    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);
    assertTrue(found.isEmpty());

    scenario.configure("modules:\n  maptools:\n    nearby-claim-distance: 25\n");
    store(pond);
    found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);
    assertEquals(1, found.size());
    assertEquals(20.5, found.getFirst().distance(), DELTA);
  }

  @Test
  void nearbyClaimsOfACircleShapeWorksToo() {
    MapArea next = MapToolsTestUtils.rect("next", WORLD, 20, -5, 30, 5);
    store(next);
    AreaGeometry circle = MapToolsUtils.circleShape(WORLD, new MapPoint(5.5, 0.5), 5);

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(circle, null);

    assertEquals(1, found.size());
    assertEquals(9.5, found.getFirst().distance(), DELTA);
  }

  @Test
  void nearbyClaimsUsesTheDefaultDistanceOfThreeHundredWithoutConfiguration() {
    scenario.configure("");
    store(
        MapToolsTestUtils.rect("in-range", WORLD, 310, 0, 319, 9),
        MapToolsTestUtils.rect("out-of-range", WORLD, 311, 0, 320, 9));

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(List.of("in-range"), found.stream().map(n -> n.claim().id()).toList());
    assertEquals(300, found.getFirst().distance(), DELTA);
  }

  @Test
  void aDistanceOfZeroOnlyFindsClaimsThatTouchOrOverlap() {
    scenario.configure("modules:\n  maptools:\n    nearby-claim-distance: 0\n");
    MapArea touching = MapToolsTestUtils.rect("touching", WORLD, 10, 0, 19, 9);
    store(touching, MapToolsTestUtils.rect("apart", WORLD, 11, 0, 20, 9));

    List<NearbyClaim> found = MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null);

    assertEquals(List.of("touching"), found.stream().map(n -> n.claim().id()).toList());
    assertEquals(0, found.getFirst().distance(), DELTA);
  }

  @Test
  void nearbyClaimsIsEmptyWithoutClaims() {
    assertTrue(MapToolsUtils.nearbyClaims(rectShape(WORLD, 0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void nearbyClaimsIsEmptyWhenTheShapeIsInAnotherWorld() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertTrue(MapToolsUtils.nearbyClaims(rectShape(NETHER, 0, 0, 9, 9), null).isEmpty());
  }
}
