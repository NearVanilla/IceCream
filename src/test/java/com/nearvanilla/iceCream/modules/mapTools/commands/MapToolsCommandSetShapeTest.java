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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Replacing the shape of a claim or the position of a marker with typed coordinates. */
class MapToolsCommandSetShapeTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String CIRCLE_ONLY = "Only circles have a center and radius.";

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
    store(MapToolsTestUtils.circle("pond", WORLD, 1000, 1000, 20));
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

  private String next() {
    return MessageTestUtils.nextMessage(alex);
  }

  private MapArea area(String id) {
    return scenario.store().getArea(id);
  }

  // Rectangles

  @Test
  void aRectangleReplacesTheShapeOfAClaim() {
    command.setRect(source(), Kind.CLAIM, "home", 100, 200, 109, 209);

    MapArea home = area("home");
    assertEquals(MapArea.RECT, home.shape());
    assertEquals(MapToolsUtils.rectangle(100, 200, 109, 209), home.points());
    assertEquals("Updated the shape of home.", next());
  }

  @Test
  void aRectangleKeepsEverythingElseAboutTheClaim() {
    store(area("home").withColor("blue").withDescription("Nice"));

    command.setRect(source(), Kind.CLAIM, "home", 100, 200, 109, 209);

    assertEquals("blue", area("home").color());
    assertEquals("Nice", area("home").description());
    assertEquals("Label home", area("home").label());
    assertEquals(WORLD, area("home").world());
  }

  @Test
  void aRectangleTurnsACircleIntoARectangle() {
    command.setRect(source(), Kind.CLAIM, "pond", 2000, 2000, 2009, 2009);

    assertEquals(MapArea.RECT, area("pond").shape());
    assertNull(area("pond").center());
    assertNull(area("pond").radius());
  }

  @Test
  void aRectangleThatIsTooLargeIsRefused() {
    command.setRect(source(), Kind.CLAIM, "home", 0, 0, 3000, 5);

    assertEquals("A rectangle can be at most 3000 blocks wide and long.", next());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  @Test
  void aRectangleForAnUnknownClaimSaysSo() {
    command.setRect(source(), Kind.CLAIM, "nobody", 0, 0, 9, 9);

    assertEquals("There is no claim called nobody.", next());
  }

  @Test
  void aRectangleForAMarkerIsRefused() {
    command.setRect(source(), Kind.MARKER, "spot", 0, 0, 9, 9);

    assertEquals("That only works for claims.", next());
    assertEquals(MapArea.MARKER, area("spot").shape());
  }

  @Test
  void aRectangleForAClaimNamedAsAMarkerSaysThereIsNoSuchMarker() {
    command.setRect(source(), Kind.MARKER, "home", 0, 0, 9, 9);

    assertEquals("There is no marker called home.", next());
  }

  @Test
  void theConsoleCanReshapeAClaim() {
    command.setRect(stack(console), Kind.CLAIM, "home", 100, 200, 109, 209);

    assertEquals(MapToolsUtils.rectangle(100, 200, 109, 209), area("home").points());
    assertEquals("Updated the shape of home.", MessageTestUtils.nextMessage(console));
  }

  // Circles, radii and centers

  @Test
  void aCircleReplacesTheShapeOfAClaim() {
    command.setCircle(source(), Kind.CLAIM, "home", 500, 600, 15);

    MapArea home = area("home");
    assertEquals(MapArea.CIRCLE, home.shape());
    assertEquals(new MapPoint(500.5, 600.5), home.center());
    assertEquals(15.0, home.radius());
    assertEquals("Updated the shape of home.", next());
  }

  @Test
  void aCircleForAMarkerIsRefused() {
    command.setCircle(source(), Kind.MARKER, "spot", 0, 0, 5);

    assertEquals("That only works for claims.", next());
  }

  @Test
  void theRadiusResizesACircleKeepingItsCenter() {
    command.setRadius(source(), Kind.CLAIM, "pond", 35);

    MapArea pond = area("pond");
    assertEquals(35.0, pond.radius());
    assertEquals(new MapPoint(1000.5, 1000.5), pond.center());
    assertEquals("Updated the shape of pond.", next());
  }

  @Test
  void theRadiusOfAClaimThatIsNotACircleIsRefused() {
    command.setRadius(source(), Kind.CLAIM, "home", 35);

    assertEquals(CIRCLE_ONLY, next());
    assertEquals(MapArea.RECT, area("home").shape());
  }

  @Test
  void theRadiusForAMarkerIsRefused() {
    command.setRadius(source(), Kind.MARKER, "spot", 5);

    assertEquals("That only works for claims.", next());
  }

  @Test
  void theCenterMovesACircleKeepingItsSize() {
    command.setCenter(source(), Kind.CLAIM, "pond", 2000, 3000);

    MapArea pond = area("pond");
    assertEquals(new MapPoint(2000.5, 3000.5), pond.center());
    assertEquals(20.0, pond.radius());
    assertEquals("Updated the shape of pond.", next());
  }

  @Test
  void theCenterOfAClaimThatIsNotACircleIsRefused() {
    command.setCenter(source(), Kind.CLAIM, "home", 5, 5);

    assertEquals(CIRCLE_ONLY, next());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  // Markers

  @Test
  void movingAMarkerPutsItOnTheMiddleOfTheBlock() {
    command.setMove(source(), Kind.MARKER, "spot", 40, -5);

    MapArea spot = area("spot");
    assertEquals(new MapPoint(40.5, -4.5), spot.center());
    assertEquals(List.of(new MapPoint(40.5, -4.5)), spot.points());
    assertEquals(MapArea.MARKER, spot.shape());
    assertEquals("Updated the position of spot.", next());
  }

  @Test
  void movingAClaimIsRefused() {
    command.setMove(source(), Kind.CLAIM, "home", 40, 40);

    assertEquals("That only works for markers.", next());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  @Test
  void movingAMarkerThatDoesNotExistSaysSo() {
    command.setMove(source(), Kind.MARKER, "nobody", 1, 1);

    assertEquals("There is no marker called nobody.", next());
  }

  @Test
  void movingAMarkerNeverAsksAboutNearbyClaims() {
    store(MapToolsTestUtils.rect("shop", WORLD, 40, 40, 49, 49));

    command.setMove(source(), Kind.MARKER, "spot", 41, 41);

    assertTrue(alex.dialogs().isEmpty());
    assertEquals(new MapPoint(41.5, 41.5), area("spot").center());
  }

  // Nearby claims

  @Test
  void reshapingNearAnotherClaimAsksAPlayerFirst() {
    store(MapToolsTestUtils.rect("shop", WORLD, 60, 10, 69, 19));

    command.setRect(source(), Kind.CLAIM, "home", 30, 10, 39, 19);

    ShownDialog question = alex.lastDialog();
    assertEquals("Claims nearby", question.title());
    assertEquals(List.of("Save anyway"), question.buttons());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  @Test
  void confirmingTheQuestionSavesTheNewShape() {
    store(MapToolsTestUtils.rect("shop", WORLD, 60, 10, 69, 19));
    command.setRect(source(), Kind.CLAIM, "home", 30, 10, 39, 19);
    ShownDialog question = alex.lastDialog();

    question.click("Save anyway", question.responseWithInitialValues(), alex);

    assertEquals(MapToolsUtils.rectangle(30, 10, 39, 19), area("home").points());
    assertEquals("Updated the shape of home.", next());
  }

  @Test
  void theClaimBeingReshapedDoesNotCountAsNearItself() {
    command.setRect(source(), Kind.CLAIM, "home", 12, 12, 21, 21);

    assertTrue(alex.dialogs().isEmpty());
    assertEquals(MapToolsUtils.rectangle(12, 12, 21, 21), area("home").points());
  }

  @Test
  void theClaimIsGoneByTheTimeTheQuestionIsAnswered() {
    store(MapToolsTestUtils.rect("shop", WORLD, 60, 10, 69, 19));
    command.setRect(source(), Kind.CLAIM, "home", 30, 10, 39, 19);
    ShownDialog question = alex.lastDialog();
    scenario.store().removeArea("home");

    question.click("Save anyway", question.responseWithInitialValues(), alex);

    assertNull(area("home"));
    assertEquals("There is no claim called home.", next());
  }

  // Other people editing

  @Test
  void aClaimSomeoneElseIsEditingCannotBeReshaped() {
    MapToolsUtils.startEditing(sam, EditSession.edit(area("home")));

    command.setRect(source(), Kind.CLAIM, "home", 100, 100, 109, 109);

    assertEquals("Sam is editing this claim right now.", next());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  @Test
  void aClaimWhoseOwnEditHasUnsavedChangesCannotBeReshaped() {
    EditSession mine = EditSession.edit(area("home"));
    mine.setChanged(true);
    MapToolsUtils.startEditing(alex, mine);

    command.setRect(source(), Kind.CLAIM, "home", 100, 100, 109, 109);

    assertEquals("Save or stop your own changes to this claim first.", next());
    assertEquals(MapToolsUtils.rectangle(10, 10, 19, 19), area("home").points());
  }

  @Test
  void aClaimWhoseOwnEditHasNoChangesIsReshapedAndTheEditReloaded() {
    MapToolsUtils.startEditing(alex, EditSession.edit(area("home")));

    command.setRect(source(), Kind.CLAIM, "home", 100, 100, 109, 109);

    assertEquals(MapToolsUtils.rectangle(100, 100, 109, 109), area("home").points());
    EditSession session = MapToolsModule.sessions.get(alex.getUniqueId());
    assertTrue(session.isEditingArea());
    assertEquals("home", session.getAreaId());
    assertEquals(100, session.definingBlocks().getFirst().getBlockX());
  }

  @Test
  void aMarkerSomeoneElseIsEditingCannotBeMoved() {
    MapToolsUtils.startEditing(sam, EditSession.edit(area("spot")));

    command.setMove(source(), Kind.MARKER, "spot", 40, 40);

    assertEquals("Sam is editing this claim right now.", next());
    assertEquals(new MapPoint(3.5, 3.5), area("spot").center());
  }
}
