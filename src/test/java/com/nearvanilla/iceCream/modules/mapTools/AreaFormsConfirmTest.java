package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The questions asked before creating near other claims and before deleting. */
class AreaFormsConfirmTest {
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

  private static AreaGeometry square() {
    return new AreaGeometry(WORLD, MapToolsUtils.rectangle(0, 0, 9, 9), MapArea.RECT, null, null);
  }

  /** A claim 10 blocks to the right of the square, with the given id. */
  private MapArea neighbour(String id, int gap) {
    return stored(MapToolsTestUtils.rect(id, WORLD, 9 + gap + 1, 0, 9 + gap + 10, 9));
  }

  @Test
  void withNoClaimNearbyTheActionRunsAtOnce() {
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    assertEquals(1, ran.get());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void aClaimBeyondTheDistanceDoesNotCount() {
    stored(MapToolsTestUtils.rect("far", WORLD, 400, 0, 409, 9));
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    assertEquals(1, ran.get());
    assertTrue(alex.dialogs().isEmpty());
  }

  @Test
  void theConsoleIsNeverAsked() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(
        scenario.server().getConsoleSender(), square(), null, ran::incrementAndGet);

    assertEquals(1, ran.get());
  }

  @Test
  void aPlayerIsAskedWhenAClaimIsNearby() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    assertEquals(0, ran.get());
    ShownDialog dialog = alex.lastDialog();
    assertEquals("Claims nearby", dialog.title());
    assertEquals(
        List.of(
            "This claim overlaps or is within 300 blocks of 1 other claim:",
            "Label shop (shop)\nOwner(s): none · 10 blocks away"),
        dialog.body());
    assertEquals(List.of("Create anyway"), dialog.buttons());
    assertEquals("Cancel", dialog.exitLabel());
    assertEquals(1, dialog.columns());
  }

  @Test
  void theQuestionUsesTheConfiguredDistance() {
    scenario.configure("modules:\n  maptools:\n    nearby-claim-distance: 25\n");
    neighbour("shop", 10);

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    assertEquals(
        "This claim overlaps or is within 25 blocks of 1 other claim:",
        alex.lastDialog().body().getFirst());
  }

  @Test
  void aClaimThatOverlapsSaysSo() {
    stored(MapToolsTestUtils.rect("over", WORLD, 5, 5, 14, 14));

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    assertEquals("Label over (over)\nOwner(s): none · overlaps", alex.lastDialog().body().get(1));
  }

  @Test
  void theOwnersOfANearbyClaimAreListed() {
    stored(neighbourOf("shop", 10).withOwners(List.of("Alex", "Sam")));

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    assertEquals(
        "Label shop (shop)\nOwner(s): Alex, Sam · 10 blocks away", alex.lastDialog().body().get(1));
  }

  private MapArea neighbourOf(String id, int gap) {
    return MapToolsTestUtils.rect(id, WORLD, 9 + gap + 1, 0, 9 + gap + 10, 9);
  }

  @Test
  void theDistanceIsRoundedToWholeBlocks() {
    // The corner of the claim is 3 blocks across and 4 blocks up from the square.
    stored(MapToolsTestUtils.rect("diagonal", WORLD, 13, 14, 20, 20));

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    assertEquals(
        "Label diagonal (diagonal)\nOwner(s): none · 5 blocks away",
        alex.lastDialog().body().get(1));
  }

  @Test
  void severalClaimsAreCountedAndListedClosestFirst() {
    neighbour("far", 30);
    neighbour("near", 10);
    neighbour("middle", 20);

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    List<String> body = alex.lastDialog().body();
    assertEquals("This claim overlaps or is within 300 blocks of 3 other claims:", body.get(0));
    assertTrue(body.get(1).startsWith("Label near (near)"));
    assertTrue(body.get(2).startsWith("Label middle (middle)"));
    assertTrue(body.get(3).startsWith("Label far (far)"));
    assertEquals(4, body.size());
  }

  @Test
  void sixClaimsAreAllListed() {
    for (int i = 0; i < 6; i++) neighbour("c" + i, 10 + i);

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    List<String> body = alex.lastDialog().body();
    assertEquals(7, body.size());
    assertEquals("This claim overlaps or is within 300 blocks of 6 other claims:", body.get(0));
    assertTrue(body.getLast().startsWith("Label c5 (c5)"));
  }

  @Test
  void moreThanSixClaimsAreSummedUp() {
    for (int i = 0; i < 9; i++) neighbour("c" + i, 10 + i);

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    List<String> body = alex.lastDialog().body();
    assertEquals("This claim overlaps or is within 300 blocks of 9 other claims:", body.get(0));
    assertEquals(8, body.size());
    assertTrue(body.get(6).startsWith("Label c5 (c5)"));
    assertEquals("...and 3 more.", body.getLast());
  }

  @Test
  void sevenClaimsLeaveOneMore() {
    for (int i = 0; i < 7; i++) neighbour("c" + i, 10 + i);

    AreaForms.confirmNearby(alex, square(), null, () -> {});

    assertEquals("...and 1 more.", alex.lastDialog().body().getLast());
  }

  @Test
  void markersAreNotListed() {
    stored(MapToolsTestUtils.marker("spot", WORLD, 5, 5));
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    assertEquals(1, ran.get());
  }

  @Test
  void theClaimBeingChangedIsNotListed() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AtomicInteger ran = new AtomicInteger();

    AreaForms.confirmNearby(alex, square(), "home", ran::incrementAndGet);

    assertEquals(1, ran.get());
  }

  @Test
  void theButtonSaysSaveAnywayWhenAnExistingClaimIsChanged() {
    neighbour("shop", 10);
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    AreaForms.confirmNearby(alex, square(), "home", () -> {});

    assertEquals(List.of("Save anyway"), alex.lastDialog().buttons());
  }

  @Test
  void clickingTheButtonRunsTheAction() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();
    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    alex.lastDialog().click("Create anyway", new Response(), alex);

    assertEquals(1, ran.get());
  }

  @Test
  void cancellingDoesNothingWithoutAWayBack() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();
    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet);

    assertFalse(alex.lastDialog().exitHasAction());
    alex.lastDialog().clickExit(new Response(), alex);

    assertEquals(0, ran.get());
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void cancellingGoesBackWhenThereIsAWayBack() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();
    AtomicInteger back = new AtomicInteger();
    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet, back::incrementAndGet);

    assertTrue(alex.lastDialog().exitHasAction());
    alex.lastDialog().clickExit(new Response(), alex);

    assertEquals(0, ran.get());
    assertEquals(1, back.get());
  }

  @Test
  void aStrangerCannotAnswerTheQuestion() {
    neighbour("shop", 10);
    AtomicInteger ran = new AtomicInteger();
    AtomicInteger back = new AtomicInteger();
    AreaForms.confirmNearby(alex, square(), null, ran::incrementAndGet, back::incrementAndGet);
    DialogPlayer stranger = DialogTestUtils.addPlayer(scenario.server(), "Stranger");

    alex.lastDialog().click("Create anyway", new Response(), stranger);
    alex.lastDialog().clickExit(new Response(), stranger);

    assertEquals(0, ran.get());
    assertEquals(0, back.get());
  }

  @Test
  void deletingIsAskedWithTheClaimAndItsShape() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    AreaForms.confirmDelete(alex, "home");

    ShownDialog dialog = alex.lastDialog();
    assertEquals("Delete home?", dialog.title());
    assertEquals(List.of("Label home: rect 0 0 9 9", "This cannot be undone."), dialog.body());
    assertEquals(List.of("Delete", "Cancel"), dialog.buttons());
  }

  @Test
  void theQuestionShowsTheShapeOfAMarkerAndOfACircle() {
    stored(MapToolsTestUtils.marker("spot", WORLD, 3, -4));
    stored(MapToolsTestUtils.circle("pond", WORLD, 5, 6, 7));

    AreaForms.confirmDelete(alex, "spot");
    AreaForms.confirmDelete(alex, "pond");

    List<ShownDialog> dialogs = alex.dialogs();
    assertEquals("Label spot: marker 3 -4", dialogs.get(0).body().getFirst());
    assertEquals("Label pond: circle 5 6 7", dialogs.get(1).body().getFirst());
  }

  @Test
  void aQuestionAboutAnIdThatDoesNotExistStillAsks() {
    AreaForms.confirmDelete(alex, "ghost");

    assertEquals("Delete ghost?", alex.lastDialog().title());
    assertEquals("This cannot be undone.", alex.lastDialog().body().getLast());
  }

  @Test
  void confirmingDeletesTheClaimAndTellsThePlayer() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.confirmDelete(alex, "home");

    alex.lastDialog().click("Delete", new Response(), alex);

    assertNull(scenario.store().getArea("home"));
    assertEquals(
        "Deleted home (Label home). rect 0 0 9 9 in minecraft:overworld",
        MessageTestUtils.nextMessage(alex));
  }

  @Test
  void deletingAClaimThatIsAlreadyGoneSaysSo() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.confirmDelete(alex, "home");
    scenario.store().removeArea("home");

    alex.lastDialog().click("Delete", new Response(), alex);

    assertEquals("This claim no longer exists.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void deletingSendsTheEditorsOfTheClaimBackToViewing() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, area);
    AreaForms.confirmDelete(alex, "home");

    alex.lastDialog().click("Delete", new Response(), alex);

    assertTrue(MapToolsModule.sessions.get(alex.getUniqueId()).isViewing());
  }

  @Test
  void cancellingTheDeleteQuestionOnItsOwnJustClosesIt() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.confirmDelete(alex, "home");

    assertFalse(alex.lastDialog().hasAction("Cancel"));
    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void aStrangerCannotConfirmADelete() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.confirmDelete(alex, "home");
    DialogPlayer stranger = DialogTestUtils.addPlayer(scenario.server(), "Stranger");

    alex.lastDialog().click("Delete", new Response(), stranger);

    assertNotNull(scenario.store().getArea("home"));
    assertNull(stranger.nextComponentMessage());
  }

  @Test
  void deletingFromTheManageFormAsksFirstAndCancelGoesBackToTheForm() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.openManage(alex, area);
    ShownDialog form = alex.lastDialog();

    form.click("Delete", form.responseWithInitialValues(), alex);

    ShownDialog question = alex.lastDialog();
    assertEquals("Delete home?", question.title());
    assertTrue(question.hasAction("Cancel"));
    assertNotNull(scenario.store().getArea("home"));

    question.click("Cancel", new Response(), alex);

    assertEquals("Manage claim home", alex.lastDialog().title());
    assertEquals("home", alex.lastDialog().text("area_id").initial());
  }

  @Test
  void deletingFromTheManageFormDeletesTheClaim() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.openManage(alex, area);
    ShownDialog form = alex.lastDialog();
    form.click("Delete", form.responseWithInitialValues(), alex);

    alex.lastDialog().click("Delete", new Response(), alex);

    assertNull(scenario.store().getArea("home"));
  }

  @Test
  void goingBackToTheFormAfterTheClaimWasDeletedOpensNothing() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.openManage(alex, area);
    ShownDialog form = alex.lastDialog();
    form.click("Delete", form.responseWithInitialValues(), alex);
    ShownDialog question = alex.lastDialog();
    scenario.store().removeArea("home");

    question.click("Cancel", new Response(), alex);

    assertEquals(2, alex.dialogs().size());
  }

  @Test
  void theFormAfterCancellingShowsTheClaimAsItIsNow() {
    MapArea area = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    AreaForms.openManage(alex, area);
    ShownDialog form = alex.lastDialog();
    form.click("Delete", form.responseWithInitialValues(), alex);
    ShownDialog question = alex.lastDialog();
    scenario.store().putArea(area.withLabel("Changed meanwhile"));

    question.click("Cancel", new Response(), alex);

    assertEquals("Changed meanwhile", alex.lastDialog().text("label").initial());
  }
}
