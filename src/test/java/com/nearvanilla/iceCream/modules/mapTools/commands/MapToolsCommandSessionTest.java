package com.nearvanilla.iceCream.modules.mapTools.commands;

import static com.nearvanilla.iceCream.testutil.CommandTestUtils.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditActions;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand.Kind;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** The commands that start, leave and save drawing and editing, and manage or delete areas. */
class MapToolsCommandSessionTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String PLAYERS_ONLY = "This command can only be used by players.";
  private static final String NOTHING_TO_SAVE =
      "Nothing to save. Draw with /maptools create claim or create marker, or edit in /maptools"
          + " editmode.";

  private Scenario scenario;
  private DialogPlayer alex;
  private ConsoleCommandSenderMock console;
  private final MapToolsCommand command = new MapToolsCommand();

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    console = scenario.server().getConsoleSender();
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private MapArea store(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private CommandSourceStack source() {
    return stack(alex);
  }

  private EditSession session() {
    return MapToolsModule.sessions.get(alex.getUniqueId());
  }

  private void edit(EditSession session) {
    MapToolsUtils.startEditing(alex, session);
  }

  private String next() {
    return MessageTestUtils.nextMessage(alex);
  }

  /** A session editing "home" that has an unsaved change. */
  private EditSession changed() {
    EditSession session = EditSession.edit(scenario.store().getArea("home"));
    session.setChanged(true);
    edit(session);
    return session;
  }

  private Location block(int x, int z) {
    return scenario.overworldBlock(x, 64, z);
  }

  // Players only

  static Stream<Arguments> playerCommands() {
    MapToolsCommand command = new MapToolsCommand();
    return Stream.of(
        command("stop", command::stop),
        command("editMode", command::editMode),
        command("createRectangle", command::createRectangle),
        command("createCircle", command::createCircle),
        command("createPolygon", command::createPolygon),
        command("createMarker", command::createMarker),
        command("save", command::save),
        command("undo", command::undo),
        command("redo", command::redo),
        command("manageEdited", command::manageEdited),
        command("manage", source -> command.manage(source, Kind.CLAIM, "home")),
        command("teleport", source -> command.teleport(source, Kind.CLAIM, "home")));
  }

  private static Arguments command(String name, Consumer<CommandSourceStack> action) {
    return Arguments.of(name, action);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("playerCommands")
  void theConsoleCannotRunACommandThatNeedsAPlayer(
      String name, Consumer<CommandSourceStack> action) {
    action.accept(stack(console));

    assertEquals(PLAYERS_ONLY, MessageTestUtils.nextMessage(console));
    assertNull(console.nextMessage());
  }

  // Stopping

  @Test
  void stopLeavesEditMode() {
    edit(EditSession.view());

    command.stop(source());

    assertFalse(MapToolsUtils.isEditing(alex));
    assertEquals("Stopped.", next());
    assertNull(alex.nextMessage());
  }

  @Test
  void stopWhileNotEditingExplainsHowToStart() {
    command.stop(source());

    assertEquals(
        "Use /maptools create claim or create marker to draw something new, or /maptools editmode"
            + " to change it.",
        next());
  }

  @Test
  void stopWithUnsavedChangesAsksFirst() {
    EditSession session = changed();

    command.stop(source());

    assertEquals("Unsaved changes to home", alex.lastDialog().title());
    assertSame(session, session());
    assertNull(alex.nextMessage());
  }

  @Test
  void discardingTheChangesCompletesTheStop() {
    changed();
    command.stop(source());
    ShownDialog question = alex.lastDialog();

    question.click("Discard", question.responseWithInitialValues(), alex);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertEquals("Stopped.", next());
  }

  @Test
  void cancellingTheQuestionKeepsEditing() {
    EditSession session = changed();
    command.stop(source());
    ShownDialog question = alex.lastDialog();

    question.clickExit(question.responseWithInitialValues(), alex);

    assertSame(session, session());
    assertTrue(session.hasChanges());
  }

  // Edit mode

  @Test
  void editModeStartsViewingAreas() {
    command.editMode(source());

    assertTrue(session().isViewing());
    assertNull(alex.nextMessage());
  }

  @Test
  void editModeAgainWhileViewingStopsIt() {
    edit(EditSession.view());

    command.editMode(source());

    assertFalse(MapToolsUtils.isEditing(alex));
    assertEquals("Stopped.", next());
  }

  @Test
  void editModeWhileDrawingANewShapeGoesBackToViewing() {
    edit(EditSession.create(Mode.RECT));

    command.editMode(source());

    assertTrue(session().isViewing());
    assertNull(alex.nextMessage());
  }

  @Test
  void editModeWhileEditingAnAreaGoesBackToViewingInsteadOfStopping() {
    edit(EditSession.edit(scenario.store().getArea("home")));

    command.editMode(source());

    assertTrue(session().isViewing());
    assertNull(alex.nextMessage());
  }

  @Test
  void editModeWithUnsavedChangesAsksAndThenGoesBackToViewing() {
    changed();
    command.editMode(source());
    assertEquals("Unsaved changes to home", alex.lastDialog().title());
    ShownDialog question = alex.lastDialog();

    question.click("Discard", question.responseWithInitialValues(), alex);

    assertTrue(session().isViewing());
    assertNull(alex.nextMessage());
  }

  // Drawing new shapes

  @Test
  void theRectangleAndCircleCommandsOpenAFormToTypeCoordinatesIn() {
    command.createRectangle(source());
    assertEquals("New rectangle claim", alex.lastDialog().title());

    command.createCircle(source());
    assertEquals("New circle claim", alex.lastDialog().title());
    assertEquals(2, alex.dialogs().size());
  }

  @Test
  void createPolygonStartsDrawingWithInstructions() {
    command.createPolygon(source());

    assertEquals(Mode.POLYGON, session().getMode());
    assertTrue(session().isNew());
    assertEquals(
        "Polygon: click to add points, click a point to move it, shift + click to remove or"
            + " insert. /maptools save when done.",
        next());
  }

  @Test
  void createMarkerStartsPlacingAMarkerWithInstructions() {
    command.createMarker(source());

    assertEquals(Mode.MARKER, session().getMode());
    assertEquals("Marker: click where the marker goes. /maptools save when done.", next());
  }

  @Test
  void startingToDrawReplacesWhatWasBeingDrawn() {
    edit(EditSession.create(Mode.RECT));

    command.createMarker(source());

    assertEquals(Mode.MARKER, session().getMode());
  }

  @Test
  void startingToDrawWithUnsavedChangesAsksFirst() {
    EditSession editing = changed();

    command.createPolygon(source());

    assertSame(editing, session());
    assertEquals("Unsaved changes to home", alex.lastDialog().title());
    assertNull(alex.nextMessage());
  }

  @Test
  void afterDiscardingTheChangesTheNewShapeIsStarted() {
    changed();
    command.createMarker(source());
    ShownDialog question = alex.lastDialog();

    question.click("Discard", question.responseWithInitialValues(), alex);

    assertEquals(Mode.MARKER, session().getMode());
    assertTrue(session().isNew());
    assertEquals("Marker: click where the marker goes. /maptools save when done.", next());
  }

  // Saving

  @Test
  void saveWithoutASessionHasNothingToSave() {
    command.save(source());

    assertEquals(NOTHING_TO_SAVE, next());
  }

  @Test
  void saveWhileOnlyViewingHasNothingToSave() {
    edit(EditSession.view());

    command.save(source());

    assertEquals(NOTHING_TO_SAVE, next());
  }

  @Test
  void saveBeforeTheShapeIsCompleteHasNothingToSave() {
    EditSession session = EditSession.create(Mode.RECT);
    edit(session);
    EditActions.click(alex, block(5, 5), false);

    command.save(source());

    assertEquals(NOTHING_TO_SAVE, next());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void saveOpensTheFormForACompletedNewClaim() {
    edit(EditSession.create(Mode.RECT));
    EditActions.click(alex, block(5, 5), false);
    EditActions.click(alex, block(15, 25), true);

    command.save(source());

    assertEquals("New claim", alex.lastDialog().title());
  }

  @Test
  void saveOpensTheFormForAPlacedMarker() {
    edit(EditSession.create(Mode.MARKER));
    EditActions.click(alex, block(5, 5), false);

    command.save(source());

    assertEquals("New marker", alex.lastDialog().title());
  }

  @Test
  void saveWhileEditingWithoutChangesSaysThereAreNone() {
    edit(EditSession.edit(scenario.store().getArea("home")));

    command.save(source());

    assertEquals("There are no changes to save.", next());
  }

  @Test
  void saveWhileEditingSavesTheChangedShape() {
    MapArea pond = store(MapToolsTestUtils.circle("pond", WORLD, 1000, 1000, 10));
    EditSession session = EditSession.edit(pond);
    session.setRadius(25);
    session.setChanged(true);
    edit(session);

    command.save(source());

    assertEquals("Saved the changes to pond.", next());
    assertEquals(25, scenario.store().getArea("pond").radius());
    assertTrue(session().isViewing());
  }

  // Undo and redo

  @Test
  void undoAndRedoWithoutAShapeSayYouAreNotDrawing() {
    command.undo(source());
    assertEquals("You are not drawing or editing a shape.", next());

    edit(EditSession.view());
    command.redo(source());
    assertEquals("You are not drawing or editing a shape.", next());
  }

  @Test
  void undoTakesBackTheLastClickAndRedoPutsItBack() {
    EditSession session = EditSession.create(Mode.POLYGON);
    edit(session);
    EditActions.click(alex, block(5, 5), false);
    EditActions.click(alex, block(15, 5), false);
    assertEquals(2, session.getPointCount());

    command.undo(source());
    assertEquals(1, session.getPointCount());

    command.redo(source());
    assertEquals(2, session.getPointCount());
    assertNull(alex.nextMessage());
  }

  @Test
  void undoWithNothingToUndoSaysSo() {
    edit(EditSession.create(Mode.POLYGON));

    command.undo(source());

    assertEquals("Nothing to undo.", next());
  }

  @Test
  void redoWithNothingToRedoSaysSo() {
    edit(EditSession.create(Mode.POLYGON));

    command.redo(source());

    assertEquals("Nothing to redo.", next());
  }

  // Managing

  @Test
  void manageWhileEditingOpensTheFormOfThatArea() {
    edit(EditSession.edit(scenario.store().getArea("home")));

    command.manageEdited(source());

    assertEquals("Manage claim home", alex.lastDialog().title());
  }

  @Test
  void manageWhileNotEditingSaysHowToChooseAnArea() {
    command.manageEdited(source());

    assertEquals("Use /maptools manage claim|marker <id>, or run this while editing.", next());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void manageWhileOnlyViewingSaysHowToChooseAnArea() {
    edit(EditSession.view());

    command.manageEdited(source());

    assertEquals("Use /maptools manage claim|marker <id>, or run this while editing.", next());
  }

  @Test
  void manageWhileEditingAnAreaThatWasDeletedSaysHowToChooseAnArea() {
    edit(EditSession.edit(scenario.store().getArea("home")));
    scenario.store().removeArea("home");

    command.manageEdited(source());

    assertEquals("Use /maptools manage claim|marker <id>, or run this while editing.", next());
  }

  @Test
  void manageAClaimByIdOpensItsForm() {
    command.manage(source(), Kind.CLAIM, "home");

    assertEquals("Manage claim home", alex.lastDialog().title());
  }

  @Test
  void manageAMarkerByIdOpensItsForm() {
    command.manage(source(), Kind.MARKER, "spot");

    assertEquals("Manage marker spot", alex.lastDialog().title());
  }

  @Test
  void manageAnUnknownIdSaysSo() {
    command.manage(source(), Kind.CLAIM, "nobody");

    assertEquals("There is no claim called nobody.", next());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void manageAClaimAsAMarkerSaysThereIsNoSuchMarker() {
    command.manage(source(), Kind.MARKER, "home");

    assertEquals("There is no marker called home.", next());
    assertTrue(alex.dialogs().isEmpty());
  }

  // Deleting

  @Test
  void deletingAnUnknownIdSaysSo() {
    command.delete(source(), Kind.CLAIM, "nobody");

    assertEquals("There is no claim called nobody.", next());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void deletingAClaimAsAMarkerSaysThereIsNoSuchMarker() {
    command.delete(source(), Kind.MARKER, "home");

    assertEquals("There is no marker called home.", next());
    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void aPlayerConfirmsADeletionInADialog() {
    command.delete(source(), Kind.CLAIM, "home");

    assertEquals("Delete home?", alex.lastDialog().title());
    assertNotNull(scenario.store().getArea("home"));
    assertNull(alex.nextMessage());
  }

  @Test
  void confirmingTheDialogDeletesTheClaim() {
    command.delete(source(), Kind.CLAIM, "home");
    ShownDialog question = alex.lastDialog();

    question.click("Delete", question.responseWithInitialValues(), alex);

    assertNull(scenario.store().getArea("home"));
    assertEquals("Deleted home (Label home). rect 10 10 19 19 in minecraft:overworld", next());
  }

  @Test
  void theConsoleDeletesRightAway() {
    command.delete(stack(console), Kind.CLAIM, "home");

    assertNull(scenario.store().getArea("home"));
    assertEquals(
        "Deleted home (Label home). rect 10 10 19 19 in minecraft:overworld",
        MessageTestUtils.nextMessage(console));
  }

  @Test
  void theConsoleDeletesAMarkerWithItsPosition() {
    command.delete(stack(console), Kind.MARKER, "spot");

    assertNull(scenario.store().getArea("spot"));
    assertEquals(
        "Deleted spot (Label spot). marker 3 3 in minecraft:overworld",
        MessageTestUtils.nextMessage(console));
  }
}
