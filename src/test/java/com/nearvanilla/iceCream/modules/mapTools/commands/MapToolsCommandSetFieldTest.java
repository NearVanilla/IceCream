package com.nearvanilla.iceCream.modules.mapTools.commands;

import static com.nearvanilla.iceCream.testutil.CommandTestUtils.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand.Kind;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Changing one detail of a claim or marker with {@code /maptools set <kind> <id> <field>}. */
class MapToolsCommandSetFieldTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String CLAIM_FIELDS =
      "Use id, label, color, owners, description, opacity, points.";
  private static final String MARKER_FIELDS = "Use id, label, color, owners, description, icon.";
  private static final String BAD_COLOR =
      "Use a color name such as gold, or a hex code such as ff8800.";
  private static final String POINTS_USAGE =
      "Use points <x z; x z; x z> with at least 3 points that are not in one line.";
  private static final String BAD_PERCENT = "Use a percentage from 0 to 100.";

  private Scenario scenario;
  private DialogPlayer alex;
  private DialogPlayer sam;
  private ConsoleCommandSenderMock console;
  private final MapToolsCommand command = new MapToolsCommand();

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    sam = scenario.editor("Sam");
    console = scenario.server().getConsoleSender();
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    store(MapToolsTestUtils.rect("shop", WORLD, 2000, 2000, 2009, 2009));
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

  private MapArea area(String id) {
    return scenario.store().getArea(id);
  }

  private CommandSourceStack source() {
    return stack(alex);
  }

  private String next() {
    return MessageTestUtils.nextMessage(alex);
  }

  private void setClaim(String field, String value) {
    command.set(source(), Kind.CLAIM, "home", field, value);
  }

  private void setMarker(String field, String value) {
    command.set(source(), Kind.MARKER, "spot", field, value);
  }

  /** Loads icons with these names, which markers can then use. */
  private void icons(String... names) throws IOException {
    MapToolsTestUtils.writeIcons(names);
    MapToolsUtils.init(MapToolsTestUtils.emptySquaremap());
  }

  // Looking up and checking the field

  @Test
  void anUnknownAreaSaysSo() {
    command.set(source(), Kind.CLAIM, "nobody", "label", "x");

    assertEquals("There is no claim called nobody.", next());
  }

  @Test
  void aClaimAskedForAsAMarkerSaysThereIsNoSuchMarker() {
    command.set(source(), Kind.MARKER, "home", "label", "x");

    assertEquals("There is no marker called home.", next());
  }

  @Test
  void anUnknownFieldListsTheClaimFields() {
    setClaim("size", "big");

    assertEquals(CLAIM_FIELDS, next());
  }

  @Test
  void anUnknownFieldListsTheMarkerFields() {
    setMarker("size", "big");

    assertEquals(MARKER_FIELDS, next());
  }

  @Test
  void aClaimFieldThatMarkersLackIsRefusedForAMarker() {
    setMarker("opacity", "50");
    setMarker("points", "0 0; 1 0; 1 1");

    assertEquals(MARKER_FIELDS, next());
    assertEquals(MARKER_FIELDS, next());
  }

  @Test
  void theIconFieldIsRefusedForAClaim() {
    setClaim("icon", "pin");

    assertEquals(CLAIM_FIELDS, next());
  }

  @Test
  void fieldNamesCanBeTypedInAnyCase() {
    setClaim("LaBeL", "New Name");

    assertEquals("New Name", area("home").label());
    assertEquals("Updated the label of home.", next());
  }

  // Label

  @Test
  void theLabelIsChanged() {
    setClaim("label", "The Keep");

    assertEquals("The Keep", area("home").label());
    assertEquals("Updated the label of home.", next());
    assertNull(alex.nextMessage());
  }

  @Test
  void aLabelCannotBeEmptyOrMissing() {
    setClaim("label", "   ");
    setClaim("label", null);

    assertEquals("The label cannot be empty.", next());
    assertEquals("The label cannot be empty.", next());
    assertEquals("Label home", area("home").label());
  }

  @Test
  void aLabelOfTheLongestLengthIsAllowedAndOneLongerIsNot() {
    setClaim("label", "x".repeat(64));
    assertEquals("x".repeat(64), area("home").label());
    alex.nextMessage();

    setClaim("label", "x".repeat(65));

    assertEquals("That is too long; use up to 64 characters.", next());
    assertEquals("x".repeat(64), area("home").label());
  }

  @Test
  void aMarkerLabelIsChangedToo() {
    setMarker("label", "Lookout");

    assertEquals("Lookout", area("spot").label());
    assertEquals("Updated the label of spot.", next());
  }

  // Color

  @ParameterizedTest
  @CsvSource({"ff8800, '#ff8800'", "'#FF8800', '#ff8800'", "Gold, gold", "RED, red"})
  void theColorIsStoredInItsNormalForm(String typed, String stored) {
    setClaim("color", typed);

    assertEquals(stored, area("home").color());
    assertEquals("Updated the color of home.", next());
  }

  @Test
  void anInvalidColorIsRefused() {
    setClaim("color", "ff88");
    setClaim("color", null);

    assertEquals(BAD_COLOR, next());
    assertEquals(BAD_COLOR, next());
    assertEquals("gold", area("home").color());
  }

  // Owners and description

  @Test
  void theOwnersAreSplitAtCommas() {
    setClaim("owners", "Alex,  Sam , ,Kim");

    assertEquals(List.of("Alex", "Sam", "Kim"), area("home").owners());
    assertEquals("Updated the owners of home.", next());
  }

  @Test
  void noOwnersClearsTheList() {
    store(area("home").withOwners(List.of("Alex")));

    setClaim("owners", null);

    assertTrue(area("home").owners().isEmpty());
    assertEquals("Updated the owners of home.", next());
  }

  @Test
  void ownersAndDescriptionsCanBeAsLongAsTheFormsAllow() {
    setClaim("owners", "o".repeat(256));
    setClaim("description", "d".repeat(256));
    assertEquals(256, area("home").owners().getFirst().length());
    assertEquals(256, area("home").description().length());
    alex.nextMessage();
    alex.nextMessage();

    setClaim("owners", "o".repeat(257));
    setClaim("description", "d".repeat(257));

    assertEquals("That is too long; use up to 256 characters.", next());
    assertEquals("That is too long; use up to 256 characters.", next());
    assertEquals(256, area("home").description().length());
  }

  @Test
  void theDescriptionIsChangedAndCanBeCleared() {
    setClaim("description", "A fine place");
    assertEquals("A fine place", area("home").description());
    assertEquals("Updated the description of home.", next());

    setClaim("description", null);

    assertNull(area("home").description());
    assertEquals("Updated the description of home.", next());
  }

  // Opacity

  @ParameterizedTest
  @CsvSource({"0, 0.0", "50, 0.5", "100, 1.0", "50%, 0.5", " 75 %, 0.75", "7, 0.07"})
  void theOpacityIsAPercentage(String typed, double stored) {
    setClaim("opacity", typed);

    assertEquals(stored, area("home").opacity(), 1e-9);
    assertEquals("Updated the opacity of home.", next());
  }

  @ParameterizedTest
  @CsvSource({"101", "-1", "abc", "12.5", "1000"})
  void anOpacityOutsideZeroToOneHundredIsRefused(String typed) {
    setClaim("opacity", typed);

    assertEquals(BAD_PERCENT, next());
    assertNull(area("home").opacity());
  }

  @Test
  void aBlankOpacityIsRefused() {
    setClaim("opacity", "");

    assertEquals(BAD_PERCENT, next());
  }

  @Test
  void leavingTheOpacityOutGoesBackToTheConfiguredDefault() {
    store(area("home").withOpacity(0.9));

    setClaim("opacity", null);

    assertNull(area("home").opacity());
    assertEquals("Updated the opacity of home.", next());
  }

  // Icon

  @Test
  void aMarkerGetsAnIconThatExists() throws IOException {
    icons("pin", "flag");

    setMarker("icon", "flag");

    assertEquals("flag", area("spot").icon());
    assertEquals("Updated the icon of spot.", next());
  }

  @Test
  void anIconThatDoesNotExistIsRefused() throws IOException {
    icons("pin");

    setMarker("icon", "castle");

    assertEquals(
        "There is no icon called castle. Icons are the PNG files in plugins/IceCream/map-icons.",
        next());
    assertNull(area("spot").icon());
  }

  @Test
  void leavingTheIconOutClearsIt() throws IOException {
    icons("pin");
    store(area("spot").withIcon("pin"));

    setMarker("icon", null);

    assertNull(area("spot").icon());
    assertEquals("Updated the icon of spot.", next());
  }

  // Points

  @Test
  void aPolygonsPointsAreReplaced() {
    store(MapToolsTestUtils.polygon("poly", WORLD, 1000, 1000, 1010, 1000, 1010, 1010));

    command.set(source(), Kind.CLAIM, "poly", "points", "1500 1500; 1510 1500; 1510 1510");

    assertEquals(
        List.of(
            new MapPoint(1500.5, 1500.5),
            new MapPoint(1510.5, 1500.5),
            new MapPoint(1510.5, 1510.5)),
        area("poly").points());
    assertEquals("Updated the shape of poly.", next());
  }

  @Test
  void pointsMakeAnyClaimAPolygonInItsOwnWorld() {
    setClaim("points", "0 0; 10 0; 10 10");

    assertEquals(MapArea.POLYGON, area("home").shape());
    assertEquals(WORLD, area("home").world());
    assertEquals("Updated the shape of home.", next());
  }

  @Test
  void pointsThatDoNotMakeAShapeAreRefusedWithTheUsage() {
    setClaim("points", "0 0; 10 0");
    setClaim("points", "0 0; 5 5; 10 10");
    setClaim("points", null);

    assertEquals(POINTS_USAGE, next());
    assertEquals(POINTS_USAGE, next());
    assertEquals(POINTS_USAGE, next());
    assertEquals(MapArea.RECT, area("home").shape());
  }

  @Test
  void newPointsNearAnotherClaimAskAPlayerFirst() {
    store(MapToolsTestUtils.rect("near", WORLD, 100, 100, 109, 109));

    setClaim("points", "50 50; 60 50; 60 60");

    ShownDialog question = alex.lastDialog();
    assertEquals("Claims nearby", question.title());
    assertEquals(List.of("Save anyway"), question.buttons());
    assertEquals(MapArea.RECT, area("home").shape());

    question.click("Save anyway", question.responseWithInitialValues(), alex);

    assertEquals(MapArea.POLYGON, area("home").shape());
    assertEquals("Updated the shape of home.", next());
  }

  @Test
  void newPointsForAClaimThatIsGoneByConfirmationSaySo() {
    store(MapToolsTestUtils.rect("near", WORLD, 100, 100, 109, 109));
    setClaim("points", "50 50; 60 50; 60 60");
    ShownDialog question = alex.lastDialog();
    scenario.store().removeArea("home");

    question.click("Save anyway", question.responseWithInitialValues(), alex);

    assertEquals("There is no claim called home.", next());
    assertNull(area("home"));
  }

  @Test
  void theConsoleChangesPointsWithoutBeingAsked() {
    store(MapToolsTestUtils.rect("near", WORLD, 100, 100, 109, 109));

    command.set(stack(console), Kind.CLAIM, "home", "points", "50 50; 60 50; 60 60");

    assertEquals(MapArea.POLYGON, area("home").shape());
    assertEquals("Updated the shape of home.", MessageTestUtils.nextMessage(console));
  }

  @Test
  void pointsOfAClaimSomeoneElseIsEditingAreRefused() {
    MapToolsUtils.startEditing(sam, EditSession.edit(area("home")));

    setClaim("points", "50 50; 60 50; 60 60");

    assertEquals("Sam is editing this claim right now.", next());
    assertEquals(MapArea.RECT, area("home").shape());
  }

  // Renaming

  @Test
  void theIdIsChanged() {
    setClaim("id", "castle");

    assertNull(area("home"));
    assertEquals("Label home", area("castle").label());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("castle").points());
    assertEquals("Renamed home to castle.", next());
  }

  @Test
  void aMarkerIsRenamedToo() {
    setMarker("id", "lookout");

    assertNull(area("spot"));
    assertEquals(MapArea.MARKER, area("lookout").shape());
    assertEquals("Renamed spot to lookout.", next());
  }

  @Test
  void renamingWithoutANewIdShowsTheUsage() {
    setClaim("id", null);

    assertEquals("Use /maptools set claim <id> id <new id>.", next());
  }

  @Test
  void theUsageNamesTheKindOfTheAreaBeingRenamed() {
    setMarker("id", null);

    assertEquals("Use /maptools set marker <id> id <new id>.", next());
  }

  @Test
  void aNewIdWithBadCharactersIsRefused() {
    setClaim("id", "Not Valid");

    assertEquals("Ids may only use a-z, 0-9, _ and -, up to 32 characters.", next());
    assertEquals("Label home", area("home").label());
  }

  @Test
  void aNewIdThatIsAlreadyTakenIsRefused() {
    setClaim("id", "shop");

    assertEquals("A claim with id shop already exists.", next());
    assertEquals("Label home", area("home").label());
  }

  @Test
  void aClaimSomeoneElseIsEditingCannotBeRenamed() {
    MapToolsUtils.startEditing(sam, EditSession.edit(area("home")));

    setClaim("id", "castle");

    assertEquals("Sam is editing this claim right now.", next());
    assertEquals("Label home", area("home").label());
  }

  @Test
  void aClaimWhoseOwnEditHasChangesCannotBeRenamed() {
    EditSession mine = EditSession.edit(area("home"));
    mine.setChanged(true);
    MapToolsUtils.startEditing(alex, mine);

    setClaim("id", "castle");

    assertEquals("Save or stop your own changes to this claim first.", next());
  }

  @Test
  void anEditWithoutChangesContinuesUnderTheNewId() {
    MapToolsUtils.startEditing(alex, EditSession.edit(area("home")));

    setClaim("id", "castle");

    assertEquals("castle", MapToolsModule.sessions.get(alex.getUniqueId()).getAreaId());
  }
}
