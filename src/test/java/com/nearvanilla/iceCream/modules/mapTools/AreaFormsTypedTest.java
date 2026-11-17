package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The forms that create a rectangle or a circle from typed coordinates. */
class AreaFormsTypedTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String RECT_PROBLEM =
      "Use whole numbers like 100 -200 for each corner, and a rectangle at most 3000 blocks wide"
          + " and long.";
  private static final String CIRCLE_PROBLEM =
      "Use whole numbers like 100 -200 for the center, and a radius from 1 to 3000.";

  private Scenario scenario;
  private DialogPlayer alex;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    // The player stands in block 12, -4 of the limbo world.
    alex.setLocation(new Location(scenario.limbo(), 12.7, 64, -3.2));
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private EditSession session() {
    return MapToolsModule.sessions.get(alex.getUniqueId());
  }

  private ShownDialog rectForm() {
    AreaForms.openCreate(alex, Mode.RECT);
    return alex.lastDialog();
  }

  private ShownDialog circleForm() {
    AreaForms.openCreate(alex, Mode.CIRCLE);
    return alex.lastDialog();
  }

  private static Response rect(ShownDialog form, String first, String second) {
    return form.responseWithInitialValues()
        .text("coordinate0", first)
        .text("coordinate1", second)
        .text("area_id", "home")
        .text("label", "Home");
  }

  private static Response circle(ShownDialog form, String center, String radius) {
    return form.responseWithInitialValues()
        .text("coordinate0", center)
        .text("coordinate1", radius)
        .text("area_id", "pond")
        .text("label", "Pond");
  }

  @Test
  void theRectangleFormAsksForTwoCornersStartingAtThePlayersBlock() {
    ShownDialog form = rectForm();

    assertEquals("New rectangle claim", form.title());
    assertEquals(
        List.of(
            "coordinate0", "coordinate1", "area_id", "label", "opacity", "owner", "description"),
        form.inputKeys());
    assertEquals("12 -4", form.text("coordinate0").initial());
    assertEquals("12 -4", form.text("coordinate1").initial());
    assertEquals(24, form.text("coordinate0").maxLength());
    assertEquals(List.of("Created in the overworld.", "Color: ■ gold"), form.body());
    assertEquals(List.of("Create", "Pick color"), form.buttons());
  }

  @Test
  void theCircleFormAsksForACenterAndARadiusOfTen() {
    ShownDialog form = circleForm();

    assertEquals("New circle claim", form.title());
    assertEquals("12 -4", form.text("coordinate0").initial());
    assertEquals("10", form.text("coordinate1").initial());
    assertEquals(List.of("Created in the overworld.", "Color: ■ gold"), form.body());
  }

  @ParameterizedTest
  @EnumSource(
      value = Mode.class,
      names = {"POLYGON", "MARKER"})
  void onlyRectanglesAndCirclesCanBeTyped(Mode mode) {
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, () -> AreaForms.openCreate(alex, mode));

    assertEquals("Only rectangles and circles are typed", failure.getMessage());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void theFormStartsAtTheBlockOfANegativeLocation() {
    alex.setLocation(new Location(scenario.limbo(), -0.5, 64, -16.0));

    ShownDialog form = rectForm();

    assertEquals("-1 -16", form.text("coordinate0").initial());
  }

  @Test
  void aTypedRectangleIsCreatedInTheOverworldWhereverThePlayerIs() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    MapArea area = scenario.store().getArea("home");
    assertNotNull(area);
    assertEquals(WORLD, area.world());
    assertEquals(MapArea.RECT, area.shape());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), area.points());
    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aTypedClaimIsNotOpenForEditingAfterwards() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void theCornersCanBeTypedInAnyOrderAndWithCommasOrSpaces() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "  9,   9 ", "-3 , -2"), alex);

    assertEquals(MapToolsUtils.rectangle(-3, -2, 9, 9), scenario.store().getArea("home").points());
  }

  @Test
  void aRectangleOfTheLargestSizeIsCreated() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "2999 2999"), alex);

    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void aRectangleOneBlockTooWideIsRefused() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "3000 0"), alex);

    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aRectangleOneBlockTooLongIsRefused() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "0 -3000"), alex);

    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "abc", "1", "1 2 3", "1.5 2", "1;2", "x y", "99999999999 0"})
  void aCornerThatIsNotTwoWholeNumbersOpensTheFormAgain(String corner) {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", corner), alex);

    assertEquals(2, alex.dialogs().size());
    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aMissingFirstCornerOpensTheFormAgainToo() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "5 5").without("coordinate0"), alex);

    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
  }

  @Test
  void theFormOpensAgainWithWhatWasTyped() {
    ShownDialog form = rectForm();

    form.click(
        "Create",
        rect(form, " 4 5 ", "oops")
            .text("owner", "Alex")
            .text("description", "Some text")
            .number("opacity", 65f),
        alex);

    ShownDialog again = alex.lastDialog();
    assertEquals("New rectangle claim", again.title());
    assertEquals(List.of(RECT_PROBLEM, "Created in the overworld.", "Color: ■ gold"), again.body());
    assertEquals("4 5", again.text("coordinate0").initial());
    assertEquals("oops", again.text("coordinate1").initial());
    assertEquals("home", again.text("area_id").initial());
    assertEquals("Home", again.text("label").initial());
    assertEquals("Alex", again.text("owner").initial());
    assertEquals("Some text", again.text("description").initial());
    assertEquals(65f, again.number("opacity").initial());
  }

  @Test
  void theCoordinatesAreCheckedBeforeTheDetails() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "bad", "5 5").text("area_id", "Bad Id"), alex);

    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
  }

  @Test
  void anInvalidIdOpensTheTypedFormAgain() {
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "5 5").text("area_id", "Bad Id"), alex);

    ShownDialog again = alex.lastDialog();
    assertEquals(
        List.of(
            "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
            "Created in the overworld.",
            "Color: ■ gold"),
        again.body());
    assertEquals("0 0", again.text("coordinate0").initial());
    assertEquals("5 5", again.text("coordinate1").initial());
  }

  @Test
  void aTypedCircleIsCreatedAroundTheMiddleOfItsCenterBlock() {
    ShownDialog form = circleForm();

    form.click("Create", circle(form, "100 -200", "25"), alex);

    MapArea area = scenario.store().getArea("pond");
    assertNotNull(area);
    assertEquals(WORLD, area.world());
    assertTrue(area.isCircle());
    assertEquals(new MapAreasStore.MapPoint(100.5, -199.5), area.center());
    assertEquals(25.0, area.radius());
    assertEquals(MapToolsUtils.circle(100.5, -199.5, 25), area.points());
    assertEquals("Created claim pond.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aCircleCanHaveARadiusFromOneToThreeThousand() {
    ShownDialog small = circleForm();
    small.click("Create", circle(small, "0 0", "1"), alex);
    assertEquals(1.0, scenario.store().getArea("pond").radius());
    scenario.store().removeArea("pond");

    ShownDialog large = circleForm();
    large.click("Create", circle(large, "0 0", "3000"), alex);
    assertEquals(3000.0, scenario.store().getArea("pond").radius());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "-25", "3001", "100000", "2.5", "ten", "", "  ", "1e2"})
  void aRadiusOutsideOneToThreeThousandOpensTheFormAgain(String radius) {
    ShownDialog form = circleForm();

    form.click("Create", circle(form, "0 0", radius), alex);

    assertEquals(CIRCLE_PROBLEM, alex.lastDialog().body().getFirst());
    assertEquals(2, alex.dialogs().size());
    assertNull(scenario.store().getArea("pond"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "5", "5 5 5", "a b", "1.5 2"})
  void aCenterThatIsNotTwoWholeNumbersOpensTheFormAgain(String center) {
    ShownDialog form = circleForm();

    form.click("Create", circle(form, center, "10"), alex);

    assertEquals(CIRCLE_PROBLEM, alex.lastDialog().body().getFirst());
    assertNull(scenario.store().getArea("pond"));
  }

  @Test
  void aTypedClaimCloseToAnotherAsksFirst() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    assertEquals("Claims nearby", alex.lastDialog().title());
    assertNull(scenario.store().getArea("home"));

    alex.lastDialog().click("Create anyway", new Response(), alex);

    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void cancellingTheNearbyQuestionOpensTheTypedFormAgain() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    ShownDialog form = rectForm();
    form.click("Create", rect(form, "0 0", "9 9"), alex);

    alex.lastDialog().clickExit(new Response(), alex);

    ShownDialog again = alex.lastDialog();
    assertEquals("New rectangle claim", again.title());
    assertEquals("0 0", again.text("coordinate0").initial());
    assertEquals("9 9", again.text("coordinate1").initial());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void creatingATypedClaimLeavesTheShapeThePlayerWasEditing() {
    MapToolsUtils.startEditing(alex, EditSession.view());
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void creatingATypedClaimLeavesTheClaimThePlayerWasEditing() {
    MapArea other = MapToolsTestUtils.rect("other", WORLD, 1000, 1000, 1009, 1009);
    scenario.store().putArea(other);
    MapToolsUtils.tryEditing(alex, other);
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    assertNotNull(scenario.store().getArea("home"));
    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void unsavedChangesAreAskedAboutBeforeATypedClaimIsCreated() {
    MapArea other = MapToolsTestUtils.rect("other", WORLD, 100, 100, 109, 109);
    scenario.store().putArea(other);
    MapToolsUtils.tryEditing(alex, other);
    session().moveRectCorner(0, scenario.overworldBlock(90, 63, 90));
    session().setChanged(true);
    ShownDialog form = rectForm();

    form.click("Create", rect(form, "0 0", "9 9"), alex);

    assertEquals("Unsaved changes to other", alex.lastDialog().title());
    assertNull(scenario.store().getArea("home"));
    assertTrue(session().hasChanges());
  }

  @Test
  void discardingTheChangesOpensTheTypedFormAgainWithWhatWasTyped() {
    MapArea other = MapToolsTestUtils.rect("other", WORLD, 100, 100, 109, 109);
    scenario.store().putArea(other);
    MapToolsUtils.tryEditing(alex, other);
    session().setChanged(true);
    ShownDialog form = rectForm();
    form.click("Create", rect(form, "0 0", "9 9"), alex);

    alex.lastDialog().click("Discard", new Response(), alex);

    ShownDialog again = alex.lastDialog();
    assertEquals("New rectangle claim", again.title());
    assertEquals("0 0", again.text("coordinate0").initial());
    assertEquals("home", again.text("area_id").initial());
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aTypedClaimDoesNotNeedAShapeBeingDrawn() {
    ShownDialog form = circleForm();

    form.click("Create", circle(form, "0 0", "10"), alex);

    assertNotNull(scenario.store().getArea("pond"));
    assertEquals(1, scenario.store().getAreas().size());
  }
}
