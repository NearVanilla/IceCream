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
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.io.IOException;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Undoing, redoing and saving the changes made to a shape. */
class EditActionsApplyTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String NOT_DRAWING = "You are not drawing or editing a shape.";

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

  private void click(int x, int z) {
    EditActions.click(alex, at(x, z), false);
  }

  private EditSession draw(Mode mode) {
    EditSession session = EditSession.create(mode);
    MapToolsUtils.startEditing(alex, session);
    return session;
  }

  private EditSession edit(MapArea area) {
    scenario.store().putArea(area);
    assertTrue(MapToolsUtils.tryEditing(alex, area));
    return session();
  }

  /** Moves the corner at 0, 0 of a 0..9 square to the given block, as two clicks do. */
  private void moveFirstCorner(int x, int z) {
    click(0, 0);
    click(x, z);
  }

  private MapAreasStore reloaded() throws IOException {
    MapAreasStore fresh = new MapAreasStore(MapToolsTestUtils.storeFile());
    fresh.load();
    return fresh;
  }

  @Test
  void undoWithoutASessionSaysSo() {
    EditActions.undo(alex);

    assertEquals(NOT_DRAWING, MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void redoWithoutASessionSaysSo() {
    EditActions.redo(alex);

    assertEquals(NOT_DRAWING, MessageTestUtils.nextMessage(alex));
  }

  @Test
  void undoAndRedoWhileOnlyViewingSaySo() {
    MapToolsUtils.startEditing(alex, EditSession.view());

    EditActions.undo(alex);
    EditActions.redo(alex);

    assertEquals(NOT_DRAWING, MessageTestUtils.nextMessage(alex));
    assertEquals(NOT_DRAWING, MessageTestUtils.nextMessage(alex));
  }

  @Test
  void undoWithNothingDoneSaysSo() {
    draw(Mode.POLYGON);

    EditActions.undo(alex);

    assertEquals("Nothing to undo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void redoWithNothingUndoneSaysSo() {
    draw(Mode.POLYGON);
    click(0, 0);

    EditActions.redo(alex);

    assertEquals("Nothing to redo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void undoTakesBackTheLastClick() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    alex.nextComponentMessage();

    EditActions.undo(alex);

    assertEquals(List.of(at(0, 0), at(10, 0)), session.definingBlocks());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void undoCanTakeBackEveryClick() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    click(10, 10);
    // The third point completes the polygon, which shows the hint about saving it.
    alex.nextComponentMessage();

    EditActions.undo(alex);
    EditActions.undo(alex);
    EditActions.undo(alex);

    assertEquals(0, session.getPointCount());
    assertNull(alex.nextComponentMessage());
    EditActions.undo(alex);
    assertEquals("Nothing to undo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void redoPutsTheClickBack() {
    EditSession session = draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    EditActions.undo(alex);

    EditActions.redo(alex);

    assertEquals(List.of(at(0, 0), at(10, 0)), session.definingBlocks());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aNewClickAfterAnUndoMakesRedoImpossible() {
    draw(Mode.POLYGON);
    click(0, 0);
    click(10, 0);
    EditActions.undo(alex);
    click(5, 5);

    EditActions.redo(alex);

    assertEquals("Nothing to redo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aClickThatChangedNothingIsNotRemembered() {
    draw(Mode.POLYGON);
    click(0, 0);
    // The second click only picks the point up, which is not a change of the shape.
    click(0, 0);

    EditActions.undo(alex);
    EditActions.undo(alex);

    assertEquals("Nothing to undo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void undoPutsAMovedCornerBackAndClearsTheUnsavedChanges() {
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    assertTrue(session.hasChanges());

    EditActions.undo(alex);

    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), session.toGeometry().points());
    assertFalse(session.hasChanges());
  }

  @Test
  void redoMovesTheCornerAgainAndBringsBackTheUnsavedChanges() {
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.undo(alex);

    EditActions.redo(alex);

    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), session.toGeometry().points());
    assertTrue(session.hasChanges());
  }

  @Test
  void applyWithoutASessionSaysSo() {
    EditActions.apply(alex);

    assertEquals("You are not editing a claim.", MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void applyWhileViewingOrDrawingANewShapeSaysSo() {
    MapToolsUtils.startEditing(alex, EditSession.view());
    EditActions.apply(alex);
    draw(Mode.RECT);
    EditActions.apply(alex);

    assertEquals("You are not editing a claim.", MessageTestUtils.nextMessage(alex));
    assertEquals("You are not editing a claim.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void applyWithoutChangesSaysThereIsNothingToSave() {
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    EditActions.apply(alex);

    assertEquals("There are no changes to save.", MessageTestUtils.nextMessage(alex));
    assertSame(session, session());
  }

  @Test
  void applyOfAClaimThatWasDeletedSaysSo() {
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    scenario.store().removeArea("home");

    EditActions.apply(alex);

    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
    assertSame(session, session());
  }

  @Test
  void applyOfAClaimWhoseWorldIsNotLoadedSaysItIsGone() {
    MapArea ghost = MapToolsTestUtils.rect("ghost", "minecraft:missing_world", 0, 0, 9, 9);
    scenario.store().putArea(ghost);
    EditSession session = EditSession.edit(ghost);
    session.setChanged(true);
    MapToolsUtils.startEditing(alex, session);

    EditActions.apply(alex);

    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
    assertSame(ghost, scenario.store().getArea("ghost"));
  }

  @Test
  void applySavesTheNewShapeAndKeepsTheDetails() throws IOException {
    MapArea home =
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)
            .withLabel("Home")
            .withOwners(List.of("Alex"))
            .withColor("red");
    edit(home);
    moveFirstCorner(-5, -5);

    EditActions.apply(alex);

    MapArea saved = scenario.store().getArea("home");
    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), saved.points());
    assertEquals("Home", saved.label());
    assertEquals(List.of("Alex"), saved.owners());
    assertEquals("red", saved.color());
    assertEquals(saved.points(), reloaded().getArea("home").points());
  }

  @Test
  void applyTellsThePlayerAndGoesBackToViewing() {
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);

    EditActions.apply(alex);

    assertEquals("Saved the changes to home.", MessageTestUtils.nextMessage(alex));
    assertTrue(session().isViewing());
    assertFalse(session.hasChanges());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void applySavesAMovedCircle() {
    EditSession session = edit(MapToolsTestUtils.circle("pond", WORLD, 0, 0, 10));
    EditActions.click(alex, at(40, 0), true);
    assertEquals(40, session.getRadius());

    EditActions.apply(alex);

    MapArea saved = scenario.store().getArea("pond");
    assertEquals(40.0, saved.radius());
    assertEquals(MapToolsUtils.circle(0.5, 0.5, 40), saved.points());
    assertEquals(MapArea.CIRCLE, saved.shape());
  }

  @Test
  void applySavesAMovedMarkerWithoutAskingAboutNearbyClaims() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 8, 8, 20, 20));
    edit(MapToolsTestUtils.marker("spot", WORLD, 5, 5));
    click(5, 5);
    click(9, 9);

    EditActions.apply(alex);

    assertNull(alex.lastDialog());
    assertEquals(MapToolsUtils.blockCenter(9, 9), scenario.store().getArea("spot").center());
  }

  @Test
  void applyAsksBeforeSavingAClaimThatIsCloseToAnother() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);

    EditActions.apply(alex);

    ShownDialog dialog = alex.lastDialog();
    assertNotNull(dialog);
    assertEquals("Claims nearby", dialog.title());
    assertTrue(dialog.buttons().contains("Save anyway"));
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), scenario.store().getArea("home").points());
    assertTrue(session().hasChanges());
  }

  @Test
  void savingAnywaySavesTheClaim() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), scenario.store().getArea("home").points());
    assertTrue(session().isViewing());
    assertEquals("Saved the changes to home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void cancellingTheNearbyQuestionSavesNothing() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);

    alex.lastDialog().clickExit(new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), scenario.store().getArea("home").points());
    assertSame(session, session());
    assertTrue(session.hasChanges());
  }

  @Test
  void applyWithoutTheNearbyCheckSavesAtOnce() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);

    EditActions.apply(alex, false);

    assertNull(alex.lastDialog());
    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), scenario.store().getArea("home").points());
  }

  @Test
  void aClaimFarFromEveryOtherClaimIsSavedWithoutAsking() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 1000, 0, 1009, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);

    EditActions.apply(alex);

    assertNull(alex.lastDialog());
    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), scenario.store().getArea("home").points());
  }

  @Test
  void aClaimIsNotCloseToItself() {
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);

    EditActions.apply(alex);

    assertNull(alex.lastDialog());
  }

  @Test
  void ifTheClaimIsDeletedWhileTheQuestionIsOpenNothingIsSaved() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);
    scenario.store().removeArea("home");

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertNull(scenario.store().getArea("home"));
    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void ifThePlayerMovedOnWhileTheQuestionIsOpenNothingIsSaved() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);
    EditSession other = EditSession.view();
    MapToolsUtils.startEditing(alex, other);
    alex.nextComponentMessage();

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), scenario.store().getArea("home").points());
    assertSame(other, session());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void theDetailsOfTheClaimAsItIsWhenAnsweringAreKept() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);
    // Someone changes the label while the question is open.
    scenario.store().putArea(scenario.store().getArea("home").withLabel("Renamed"));

    alex.lastDialog().click("Save anyway", new Response(), alex);

    MapArea saved = scenario.store().getArea("home");
    assertEquals("Renamed", saved.label());
    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), saved.points());
  }

  @Test
  void theShapeSavedIsTheOneFromWhenTheQuestionWasAsked() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 100, 0, 109, 9));
    EditSession session = edit(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    moveFirstCorner(-5, -5);
    EditActions.apply(alex);
    // The session is still the same one, so the player can keep drawing under the question.
    click(-5, -5);
    click(-30, -30);
    assertEquals(MapToolsUtils.rectangle(-30, -30, 9, 9), session.toGeometry().points());

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), scenario.store().getArea("home").points());
  }
}
