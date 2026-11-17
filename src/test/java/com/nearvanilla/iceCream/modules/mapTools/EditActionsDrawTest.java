package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Clicking blocks while viewing and while drawing a new shape. */
class EditActionsDrawTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String HINT = "Run /maptools save to fill in the details and save it.";

  private Scenario scenario;
  private DialogPlayer alex;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private Location at(int x, int z) {
    return scenario.overworldBlock(x, 64, z);
  }

  private EditSession draw(Mode mode) {
    EditSession session = EditSession.create(mode);
    MapToolsUtils.startEditing(alex, session);
    return session;
  }

  private void click(int x, int z) {
    EditActions.click(alex, at(x, z), false);
  }

  private void sneakClick(int x, int z) {
    EditActions.click(alex, at(x, z), true);
  }

  @Test
  void clickingWithoutASessionDoesNothing() {
    EditActions.click(alex, at(0, 0), false);
    EditActions.click(alex, at(0, 0), true);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void clickingAnAreaWhileViewingStartsEditingIt() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, EditSession.view());

    click(5, 5);

    EditSession session = MapToolsModule.sessions.get(alex.getUniqueId());
    assertEquals("home", session.getAreaId());
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingAnAreaWhileViewingStartsEditingItWhenSneakingToo() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, EditSession.view());

    sneakClick(5, 5);

    assertEquals("home", MapToolsModule.sessions.get(alex.getUniqueId()).getAreaId());
  }

  @Test
  void clickingOutsideEveryAreaWhileViewingDoesNothing() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    EditSession view = EditSession.view();
    MapToolsUtils.startEditing(alex, view);

    click(50, 50);

    assertSame(view, MapToolsModule.sessions.get(alex.getUniqueId()));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void clickingAMarkerWhileViewingStartsEditingIt() {
    scenario.store().putArea(MapToolsTestUtils.marker("spot", WORLD, 3, 3));
    MapToolsUtils.startEditing(alex, EditSession.view());

    click(3, 3);

    EditSession session = MapToolsModule.sessions.get(alex.getUniqueId());
    assertEquals("spot", session.getAreaId());
    assertEquals(Mode.MARKER, session.getMode());
  }

  @Test
  void clickingAnAreaSomeoneElseEditsWhileViewingTellsThePlayer() {
    MapArea home = MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9);
    scenario.store().putArea(home);
    DialogPlayer bob = scenario.editor("Bob");
    MapToolsUtils.tryEditing(bob, home);
    MapToolsUtils.startEditing(alex, EditSession.view());

    click(5, 5);

    assertEquals("Bob is already editing home.", MessageTestUtils.nextMessage(alex));
    assertTrue(MapToolsModule.sessions.get(alex.getUniqueId()).isViewing());
  }

  @Test
  void theFirstClickOfARectangleSetsTheFirstCorner() {
    EditSession session = draw(Mode.RECT);

    click(2, 3);

    assertEquals(List.of(at(2, 3)), session.definingBlocks());
    assertNull(session.toGeometry());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aSneakClickOfARectangleSetsTheSecondCorner() {
    EditSession session = draw(Mode.RECT);
    click(2, 3);

    sneakClick(5, 7);

    assertEquals(MapToolsUtils.rectangle(2, 3, 5, 7), session.toGeometry().points());
  }

  @Test
  void aRectangleCanStartWithTheSecondCorner() {
    EditSession session = draw(Mode.RECT);

    sneakClick(5, 7);
    assertNull(session.toGeometry());
    click(2, 3);

    assertEquals(MapToolsUtils.rectangle(2, 3, 5, 7), session.toGeometry().points());
  }

  @Test
  void aClickSetsTheFirstCornerAndASneakClickTheSecondCorner() {
    EditSession session = draw(Mode.RECT);

    click(2, 3);
    sneakClick(5, 7);

    assertEquals(List.of(at(2, 3), at(5, 3), at(5, 7), at(2, 7)), session.definingBlocks());
  }

  @Test
  void clickingAgainMovesACornerOfTheRectangle() {
    EditSession session = draw(Mode.RECT);
    click(2, 3);
    sneakClick(5, 7);

    click(-4, -6);
    sneakClick(10, 12);

    assertEquals(MapToolsUtils.rectangle(-4, -6, 10, 12), session.toGeometry().points());
  }

  @Test
  void theHintIsShownOnceTheRectangleIsComplete() {
    draw(Mode.RECT);
    click(2, 3);
    assertNull(alex.nextComponentMessage());

    sneakClick(5, 7);

    assertEquals(HINT, MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void theHintIsShownOnlyOncePerSession() {
    draw(Mode.RECT);
    click(2, 3);
    sneakClick(5, 7);
    alex.nextComponentMessage();

    click(0, 0);
    sneakClick(9, 9);

    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aNewSessionShowsTheHintAgain() {
    draw(Mode.MARKER);
    click(1, 1);
    assertEquals(HINT, MessageTestUtils.nextMessage(alex));

    draw(Mode.MARKER);
    click(2, 2);

    assertEquals(HINT, MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aMarkerIsPlacedByAClickAndNotBySneaking() {
    EditSession session = draw(Mode.MARKER);

    sneakClick(4, 4);
    assertNull(session.toGeometry());
    assertNull(alex.nextComponentMessage());

    click(5, 6);
    assertEquals(List.of(at(5, 6)), session.definingBlocks());
    assertEquals(HINT, MessageTestUtils.nextMessage(alex));

    click(-1, -1);
    assertEquals(List.of(at(-1, -1)), session.definingBlocks());
  }

  @Test
  void aCircleNeedsACenterBeforeARadius() {
    EditSession session = draw(Mode.CIRCLE);

    sneakClick(5, 5);

    assertNull(session.getCenter());
    assertNull(session.getRadius());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aSneakClickSetsTheRadiusOfACircleToTheDistanceFromTheCenter() {
    EditSession session = draw(Mode.CIRCLE);
    click(0, 0);
    assertNull(alex.nextComponentMessage());

    sneakClick(6, 8);

    assertEquals(10, session.getRadius());
    assertEquals(HINT, MessageTestUtils.nextMessage(alex));
    assertEquals(MapArea.CIRCLE, session.toGeometry().shape());
  }

  @Test
  void theRadiusIsTheDistanceRoundedAndNeverBelowOne() {
    EditSession session = draw(Mode.CIRCLE);
    click(10, 10);

    sneakClick(10, 10);
    assertEquals(1, session.getRadius());
    sneakClick(11, 11);
    assertEquals(1, session.getRadius());
    sneakClick(12, 12);
    assertEquals(3, session.getRadius());
    sneakClick(10, 20);
    assertEquals(10, session.getRadius());
    sneakClick(0, 10);
    assertEquals(10, session.getRadius());
    sneakClick(-14, 10);
    assertEquals(24, session.getRadius());
  }

  @Test
  void clickingMovesTheCenterOfACircleAndKeepsItsRadius() {
    EditSession session = draw(Mode.CIRCLE);
    click(0, 0);
    sneakClick(0, 5);

    click(20, 30);

    assertEquals(5, session.getRadius());
    assertEquals(at(20, 30), session.getCenter());
  }

  @Test
  void clickingThreeBlocksMakesAPolygon() {
    EditSession session = draw(Mode.POLYGON);

    click(0, 0);
    click(10, 0);
    assertNull(alex.nextComponentMessage());
    click(10, 10);

    assertEquals(List.of(at(0, 0), at(10, 0), at(10, 10)), session.definingBlocks());
    assertEquals(HINT, MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aPolygonGrowsWithEveryClickAwayFromItsPoints() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);

    click(0, 10);

    assertEquals(4, session.getPointCount());
    assertEquals(at(0, 10), session.definingBlocks().get(3));
  }

  @Test
  void clickingAPointOfAPolygonPicksItUp() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);

    click(10, 0);

    assertEquals(1, session.getPicked());
    assertEquals(3, session.getPointCount());
  }

  @Test
  void clickingAwayWithAPickedPointMovesIt() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    click(10, 0);

    click(20, -5);

    assertEquals(List.of(at(0, 0), at(20, -5), at(10, 10)), session.definingBlocks());
    assertNull(session.getPicked());
  }

  @Test
  void aSneakClickOnAPointOfAPolygonRemovesIt() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    click(0, 10);
    click(10, 0);

    sneakClick(10, 0);

    assertEquals(List.of(at(0, 0), at(10, 10), at(0, 10)), session.definingBlocks());
    assertNull(session.getPicked());
  }

  @Test
  void aPolygonBeingDrawnCanLoseItsPointsDownToNone() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);

    sneakClick(10, 0);
    sneakClick(0, 0);

    assertEquals(0, session.getPointCount());
  }

  @Test
  void aSneakClickAwayFromThePointsOfAPolygonSplitsTheNearestEdge() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);

    sneakClick(5, -3);

    assertEquals(List.of(at(0, 0), at(5, -3), at(10, 0), at(10, 10)), session.definingBlocks());
  }

  @Test
  void aSneakClickNearTheClosingEdgeOfAPolygonAddsAtTheEnd() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);

    sneakClick(2, 8);

    assertEquals(List.of(at(0, 0), at(10, 0), at(10, 10), at(2, 8)), session.definingBlocks());
  }

  @Test
  void aSneakClickOnAnEmptyPolygonAddsTheFirstPoint() {
    EditSession session = draw(Mode.POLYGON);

    sneakClick(3, 3);

    assertEquals(List.of(at(3, 3)), session.definingBlocks());
  }

  @Test
  void aSneakClickDropsAPickedPoint() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    click(10, 0);

    sneakClick(5, -3);

    assertNull(session.getPicked());
    assertEquals(4, session.getPointCount());
  }

  @Test
  void aClickInAnotherWorldStartsThePolygonOver() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    alex.nextComponentMessage();
    Location nether = new Location(scenario.nether(), 3, 64, 3);

    EditActions.click(alex, nether, false);

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(List.of(nether), session.definingBlocks());
  }

  @Test
  void aSneakClickInAnotherWorldStartsThePolygonOverToo() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    Location nether = new Location(scenario.nether(), 3, 64, 3);

    EditActions.click(alex, nether, true);

    assertEquals(List.of(nether), session.definingBlocks());
  }

  @Test
  void aClickInAnotherWorldMovesARectangleThere() {
    EditSession session = draw(Mode.RECT);
    click(0, 0);
    sneakClick(5, 5);
    Location nether = new Location(scenario.nether(), 1, 64, 1);

    EditActions.click(alex, nether, false);

    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());
    assertEquals(List.of(nether), session.definingBlocks());
    assertNull(session.toGeometry());
  }

  @Test
  void aClickThatDoesNotCompleteTheShapeNeverTouchesTheStore() {
    draw(Mode.RECT);
    click(0, 0);
    sneakClick(5, 5);

    assertTrue(scenario.store().getAreas().isEmpty());
    assertNotNull(MapToolsModule.sessions.get(alex.getUniqueId()));
  }

  @Test
  void aDrawnShapeIsNotMarkedAsChangedBecauseItIsNotSavedYet() {
    EditSession session = draw(Mode.RECT);

    click(0, 0);
    sneakClick(5, 5);

    assertFalse(session.hasChanges());
  }
}
