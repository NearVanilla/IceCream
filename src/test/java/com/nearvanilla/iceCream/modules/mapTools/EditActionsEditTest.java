package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Clicking blocks while editing a saved area: picking corners up, moving them and leaving. */
class EditActionsEditTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

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

  private EditSession session() {
    return MapToolsModule.sessions.get(alex.getUniqueId());
  }

  // Corners of a saved area stand at sea level, so blocks are compared by their x and z.
  private static List<String> xz(EditSession session) {
    return session.definingBlocks().stream()
        .map(block -> block.getBlockX() + "," + block.getBlockZ())
        .toList();
  }

  private EditSession edit(MapArea area) {
    scenario.store().putArea(area);
    assertTrue(MapToolsUtils.tryEditing(alex, area));
    return session();
  }

  private void click(int x, int z) {
    EditActions.click(alex, at(x, z), false);
  }

  private void sneakClick(int x, int z) {
    EditActions.click(alex, at(x, z), true);
  }

  private EditSession editSquare() {
    return edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
  }

  private EditSession editTriangle() {
    return edit(MapToolsTestUtils.polygon("shape", WORLD, 0.5, 0.5, 10.5, 0.5, 10.5, 10.5));
  }

  private EditSession editCircle() {
    return edit(MapToolsTestUtils.circle("pond", WORLD, 0, 0, 10));
  }

  private EditSession editMarker() {
    return edit(MapToolsTestUtils.marker("spot", WORLD, 5, 5));
  }

  @Test
  void clickingACornerOfARectanglePicksItUp() {
    EditSession session = editSquare();

    click(9, 0);

    assertEquals(1, session.getPicked());
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingAwayWithACornerPickedMovesItAndKeepsTheOppositeCorner() {
    EditSession session = editSquare();
    click(0, 0);

    click(-5, -5);

    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), session.toGeometry().points());
    assertTrue(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void anyCornerOfARectangleCanBeMoved() {
    EditSession session = editSquare();

    click(9, 9);
    click(20, 20);
    assertEquals(MapToolsUtils.rectangle(0, 0, 20, 20), session.toGeometry().points());

    // The corner at the top of the new rectangle is at 20, 0.
    click(20, 0);
    click(25, -5);
    assertEquals(MapToolsUtils.rectangle(0, 20, 25, -5), session.toGeometry().points());
  }

  @Test
  void clickingAnotherCornerWhileOneIsPickedSwitchesThePick() {
    EditSession session = editSquare();
    click(0, 0);

    click(9, 9);

    assertEquals(2, session.getPicked());
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingInsideTheRectangleWithNothingPickedDoesNothing() {
    EditSession session = editSquare();

    click(5, 5);

    assertSame(session, session());
    assertFalse(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void sneakClicksDoNothingToARectangle() {
    EditSession session = editSquare();

    sneakClick(0, 0);
    sneakClick(50, 50);
    click(0, 0);
    sneakClick(-5, -5);

    assertSame(session, session());
    assertFalse(session.hasChanges());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), session.toGeometry().points());
  }

  @Test
  void clickingJustOutsideTheClaimKeepsEditingIt() {
    EditSession session = editSquare();

    // The block center is 7.5 blocks from the edge.
    click(17, 5);
    click(-8, 5);
    click(5, 17);
    click(5, -8);

    assertSame(session, session());
  }

  @Test
  void clickingFarOutsideTheClaimStopsEditingIt() {
    editSquare();

    // The block center is 8.5 blocks from the edge.
    click(18, 5);

    assertTrue(session().isViewing());
  }

  @Test
  void clickingFarOutsideOnEverySideStopsEditingIt() {
    for (int[] spot : new int[][] {{18, 5}, {-9, 5}, {5, 18}, {5, -9}, {18, 18}, {-9, -9}}) {
      editSquare();

      click(spot[0], spot[1]);

      assertTrue(session().isViewing(), "click at " + spot[0] + ", " + spot[1]);
    }
  }

  @Test
  void clickingFarOutsideWithACornerPickedMovesTheCornerThereInstead() {
    EditSession session = editSquare();
    click(9, 9);

    click(50, 50);

    assertSame(session, session());
    assertEquals(MapToolsUtils.rectangle(0, 0, 50, 50), session.toGeometry().points());
  }

  @Test
  void clickingFarOutsideAskedAboutUnsavedChangesFirst() {
    EditSession session = editSquare();
    click(0, 0);
    click(-5, -5);

    click(50, 50);

    assertSame(session, session());
    assertEquals("Unsaved changes to home", alex.lastDialog().title());
  }

  @Test
  void discardingTheChangesThenGoesBackToViewing() {
    EditSession session = editSquare();
    click(0, 0);
    click(-5, -5);
    click(50, 50);

    alex.lastDialog().click("Discard", new Response(), alex);

    assertTrue(session().isViewing());
    assertFalse(session.hasChanges());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), scenario.store().getArea("home").points());
  }

  @Test
  void cancellingTheQuestionKeepsEditing() {
    EditSession session = editSquare();
    click(0, 0);
    click(-5, -5);
    click(50, 50);

    alex.lastDialog().clickExit(new Response(), alex);

    assertSame(session, session());
    assertTrue(session.hasChanges());
  }

  @Test
  void clickingInsideAnotherAreaSwitchesToEditingThatOne() {
    editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));

    click(25, 5);

    assertEquals("shop", session().getAreaId());
  }

  @Test
  void clickingInsideAnOverlappingAreaSwitchesToThatArea() {
    editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 5, 5, 20, 20));

    click(7, 7);

    assertEquals("shop", session().getAreaId());
  }

  @Test
  void clickingInsideTheAreaBeingEditedDoesNotSwitch() {
    EditSession session = editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));

    click(5, 5);

    assertSame(session, session());
  }

  @Test
  void clickingInsideAnotherAreaWithACornerPickedMovesTheCornerThere() {
    EditSession session = editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    click(9, 9);

    click(25, 5);

    assertSame(session, session());
    assertEquals(MapToolsUtils.rectangle(0, 0, 25, 5), session.toGeometry().points());
  }

  @Test
  void clickingACornerThatLiesInsideAnotherAreaPicksTheCornerUp() {
    EditSession session = editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 0, 0, 20, 20));

    click(9, 9);

    assertSame(session, session());
    assertEquals(2, session.getPicked());
  }

  @Test
  void switchingAwayFromUnsavedChangesAsksFirst() {
    EditSession session = editSquare();
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    click(0, 0);
    click(-5, -5);

    click(25, 5);

    assertSame(session, session());
    assertEquals("Unsaved changes to home", alex.lastDialog().title());
  }

  @Test
  void aClaimInAnotherWorldCannotBeEditedFromHere() {
    EditSession session = editSquare();

    EditActions.click(alex, new Location(scenario.nether(), 0, 64, 0), false);

    assertEquals("This claim is in another world.", MessageTestUtils.nextMessage(alex));
    assertSame(session, session());
    assertFalse(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void clickingACornerOfAPolygonPicksItAndClickingAwayMovesIt() {
    EditSession session = editTriangle();

    click(10, 0);
    assertEquals(1, session.getPicked());
    assertFalse(session.hasChanges());
    click(20, -5);

    assertEquals(List.of("0,0", "20,-5", "10,10"), xz(session));
    assertTrue(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void clickingInsideAPolygonWithNothingPickedDoesNothing() {
    EditSession session = editTriangle();

    click(8, 3);

    assertFalse(session.hasChanges());
    assertEquals(3, session.getPointCount());
  }

  @Test
  void aSneakClickSplitsTheNearestEdgeOfAPolygon() {
    EditSession session = editTriangle();

    sneakClick(5, -3);

    assertEquals(List.of("0,0", "5,-3", "10,0", "10,10"), xz(session));
    assertTrue(session.hasChanges());
  }

  @Test
  void aSneakClickOnAPointRemovesItWhenThereAreMoreThanThree() {
    EditSession session = editTriangle();
    sneakClick(5, -3);

    sneakClick(5, -3);

    assertEquals(List.of("0,0", "10,0", "10,10"), xz(session));
    assertTrue(session.hasChanges());
  }

  @Test
  void aPolygonCannotLoseItsThirdPoint() {
    EditSession session = editTriangle();

    sneakClick(10, 0);

    assertEquals("A polygon needs at least 3 points.", MessageTestUtils.nextMessage(alex));
    assertEquals(3, session.getPointCount());
    assertFalse(session.hasChanges());
  }

  @Test
  void aSneakClickDropsAPickedPointOfAPolygon() {
    EditSession session = editTriangle();
    click(10, 0);

    sneakClick(5, -3);

    assertNull(session.getPicked());
    assertEquals(4, session.getPointCount());
  }

  @Test
  void clickingFarFromAPolygonStopsEditingIt() {
    editTriangle();

    click(40, 40);

    assertTrue(session().isViewing());
  }

  @Test
  void aSneakClickFarFromAPolygonAddsAPointInsteadOfLeaving() {
    EditSession session = editTriangle();

    sneakClick(40, 40);

    assertSame(session, session());
    assertEquals(4, session.getPointCount());
  }

  @Test
  void aSneakClickSetsTheRadiusOfACircle() {
    EditSession session = editCircle();

    sneakClick(0, 25);

    assertEquals(25, session.getRadius());
    assertTrue(session.hasChanges());
    assertEquals(25.0, session.toGeometry().radius());
  }

  @Test
  void theRadiusOfACircleIsNeverBelowOne() {
    EditSession session = editCircle();

    sneakClick(0, 0);

    assertEquals(1, session.getRadius());
  }

  @Test
  void aSneakClickFarAwayEnlargesACircleInsteadOfLeaving() {
    EditSession session = editCircle();

    sneakClick(300, 400);

    assertSame(session, session());
    assertEquals(500, session.getRadius());
  }

  @Test
  void clickingTheCenterOfACirclePicksItAndClickingAwayMovesIt() {
    EditSession session = editCircle();

    click(0, 0);
    assertEquals(0, session.getPicked());
    click(30, -4);

    assertEquals(at(30, -4), session.getCenter());
    assertEquals(10, session.getRadius());
    assertTrue(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void clickingInsideACircleWithNothingPickedDoesNothing() {
    EditSession session = editCircle();

    click(3, 3);

    assertFalse(session.hasChanges());
    assertEquals(List.of("0,0"), xz(session));
  }

  @Test
  void clickingFarFromACircleStopsEditingIt() {
    editCircle();

    click(30, 0);

    assertTrue(session().isViewing());
  }

  @Test
  void clickingNearACircleKeepsEditingIt() {
    EditSession session = editCircle();

    click(17, 0);

    assertSame(session, session());
  }

  @Test
  void clickingAMarkerPicksItUpAndClickingAwayMovesIt() {
    EditSession session = editMarker();

    click(5, 5);
    assertEquals(0, session.getPicked());
    click(8, 9);

    assertEquals(at(8, 9), session.getCenter());
    assertTrue(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void sneakClicksDoNothingToAMarker() {
    EditSession session = editMarker();

    sneakClick(5, 5);
    sneakClick(30, 30);
    click(5, 5);
    sneakClick(9, 9);

    assertSame(session, session());
    assertEquals(List.of("5,5"), xz(session));
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingAwayFromAMarkerWithNothingPickedDoesNothingWhenNear() {
    EditSession session = editMarker();

    click(12, 5);

    assertSame(session, session());
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingFarFromAMarkerStopsEditingIt() {
    editMarker();

    click(30, 5);

    assertTrue(session().isViewing());
  }

  @Test
  void clickingNothingPutsDownAPickedCorner() {
    EditSession session = editSquare();
    click(0, 0);

    EditActions.clickNothing(alex, false);

    assertNull(session.getPicked());
    assertSame(session, session());
    assertFalse(session.hasChanges());
  }

  @Test
  void clickingNothingWithNothingPickedStopsEditingTheArea() {
    editSquare();

    EditActions.clickNothing(alex, false);

    assertTrue(session().isViewing());
  }

  @Test
  void clickingNothingWithUnsavedChangesAsksFirst() {
    EditSession session = editSquare();
    click(0, 0);
    click(-5, -5);

    EditActions.clickNothing(alex, false);

    assertSame(session, session());
    assertEquals("Unsaved changes to home", alex.lastDialog().title());
  }

  @Test
  void sneakingAndClickingNothingDoesNothing() {
    EditSession session = editSquare();
    click(0, 0);

    EditActions.clickNothing(alex, true);

    assertEquals(0, session.getPicked());
    assertSame(session, session());
  }

  @Test
  void clickingNothingWhileDrawingOrViewingDoesNothing() {
    EditSession drawing = EditSession.create(EditSession.Mode.RECT);
    MapToolsUtils.startEditing(alex, drawing);
    EditActions.clickNothing(alex, false);
    assertSame(drawing, session());

    EditSession view = EditSession.view();
    MapToolsUtils.startEditing(alex, view);
    EditActions.clickNothing(alex, false);
    assertSame(view, session());
  }

  @Test
  void clickingNothingWithoutASessionDoesNothing() {
    EditActions.clickNothing(alex, false);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertNull(alex.nextComponentMessage());
  }
}
