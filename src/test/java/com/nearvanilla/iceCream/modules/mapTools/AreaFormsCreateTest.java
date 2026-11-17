package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;

/** The form that saves the shape a player drew as a new claim or marker. */
class AreaFormsCreateTest {
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

  private EditSession session() {
    return MapToolsModule.sessions.get(alex.getUniqueId());
  }

  private void click(int x, int z, boolean sneaking) {
    EditActions.click(alex, scenario.overworldBlock(x, 64, z), sneaking);
  }

  /** Draws a rectangle of 10 by 10 blocks at the origin and opens the form. */
  private ShownDialog drawnRectangle() {
    MapToolsUtils.startEditing(alex, EditSession.create(Mode.RECT));
    click(0, 0, false);
    click(9, 9, true);
    alex.nextComponentMessage();
    AreaForms.openCreate(alex);
    return alex.lastDialog();
  }

  private ShownDialog drawnMarker() {
    MapToolsUtils.startEditing(alex, EditSession.create(Mode.MARKER));
    click(5, 5, false);
    alex.nextComponentMessage();
    AreaForms.openCreate(alex);
    return alex.lastDialog();
  }

  private static Response filled(ShownDialog dialog, String id, String label) {
    return dialog.responseWithInitialValues().text("area_id", id).text("label", label);
  }

  private void create(ShownDialog dialog, Response response) {
    dialog.click("Create", response, alex);
  }

  @Test
  void theClaimFormAsksForTheIdAndDetails() {
    ShownDialog dialog = drawnRectangle();

    assertEquals("New claim", dialog.title());
    assertEquals(
        List.of("area_id", "label", "opacity", "owner", "description"), dialog.inputKeys());
    assertEquals(List.of("Color: ■ gold"), dialog.body());
    assertEquals(List.of("Create", "Pick color"), dialog.buttons());
    assertEquals("Cancel", dialog.exitLabel());
    assertEquals(2, dialog.columns());
  }

  @Test
  void theClaimFormStartsEmptyWithTheDefaultOpacity() {
    ShownDialog dialog = drawnRectangle();

    assertEquals("", dialog.text("area_id").initial());
    assertEquals("", dialog.text("label").initial());
    assertEquals("", dialog.text("owner").initial());
    assertEquals("", dialog.text("description").initial());
    assertEquals(20f, dialog.number("opacity").initial());
    assertEquals(0f, dialog.number("opacity").start());
    assertEquals(100f, dialog.number("opacity").end());
  }

  @Test
  void theInputsAreLimitedToWhatTheFormAllows() {
    ShownDialog dialog = drawnRectangle();

    assertEquals(32, dialog.text("area_id").maxLength());
    assertEquals(64, dialog.text("label").maxLength());
    assertEquals(256, dialog.text("owner").maxLength());
    assertEquals(256, dialog.text("description").maxLength());
    assertEquals(4, dialog.text("description").multiline().maxLines());
  }

  @Test
  void theFormStartsWithTheConfiguredColorAndOpacity() {
    scenario.configure(
        "modules:\n  maptools:\n    claim-color: red\n    claim-fill-opacity: 0.55\n");

    ShownDialog dialog = drawnRectangle();

    assertEquals(List.of("Color: ■ red"), dialog.body());
    assertEquals(55f, dialog.number("opacity").initial());
  }

  @Test
  void theMarkerFormHasAnIconInsteadOfAColorAndOpacity() {
    ShownDialog dialog = drawnMarker();

    assertEquals("New marker", dialog.title());
    assertEquals(List.of("area_id", "label", "owner", "description"), dialog.inputKeys());
    assertEquals(List.of("[BARRIER] Icon: none, shown as a dot"), dialog.body());
    assertEquals(List.of("Create", "Pick icon"), dialog.buttons());
  }

  @Test
  void theMarkerFormStartsWithTheFirstIconThatIsAvailable() throws IOException {
    writeIcon("home");
    writeIcon("pin");
    loadIcons();

    ShownDialog dialog = drawnMarker();

    assertEquals(List.of("[PAPER] Icon: home"), dialog.body());
  }

  @Test
  void theFormIsTheClaimFormWhenThereIsNoShapeBeingDrawn() {
    AreaForms.openCreate(alex);

    assertEquals("New claim", alex.lastDialog().title());
  }

  @Test
  void creatingSavesTheDrawnShapeAsAClaim() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home sweet home"));

    MapArea area = scenario.store().getArea("home");
    assertNotNull(area);
    assertEquals(WORLD, area.world());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), area.points());
    assertEquals(MapArea.RECT, area.shape());
    assertEquals("Home sweet home", area.label());
    assertEquals("gold", area.color());
    assertEquals(List.of(), area.owners());
    assertNull(area.description());
    assertNull(area.opacity());
  }

  @Test
  void creatingTellsThePlayerAndStartsEditingTheNewClaim() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
    assertEquals("home", session().getAreaId());
    assertEquals(Mode.RECT, session().getMode());
    assertFalse(session().hasChanges());
  }

  @Test
  void creatingTakesTheOwnersAndDescriptionFromTheForm() {
    ShownDialog dialog = drawnRectangle();

    create(
        dialog,
        filled(dialog, "home", "Home")
            .text("owner", " Alex ,, Sam ")
            .text("description", "  First line\nSecond line  "));

    MapArea area = scenario.store().getArea("home");
    assertEquals(List.of("Alex", "Sam"), area.owners());
    assertEquals("First line\nSecond line", area.description());
  }

  @Test
  void aBlankOwnerAndDescriptionAreNotStored() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home").text("owner", "   ").text("description", "\n "));

    MapArea area = scenario.store().getArea("home");
    assertEquals(List.of(), area.owners());
    assertNull(area.description());
  }

  @Test
  void anOpacityOtherThanTheDefaultIsStoredAsAFraction() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home").number("opacity", 55f));

    assertEquals(0.55, scenario.store().getArea("home").opacity());
  }

  @Test
  void theDefaultOpacityIsNotStoredSoTheConfiguredOneKeepsApplying() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home").number("opacity", 20f));

    assertNull(scenario.store().getArea("home").opacity());
  }

  @Test
  void aFullyClearOpacityIsStored() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "clear", "Clear").number("opacity", 0f));

    assertEquals(0.0, scenario.store().getArea("clear").opacity());
  }

  @Test
  void aFullySolidOpacityIsStored() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "solid", "Solid").number("opacity", 100f));

    assertEquals(1.0, scenario.store().getArea("solid").opacity());
  }

  @Test
  void anOpacityBetweenStepsIsRoundedToAWholePercent() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home").number("opacity", 42.6f));

    assertEquals(0.43, scenario.store().getArea("home").opacity());
  }

  @Test
  void theFormTakesTheColorFromItsOpening() {
    scenario.configure("modules:\n  maptools:\n    claim-color: '#00ff00'\n");
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("#00ff00", scenario.store().getArea("home").color());
  }

  @Test
  void creatingAMarkerSavesItWithoutAnOpacityOrNearbyCheck() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 0, 0, 20, 20));
    ShownDialog dialog = drawnMarker();

    create(dialog, filled(dialog, "spot", "Spot"));

    MapArea area = scenario.store().getArea("spot");
    assertTrue(area.isMarker());
    assertEquals(MapToolsUtils.blockCenter(5, 5), area.center());
    assertNull(area.opacity());
    assertEquals("Created marker spot.", MessageTestUtils.nextMessage(alex));
    assertEquals("spot", session().getAreaId());
    assertEquals(Mode.MARKER, session().getMode());
  }

  @Test
  void aMarkerKeepsTheIconItWasShownWith() throws IOException {
    writeIcon("pin");
    loadIcons();
    ShownDialog dialog = drawnMarker();

    create(dialog, filled(dialog, "spot", "Spot"));

    assertEquals("pin", scenario.store().getArea("spot").icon());
  }

  @Test
  void anInvalidIdOpensTheFormAgainWithTheErrorAndWhatWasTyped() {
    ShownDialog dialog = drawnRectangle();

    create(
        dialog,
        filled(dialog, "Bad Id", "My label")
            .text("owner", "Alex")
            .text("description", "Some text")
            .number("opacity", 35f));

    assertEquals(2, alex.dialogs().size());
    ShownDialog again = alex.lastDialog();
    assertEquals("New claim", again.title());
    assertEquals(
        List.of("Ids may only use a-z, 0-9, _ and -, up to 32 characters.", "Color: ■ gold"),
        again.body());
    assertEquals("Bad Id", again.text("area_id").initial());
    assertEquals("My label", again.text("label").initial());
    assertEquals("Alex", again.text("owner").initial());
    assertEquals("Some text", again.text("description").initial());
    assertEquals(35f, again.number("opacity").initial());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void aBlankIdAndAnEmptyLabelOpenTheFormAgain() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "  ", "Home"));
    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        alex.lastDialog().body().getFirst());

    create(alex.lastDialog(), filled(alex.lastDialog(), "home", "   "));
    assertEquals("The label cannot be empty.", alex.lastDialog().body().getFirst());

    assertTrue(scenario.store().getAreas().isEmpty());
    assertEquals(3, alex.dialogs().size());
  }

  @Test
  void anIdThatIsTakenOpensTheFormAgain() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 50, 50, 59, 59));
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("A claim with id home already exists.", alex.lastDialog().body().getFirst());
    assertEquals("home", alex.lastDialog().text("area_id").initial());
  }

  @Test
  void aTooLongLabelOpensTheFormAgain() {
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "x".repeat(65)));

    assertEquals("Labels can be up to 64 characters.", alex.lastDialog().body().getFirst());
  }

  @Test
  void theFormCanBeCorrectedAndSentAgain() {
    ShownDialog dialog = drawnRectangle();
    create(dialog, filled(dialog, "Bad Id", "Home"));

    ShownDialog again = alex.lastDialog();
    create(again, filled(again, "home", "Home"));

    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void ifTheShapeIsGoneNothingIsCreated() {
    ShownDialog dialog = drawnRectangle();
    MapToolsUtils.startEditing(alex, EditSession.view());

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Your shape is no longer there.", MessageTestUtils.nextMessage(alex));
    assertTrue(scenario.store().getAreas().isEmpty());
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void ifThePlayerStoppedEditingNothingIsCreated() {
    ShownDialog dialog = drawnRectangle();
    MapToolsUtils.stopEditing(alex);

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Your shape is no longer there.", MessageTestUtils.nextMessage(alex));
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void ifTheShapeIsNoLongerCompleteNothingIsCreated() {
    ShownDialog dialog = drawnRectangle();
    session().undo();

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Your shape is no longer there.", MessageTestUtils.nextMessage(alex));
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void ifThePlayerIsEditingAnExistingClaimNothingIsCreated() {
    ShownDialog dialog = drawnRectangle();
    MapArea other = MapToolsTestUtils.rect("other", WORLD, 100, 100, 109, 109);
    scenario.store().putArea(other);
    MapToolsUtils.tryEditing(alex, other);

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Your shape is no longer there.", MessageTestUtils.nextMessage(alex));
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aClaimCloseToAnotherAsksBeforeCreating() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home"));

    assertEquals("Claims nearby", alex.lastDialog().title());
    assertTrue(alex.lastDialog().buttons().contains("Create anyway"));
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void creatingAnywayCreatesTheClaim() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = drawnRectangle();
    create(dialog, filled(dialog, "home", "Home"));

    alex.lastDialog().click("Create anyway", new Response(), alex);

    assertNotNull(scenario.store().getArea("home"));
    assertEquals("home", session().getAreaId());
  }

  @Test
  void cancellingTheNearbyQuestionGoesBackToTheFormWithWhatWasTyped() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = drawnRectangle();
    create(dialog, filled(dialog, "home", "My label").text("owner", "Alex"));

    alex.lastDialog().clickExit(new Response(), alex);

    ShownDialog back = alex.lastDialog();
    assertEquals("New claim", back.title());
    assertEquals("home", back.text("area_id").initial());
    assertEquals("My label", back.text("label").initial());
    assertEquals("Alex", back.text("owner").initial());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void ifTheIdWasTakenWhileTheNearbyQuestionIsOpenTheOtherClaimIsLeftAlone() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = drawnRectangle();
    create(dialog, filled(dialog, "home", "Home"));
    MapArea taken = MapToolsTestUtils.rect("home", WORLD, 500, 500, 509, 509);
    scenario.store().putArea(taken);

    alex.lastDialog().click("Create anyway", new Response(), alex);

    assertEquals("A claim with id home already exists.", MessageTestUtils.nextMessage(alex));
    assertSame(taken, scenario.store().getArea("home"));
    assertTrue(session().isNew());
  }

  @Test
  void aClaimFarFromEveryOtherClaimIsCreatedAtOnce() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 5000, 0, 5009, 9));
    ShownDialog dialog = drawnRectangle();

    create(dialog, filled(dialog, "home", "Home"));

    assertNotNull(scenario.store().getArea("home"));
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void aPlayerWithoutThePermissionCannotUseAnOldForm() {
    ShownDialog dialog = drawnRectangle();
    DialogPlayer stranger = DialogTestUtils.addPlayer(scenario.server(), "Stranger");

    dialog.click("Create", filled(dialog, "home", "Home"), stranger);

    assertNull(scenario.store().getArea("home"));
    assertNull(stranger.nextComponentMessage());
    assertEquals(0, stranger.dialogs().size());
  }

  @Test
  void theConsoleCannotUseAForm() {
    ShownDialog dialog = drawnRectangle();

    dialog.click("Create", filled(dialog, "home", "Home"), scenario.server().getConsoleSender());

    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aDialogOfTheMarkerFormHasTheIconButtonOnly() {
    ShownDialog dialog = drawnMarker();

    assertFalse(dialog.buttons().contains("Pick color"));
    assertTrue(dialog.hasAction("Pick icon"));
  }

  // Squaremap is only needed to load the icon files, so its registry for them is a plain mock.
  @SuppressWarnings("unchecked")
  private void loadIcons() {
    Squaremap api = mock(Squaremap.class);
    when(api.iconRegistry()).thenReturn(mock(Registry.class));
    MapToolsUtils.init(api);
  }

  private void writeIcon(String name) throws IOException {
    File folder = new File(IceCreamFixture.dataFolder(), "map-icons");
    assertTrue(folder.mkdirs() || folder.isDirectory());
    ImageIO.write(
        new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
        "png",
        new File(folder, name + ".png"));
  }
}
