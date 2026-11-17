package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The form that changes the details of a saved claim or marker. */
class AreaFormsManageTest {
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

  private MapArea stored(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private ShownDialog manage(MapArea area) {
    AreaForms.openManage(alex, area);
    return alex.lastDialog();
  }

  private void save(ShownDialog dialog, Response response) {
    dialog.click("Save", response, alex);
  }

  private MapArea home() {
    return stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
  }

  @Test
  void theClaimFormShowsTheCurrentValues() {
    MapArea area =
        stored(
            MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)
                .withLabel("Home sweet home")
                .withOwners(List.of("Alex", "Sam"))
                .withDescription("A cozy place")
                .withOpacity(0.4)
                .withColor("red"));

    ShownDialog dialog = manage(area);

    assertEquals("Manage claim home", dialog.title());
    assertEquals("home", dialog.text("area_id").initial());
    assertEquals("Home sweet home", dialog.text("label").initial());
    assertEquals("Alex, Sam", dialog.text("owner").initial());
    assertEquals("A cozy place", dialog.text("description").initial());
    assertEquals(40f, dialog.number("opacity").initial());
    assertFalse(dialog.bool("hidden").initial());
    assertTrue(dialog.body().contains("Color: ■ red"));
  }

  @Test
  void aClaimWithoutDetailsShowsEmptyFieldsAndTheDefaultOpacity() {
    ShownDialog dialog = manage(home());

    assertEquals("", dialog.text("owner").initial());
    assertEquals("", dialog.text("description").initial());
    assertEquals(20f, dialog.number("opacity").initial());
  }

  @Test
  void aRectangleHasItsCornersInTheForm() {
    ShownDialog dialog = manage(home());

    assertEquals(
        List.of(
            "area_id",
            "coordinate0",
            "coordinate1",
            "label",
            "opacity",
            "owner",
            "description",
            "hidden"),
        dialog.inputKeys());
    assertEquals("0 0", dialog.text("coordinate0").initial());
    assertEquals("9 9", dialog.text("coordinate1").initial());
  }

  @Test
  void aCircleHasItsCenterAndRadiusInTheForm() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12)));

    assertEquals("5 -3", dialog.text("coordinate0").initial());
    assertEquals("12", dialog.text("coordinate1").initial());
  }

  @Test
  void aPolygonHasNoShapeFieldsBecauseItsPointsCanBeTooLong() {
    ShownDialog dialog =
        manage(stored(MapToolsTestUtils.polygon("shape", WORLD, 0.5, 0.5, 5.5, 0.5, 5.5, 5.5)));

    assertEquals(
        List.of("area_id", "label", "opacity", "owner", "description", "hidden"),
        dialog.inputKeys());
  }

  @Test
  void aMarkerHasNoOpacityNoHideBoxAndAnIconInsteadOfAColor() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.marker("spot", WORLD, 3, 3)));

    assertEquals("Manage marker spot", dialog.title());
    assertEquals(List.of("area_id", "label", "owner", "description"), dialog.inputKeys());
    assertEquals(List.of("[BARRIER] Icon: none, shown as a dot"), dialog.body());
    assertEquals(List.of("Pick icon", "Save", "Delete"), dialog.buttons());
  }

  @Test
  void theClaimFormHasTheColorPickerSaveAndDeleteButtons() {
    ShownDialog dialog = manage(home());

    assertEquals(List.of("Pick color", "Save", "Delete"), dialog.buttons());
    assertEquals("Cancel", dialog.exitLabel());
    assertEquals(2, dialog.columns());
  }

  @Test
  void aHiddenClaimHasTheBoxTicked() {
    ShownDialog dialog = manage(stored(home().withHidden(true)));

    assertTrue(dialog.bool("hidden").initial());
  }

  @Test
  void textThatIsTooLongForAFieldIsCutToFit() {
    ShownDialog dialog =
        manage(
            stored(
                home()
                    .withLabel("l".repeat(80))
                    .withDescription("d".repeat(300))
                    .withOwners(List.of("o".repeat(300)))));

    assertEquals(64, dialog.text("label").initial().length());
    assertEquals(256, dialog.text("description").initial().length());
    assertEquals(256, dialog.text("owner").initial().length());
  }

  @Test
  void savingWithoutChangingAnythingDoesNothing() {
    MapArea area = home();
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues());

    assertSame(area, scenario.store().getArea("home"));
    assertNull(alex.nextComponentMessage());
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void savingWithoutChangesDoesNothingForAnAreaThatWasLoadedFromTheFile() throws IOException {
    stored(home());
    MapAreasStore fromFile = new MapAreasStore(MapToolsTestUtils.storeFile());
    fromFile.load();
    MapToolsModule.store = fromFile;
    MapArea loaded = fromFile.getArea("home");
    ShownDialog dialog = manage(loaded);

    save(dialog, dialog.responseWithInitialValues());

    assertSame(loaded, fromFile.getArea("home"));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void anOpacityThatIsNotAWholePercentIsKeptWhenAnotherFieldChanges() {
    ShownDialog dialog = manage(stored(home().withOpacity(0.333)));

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals(0.333, scenario.store().getArea("home").opacity());
  }

  @Test
  void aColorChangedByAnotherPlayerWhileTheFormIsOpenIsKept() {
    ShownDialog dialog = manage(home());
    scenario.store().putArea(scenario.store().getArea("home").withColor("red"));

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals("red", scenario.store().getArea("home").color());
  }

  @Test
  void aDescriptionChangedByAnotherPlayerWhileTheFormIsOpenIsKept() {
    ShownDialog dialog = manage(home());
    scenario.store().putArea(scenario.store().getArea("home").withDescription("Theirs"));

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals("Theirs", scenario.store().getArea("home").description());
  }

  @Test
  void anOpacityChangedByAnotherPlayerWhileTheFormIsOpenIsKept() {
    ShownDialog dialog = manage(home());
    scenario.store().putArea(scenario.store().getArea("home").withOpacity(0.9));

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals(0.9, scenario.store().getArea("home").opacity());
  }

  @Test
  void aHiddenStateChangedByAnotherPlayerWhileTheFormIsOpenIsKept() {
    ShownDialog dialog = manage(home());
    scenario.store().putArea(scenario.store().getArea("home").withHidden(true));

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertTrue(scenario.store().getArea("home").isHidden());
  }

  @Test
  void savingWithTheSameTextWithSpacesAroundItDoesNothing() {
    MapArea area = stored(home().withLabel("Home").withOwners(List.of("Alex")));
    ShownDialog dialog = manage(area);

    save(
        dialog, dialog.responseWithInitialValues().text("label", "  Home ").text("owner", " Alex"));

    assertSame(area, scenario.store().getArea("home"));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aChangedLabelIsSaved() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("label", "  A new label "));

    assertEquals("A new label", scenario.store().getArea("home").label());
    assertEquals("Updated home.", MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aChangeKeepsEverythingElseOfTheClaim() {
    MapArea area =
        stored(
            home()
                .withOwners(List.of("Alex"))
                .withDescription("Text")
                .withColor("red")
                .withOpacity(0.5));
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals(area.withLabel("New"), scenario.store().getArea("home"));
  }

  @Test
  void theOwnersAreSplitOnCommas() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("owner", "Alex, Sam ,Kim"));

    assertEquals(List.of("Alex", "Sam", "Kim"), scenario.store().getArea("home").owners());
  }

  @Test
  void clearingTheOwnersRemovesThem() {
    ShownDialog dialog = manage(stored(home().withOwners(List.of("Alex", "Sam"))));

    save(dialog, dialog.responseWithInitialValues().text("owner", ""));

    assertEquals(List.of(), scenario.store().getArea("home").owners());
  }

  @Test
  void theDescriptionCanBeSetAndCleared() {
    ShownDialog dialog = manage(home());
    save(dialog, dialog.responseWithInitialValues().text("description", "Line one\nLine two"));
    assertEquals("Line one\nLine two", scenario.store().getArea("home").description());

    ShownDialog again = manage(scenario.store().getArea("home"));
    save(again, again.responseWithInitialValues().text("description", "  "));

    assertNull(scenario.store().getArea("home").description());
  }

  @Test
  void theOpacityCanBeChangedAndPutBackToTheDefault() {
    ShownDialog dialog = manage(home());
    save(dialog, dialog.responseWithInitialValues().number("opacity", 60f));
    assertEquals(0.6, scenario.store().getArea("home").opacity());

    ShownDialog again = manage(scenario.store().getArea("home"));
    assertEquals(60f, again.number("opacity").initial());
    save(again, again.responseWithInitialValues().number("opacity", 20f));

    assertNull(scenario.store().getArea("home").opacity());
  }

  @Test
  void aClaimCanBeHiddenAndShownAgain() {
    ShownDialog dialog = manage(home());
    save(dialog, dialog.responseWithInitialValues().bool("hidden", true));
    assertTrue(scenario.store().getArea("home").isHidden());

    ShownDialog again = manage(scenario.store().getArea("home"));
    save(again, again.responseWithInitialValues().bool("hidden", false));

    assertFalse(scenario.store().getArea("home").isHidden());
    assertNull(scenario.store().getArea("home").hidden());
  }

  @Test
  void aMarkerKeepsItsHiddenStateBecauseItsFormHasNoBox() {
    MapArea spot = stored(MapToolsTestUtils.marker("spot", WORLD, 3, 3).withHidden(true));
    ShownDialog dialog = manage(spot);

    save(dialog, dialog.responseWithInitialValues().text("label", "Renamed"));

    assertTrue(scenario.store().getArea("spot").isHidden());
    assertEquals("Renamed", scenario.store().getArea("spot").label());
  }

  @Test
  void aMarkerLabelCanBeChangedWithoutAnOpacityInTheResponse() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.marker("spot", WORLD, 3, 3)));

    save(dialog, dialog.responseWithInitialValues().text("label", "Renamed"));

    MapArea saved = scenario.store().getArea("spot");
    assertEquals("Renamed", saved.label());
    assertNull(saved.opacity());
  }

  @Test
  void onlyTheFieldsThePlayerChangedAreAppliedToTheClaimAsItIsNow() {
    ShownDialog dialog = manage(stored(home().withLabel("Old label")));
    // Somebody else renames the claim while the form is open.
    scenario.store().putArea(scenario.store().getArea("home").withLabel("Their label"));

    save(dialog, dialog.responseWithInitialValues().text("description", "My text"));

    MapArea saved = scenario.store().getArea("home");
    assertEquals("Their label", saved.label());
    assertEquals("My text", saved.description());
  }

  @Test
  void aFieldChangedByBothIsTakenFromThePlayer() {
    ShownDialog dialog = manage(stored(home().withLabel("Old label")));
    scenario.store().putArea(scenario.store().getArea("home").withLabel("Their label"));

    save(dialog, dialog.responseWithInitialValues().text("label", "My label"));

    assertEquals("My label", scenario.store().getArea("home").label());
  }

  @Test
  void aChangeSavedWhileTheFormIsOpenIsNotMistakenForOneOfThePlayer() {
    ShownDialog dialog = manage(stored(home().withOwners(List.of("Alex"))));
    scenario.store().putArea(scenario.store().getArea("home").withOwners(List.of("Sam")));

    // Only the label changes, so the owners saved by somebody else stay.
    save(dialog, dialog.responseWithInitialValues().text("label", "Label"));

    assertEquals(List.of("Sam"), scenario.store().getArea("home").owners());
  }

  @Test
  void anInvalidLabelOpensTheFormAgainWithTheError() {
    ShownDialog dialog = manage(home());

    save(
        dialog,
        dialog.responseWithInitialValues().text("label", "x".repeat(65)).text("owner", "Sam"));

    ShownDialog again = alex.lastDialog();
    assertEquals(2, alex.dialogs().size());
    assertEquals("Manage claim home", again.title());
    assertEquals("Labels can be up to 64 characters.", again.body().getFirst());
    assertEquals("x".repeat(64), again.text("label").initial());
    assertEquals("Sam", again.text("owner").initial());
    assertEquals("Label home", scenario.store().getArea("home").label());
  }

  @Test
  void anEmptyLabelOpensTheFormAgain() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("label", ""));

    assertEquals("The label cannot be empty.", alex.lastDialog().body().getFirst());
  }

  @Test
  void aFormThatWasOpenedAgainSavesTheChangesMadeSinceItWasFirstShown() {
    ShownDialog dialog = manage(home());
    save(dialog, dialog.responseWithInitialValues().text("label", "").text("owner", "Sam"));

    ShownDialog again = alex.lastDialog();
    save(again, again.responseWithInitialValues().text("label", "Fixed"));

    MapArea saved = scenario.store().getArea("home");
    assertEquals("Fixed", saved.label());
    assertEquals(List.of("Sam"), saved.owners());
  }

  @Test
  void theClaimBeingGoneWhenSavingSaysSo() {
    ShownDialog dialog = manage(home());
    scenario.store().removeArea("home");

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void anIdThatIsChangedRenamesTheClaim() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    assertNull(scenario.store().getArea("home"));
    assertEquals("Label home", scenario.store().getArea("house").label());
    assertEquals("Updated house.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aRenameCanChangeOtherFieldsToo() {
    ShownDialog dialog = manage(home());

    save(
        dialog, dialog.responseWithInitialValues().text("area_id", "house").text("label", "House"));

    MapArea saved = scenario.store().getArea("house");
    assertEquals("House", saved.label());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), saved.points());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aRenameKeepsThePlayerEditingTheClaimUnderItsNewId() {
    MapArea area = home();
    MapToolsUtils.tryEditing(alex, area);
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    assertEquals("house", MapToolsModule.sessions.get(alex.getUniqueId()).getAreaId());
  }

  @Test
  void aRenameToAnIdThatIsTakenOpensTheFormAgain() {
    stored(MapToolsTestUtils.rect("house", WORLD, 100, 100, 109, 109));
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    assertEquals("A claim with id house already exists.", alex.lastDialog().body().getFirst());
    assertEquals("house", alex.lastDialog().text("area_id").initial());
    assertNotNull(scenario.store().getArea("home"));
    assertEquals("Label house", scenario.store().getArea("house").label());
  }

  @Test
  void aRenameToAnInvalidIdOpensTheFormAgain() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("area_id", "My House"));

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        alex.lastDialog().body().getFirst());
    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void aClearedIdOpensTheFormAgain() {
    ShownDialog dialog = manage(home());

    save(dialog, dialog.responseWithInitialValues().text("area_id", ""));

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        alex.lastDialog().body().getFirst());
  }

  @Test
  void aRenameAfterTheFirstTryIsStillMeasuredAgainstTheClaimsRealId() {
    stored(MapToolsTestUtils.rect("house", WORLD, 100, 100, 109, 109));
    ShownDialog dialog = manage(home());
    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    ShownDialog again = alex.lastDialog();
    save(again, again.responseWithInitialValues().text("area_id", "home"));

    // Typing the original id again is no rename, and nothing else changed.
    assertEquals(2, alex.dialogs().size());
    assertNull(alex.nextComponentMessage());
    assertEquals("Label home", scenario.store().getArea("home").label());
  }

  @Test
  void aRenameIsRefusedWhileSomeoneElseEditsTheClaim() {
    MapArea area = home();
    DialogPlayer bob = scenario.editor("Bob");
    MapToolsUtils.tryEditing(bob, area);
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    assertEquals("Bob is editing this claim right now.", MessageTestUtils.nextMessage(alex));
    assertNotNull(scenario.store().getArea("home"));
    assertNull(scenario.store().getArea("house"));
  }

  @Test
  void aRenameIsRefusedWhileThePlayerHasUnsavedChangesToTheClaim() {
    MapArea area = home();
    MapToolsUtils.tryEditing(alex, area);
    MapToolsModule.sessions.get(alex.getUniqueId()).setChanged(true);
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("area_id", "house"));

    assertEquals(
        "Save or stop your own changes to this claim first.", MessageTestUtils.nextMessage(alex));
    assertNull(scenario.store().getArea("house"));
  }

  @Test
  void aChangeOfDetailsIsNotRefusedBecauseSomeoneElseEditsTheClaim() {
    MapArea area = home();
    DialogPlayer bob = scenario.editor("Bob");
    MapToolsUtils.tryEditing(bob, area);
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals("New", scenario.store().getArea("home").label());
    assertEquals("Updated home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void pickingAColorKeepsWhatWasTypedAndSavesTheColorWithTheNextSave() {
    ShownDialog dialog = manage(home());
    dialog.click("Pick color", dialog.responseWithInitialValues().text("label", "Typed"), alex);

    alex.lastDialog().click("■ blue", new Response(), alex);

    ShownDialog form = alex.lastDialog();
    assertEquals("Manage claim home", form.title());
    assertTrue(form.body().contains("Color: ■ blue"));
    assertEquals("Typed", form.text("label").initial());
    assertEquals("gold", scenario.store().getArea("home").color());

    save(form, form.responseWithInitialValues());

    MapArea saved = scenario.store().getArea("home");
    assertEquals("blue", saved.color());
    assertEquals("Typed", saved.label());
  }

  @Test
  void aPickedColorThatIsTheCurrentOneChangesNothing() {
    MapArea area = home();
    ShownDialog dialog = manage(area);
    dialog.click("Pick color", dialog.responseWithInitialValues(), alex);
    alex.lastDialog().click("■ gold", new Response(), alex);

    ShownDialog form = alex.lastDialog();
    save(form, form.responseWithInitialValues());

    assertSame(area, scenario.store().getArea("home"));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aStrangerCannotUseTheForm() {
    ShownDialog dialog = manage(home());
    DialogPlayer stranger = DialogTestUtils.addPlayer(scenario.server(), "Stranger");

    dialog.click("Save", dialog.responseWithInitialValues().text("label", "Hacked"), stranger);
    dialog.click("Delete", dialog.responseWithInitialValues(), stranger);
    dialog.click("Pick color", dialog.responseWithInitialValues(), stranger);

    assertEquals("Label home", scenario.store().getArea("home").label());
    assertEquals(0, stranger.dialogs().size());
    assertNull(stranger.nextComponentMessage());
  }
}
