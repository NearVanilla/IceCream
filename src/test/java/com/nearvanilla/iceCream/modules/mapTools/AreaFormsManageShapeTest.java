package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Changing the shape of a saved claim from the corners or the center and radius in its form. */
class AreaFormsManageShapeTest {
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

  private static Response corners(ShownDialog dialog, String first, String second) {
    return dialog
        .responseWithInitialValues()
        .text("coordinate0", first)
        .text("coordinate1", second);
  }

  @Test
  void newCornersReshapeARectangle() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)));

    save(dialog, corners(dialog, "0 0", "19 29"));

    MapArea saved = scenario.store().getArea("home");
    assertEquals(MapToolsUtils.rectangle(0, 0, 19, 29), saved.points());
    assertEquals(MapArea.RECT, saved.shape());
    assertEquals(WORLD, saved.world());
    assertEquals("Updated home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void aReshapeKeepsTheDetailsOfTheClaim() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withLabel("Home"));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "-5,-5", "9, 9"));

    assertEquals(
        area.withGeometry(
            new MapAreasStore.AreaGeometry(
                WORLD, MapToolsUtils.rectangle(-5, -5, 9, 9), MapArea.RECT, null, null)),
        scenario.store().getArea("home"));
  }

  @Test
  void aRectangleInAnotherWorldKeepsItsWorld() {
    MapArea area = stored(MapToolsTestUtils.rect("fort", MapToolsTestUtils.NETHER, 0, 0, 9, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "5 5"));

    MapArea saved = scenario.store().getArea("fort");
    assertEquals(MapToolsTestUtils.NETHER, saved.world());
    assertEquals(MapToolsUtils.rectangle(0, 0, 5, 5), saved.points());
  }

  @Test
  void aReshapeAndARenameCanBeSavedTogether() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)));

    save(dialog, corners(dialog, "0 0", "4 4").text("area_id", "house"));

    assertNull(scenario.store().getArea("home"));
    assertEquals(MapToolsUtils.rectangle(0, 0, 4, 4), scenario.store().getArea("house").points());
  }

  @Test
  void aRectangleWithItsCornersRetypedWithCommasIsSavedWithTheSameShape() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0,0", "9,9"));

    // The text is not the one that was shown, so the shape is read again from it.
    assertEquals(area.points(), scenario.store().getArea("home").points());
    assertEquals("Updated home.", MessageTestUtils.nextMessage(alex));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "1", "1 2 3", "1.5 2", "99999999999 0"})
  void aCornerThatIsNotTwoWholeNumbersOpensTheFormAgain(String corner) {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", corner));

    assertEquals(2, alex.dialogs().size());
    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
    assertSame(area, scenario.store().getArea("home"));
  }

  @Test
  void aRectangleThatIsTooLargeOpensTheFormAgain() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "3000 0"));

    assertEquals(RECT_PROBLEM, alex.lastDialog().body().getFirst());
    assertEquals("3000 0", alex.lastDialog().text("coordinate1").initial());
    assertSame(area, scenario.store().getArea("home"));
  }

  @Test
  void aRectangleOfTheLargestSizeIsSaved() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)));

    save(dialog, corners(dialog, "0 0", "2999 2999"));

    assertEquals(
        MapToolsUtils.rectangle(0, 0, 2999, 2999), scenario.store().getArea("home").points());
  }

  @Test
  void theFormOpensAgainWithTheDetailsTypedAlongWithTheBadCorner() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)));

    save(dialog, corners(dialog, "0 0", "oops").text("label", "Typed").text("owner", "Sam"));

    ShownDialog again = alex.lastDialog();
    assertEquals("Typed", again.text("label").initial());
    assertEquals("Sam", again.text("owner").initial());
    assertEquals("oops", again.text("coordinate1").initial());
  }

  @Test
  void aCorrectedFormSavesTheNewShape() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)));
    save(dialog, corners(dialog, "0 0", "oops"));

    ShownDialog again = alex.lastDialog();
    save(again, corners(again, "0 0", "14 14"));

    assertEquals(MapToolsUtils.rectangle(0, 0, 14, 14), scenario.store().getArea("home").points());
  }

  @Test
  void aCenterAndRadiusReshapeACircle() {
    ShownDialog dialog = manage(stored(MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12)));

    save(dialog, corners(dialog, "20 30", "25"));

    MapArea saved = scenario.store().getArea("pond");
    assertEquals(new MapPoint(20.5, 30.5), saved.center());
    assertEquals(25.0, saved.radius());
    assertEquals(MapToolsUtils.circle(20.5, 30.5, 25), saved.points());
    assertTrue(saved.isCircle());
  }

  @Test
  void aCircleKeepsItsExactRadiusWhenOnlyTheCenterChanges() {
    MapArea area =
        stored(
            MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12)
                .withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(5.5, -2.5), 12.4)));
    ShownDialog dialog = manage(area);
    assertEquals("12", dialog.text("coordinate1").initial());

    save(dialog, corners(dialog, "7 7", "12"));

    MapArea saved = scenario.store().getArea("pond");
    assertEquals(new MapPoint(7.5, 7.5), saved.center());
    assertEquals(12.4, saved.radius());
  }

  @Test
  void aCircleKeepsItsExactCenterWhenOnlyTheRadiusChanges() {
    MapArea area =
        stored(
            MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12)
                .withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(5.25, -2.75), 12)));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "5 -3", "20"));

    MapArea saved = scenario.store().getArea("pond");
    assertEquals(new MapPoint(5.25, -2.75), saved.center());
    assertEquals(20.0, saved.radius());
  }

  @Test
  void aCircleDrawnWithAHugeRadiusCanStillHaveItsCenterMoved() {
    MapArea area =
        stored(
            MapToolsTestUtils.circle("pond", WORLD, 0, 0, 5)
                .withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(0.5, 0.5), 5000)));
    ShownDialog dialog = manage(area);
    assertEquals("5000", dialog.text("coordinate1").initial());

    save(dialog, corners(dialog, "100 100", "5000"));

    MapArea saved = scenario.store().getArea("pond");
    assertEquals(new MapPoint(100.5, 100.5), saved.center());
    assertEquals(5000.0, saved.radius());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "3001", "4000", "2.5", "ten", ""})
  void aRadiusOutsideOneToThreeThousandOpensTheFormAgain(String radius) {
    MapArea area = stored(MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "5 -3", radius));

    assertEquals(CIRCLE_PROBLEM, alex.lastDialog().body().getFirst());
    assertSame(area, scenario.store().getArea("pond"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "5", "a b", "1.5 2"})
  void aCenterThatIsNotTwoWholeNumbersOpensTheFormAgain(String center) {
    MapArea area = stored(MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, center, "12"));

    assertEquals(CIRCLE_PROBLEM, alex.lastDialog().body().getFirst());
    assertSame(area, scenario.store().getArea("pond"));
  }

  @Test
  void aCenterOnlyChangesTheShapeWhenTheTextChanges() {
    MapArea area = stored(MapToolsTestUtils.circle("pond", WORLD, 5, -3, 12));
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("label", "Pond"));

    assertEquals("Pond", scenario.store().getArea("pond").label());
    assertEquals(area.center(), scenario.store().getArea("pond").center());
    assertEquals(area.radius(), scenario.store().getArea("pond").radius());
  }

  @Test
  void aReshapeIsRefusedWhileSomeoneElseEditsTheClaim() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    DialogPlayer bob = scenario.editor("Bob");
    MapToolsUtils.tryEditing(bob, area);
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "19 19"));

    assertEquals("Bob is editing this claim right now.", MessageTestUtils.nextMessage(alex));
    assertSame(area, scenario.store().getArea("home"));
  }

  @Test
  void aReshapeRestartsTheOwnEditOnTheNewShape() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, area);
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "19 19"));

    assertEquals(
        MapToolsUtils.rectangle(0, 0, 19, 19),
        MapToolsModule.sessions.get(alex.getUniqueId()).toGeometry().points());
  }

  @Test
  void aReshapeCloseToAnotherClaimAsksFirst() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "19 19"));

    assertEquals("Claims nearby", alex.lastDialog().title());
    assertTrue(alex.lastDialog().buttons().contains("Save anyway"));
    assertSame(area, scenario.store().getArea("home"));
  }

  @Test
  void savingAnywaySavesTheReshape() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = manage(area);
    save(dialog, corners(dialog, "0 0", "19 19"));

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(0, 0, 19, 19), scenario.store().getArea("home").points());
    assertEquals("Updated home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void cancellingTheNearbyQuestionGoesBackToTheFormWithWhatWasTyped() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = manage(area);
    save(dialog, corners(dialog, "0 0", "19 19").text("label", "Typed"));

    alex.lastDialog().clickExit(new Response(), alex);

    ShownDialog back = alex.lastDialog();
    assertEquals("Manage claim home", back.title());
    assertEquals("19 19", back.text("coordinate1").initial());
    assertEquals("Typed", back.text("label").initial());
    assertSame(area, scenario.store().getArea("home"));
  }

  @Test
  void theClaimItselfIsNotNearbyWhenReshaping() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    ShownDialog dialog = manage(area);

    save(dialog, corners(dialog, "0 0", "19 19"));

    assertEquals(1, alex.dialogs().size());
    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void aDetailChangeDoesNotAskAboutNearbyClaims() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("label", "New"));

    assertEquals(1, alex.dialogs().size());
    assertEquals("New", scenario.store().getArea("home").label());
  }

  @Test
  void theClaimBeingGoneWhileTheNearbyQuestionIsOpenSaysSo() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 50, 0, 59, 9));
    ShownDialog dialog = manage(area);
    save(dialog, corners(dialog, "0 0", "19 19"));
    scenario.store().removeArea("home");

    alex.lastDialog().click("Save anyway", new Response(), alex);

    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void aPolygonCanStillHaveItsDetailsChanged() {
    MapArea area = stored(MapToolsTestUtils.polygon("shape", WORLD, 0.5, 0.5, 8.5, 0.5, 4.5, 6.5));
    ShownDialog dialog = manage(area);

    save(dialog, dialog.responseWithInitialValues().text("label", "Triangle"));

    MapArea saved = scenario.store().getArea("shape");
    assertEquals("Triangle", saved.label());
    assertEquals(area.points(), saved.points());
  }
}
