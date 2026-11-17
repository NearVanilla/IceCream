package com.nearvanilla.iceCream.modules.mapTools.commands;

import static com.nearvanilla.iceCream.testutil.CommandTestUtils.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
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

/** Creating claims from typed coordinates with a player or from the console. */
class MapToolsCommandCreateTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;
  private static final String BAD_ID = "Ids may only use a-z, 0-9, _ and -, up to 32 characters.";
  private static final String BAD_COLOR =
      "Use a color name such as gold, or a hex code such as ff8800.";
  private static final String NOT_RENDERED =
      "squaremap does not render this world, so it will not show on the map.";

  private Scenario scenario;
  private DialogPlayer alex;
  private ConsoleCommandSenderMock console;
  private final MapToolsCommand command = new MapToolsCommand();

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    console = scenario.server().getConsoleSender();
  }

  @AfterEach
  void tearDown() {
    scenario.close();
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
  void aTypedRectangleIsCreatedInTheOverworld() {
    command.createRect(source(), 10, 20, 19, 29, "home", "gold", "My Home");

    MapArea home = area("home");
    assertNotNull(home);
    assertEquals(WORLD, home.world());
    assertEquals(MapArea.RECT, home.shape());
    assertEquals(MapToolsUtils.rectangle(10, 20, 19, 29), home.points());
    assertEquals("My Home", home.label());
    assertEquals("gold", home.color());
    assertEquals("Created claim home.", next());
    assertEquals(NOT_RENDERED, next());
  }

  @Test
  void theCornersCanBeGivenInAnyOrder() {
    command.createRect(source(), 19, 29, 10, 20, "home", "gold", "My Home");

    assertEquals(MapToolsUtils.rectangle(10, 20, 19, 29), area("home").points());
  }

  @Test
  void aRectangleOfExactlyTheLargestSizeIsAllowed() {
    command.createRect(source(), 0, 0, 2999, 2999, "big", "gold", "Big");

    assertNotNull(area("big"));
  }

  @Test
  void aRectangleLongerThanTheLargestSizeIsRefused() {
    command.createRect(source(), 0, 0, 3000, 5, "wide", "gold", "Wide");
    command.createRect(source(), 0, 0, 5, 3000, "long", "gold", "Long");

    assertEquals("A rectangle can be at most 3000 blocks wide and long.", next());
    assertEquals("A rectangle can be at most 3000 blocks wide and long.", next());
    assertNull(area("wide"));
    assertNull(area("long"));
  }

  @Test
  void theConsoleCanCreateARectangleToo() {
    command.createRect(stack(console), 0, 0, 9, 9, "home", "gold", "Home");

    assertNotNull(area("home"));
    assertEquals("Created claim home.", MessageTestUtils.nextMessage(console));
  }

  @Test
  void aRectangleWithABadIdOrColorOrLabelIsNotCreated() {
    command.createRect(source(), 0, 0, 9, 9, "Bad Id", "gold", "Home");
    command.createRect(source(), 0, 0, 9, 9, "home", "no such color", "Home");
    command.createRect(source(), 0, 0, 9, 9, "home", "gold", "   ");

    assertEquals(BAD_ID, next());
    assertEquals(BAD_COLOR, next());
    assertEquals("The label cannot be empty.", next());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void anIdThatIsTakenIsRefused() {
    scenario.store().putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 1, 1));

    command.createRect(source(), 50, 50, 59, 59, "home", "gold", "Again");

    assertEquals("A claim with id home already exists.", next());
    assertEquals("Label home", area("home").label());
  }

  @Test
  void aRectangleNearAnotherClaimAsksAPlayerFirst() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));

    command.createRect(source(), 0, 0, 9, 9, "home", "gold", "Home");

    assertNull(area("home"));
    ShownDialog question = alex.lastDialog();
    assertEquals("Claims nearby", question.title());
    assertEquals(List.of("Create anyway"), question.buttons());
  }

  @Test
  void confirmingTheQuestionCreatesTheRectangle() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));
    command.createRect(source(), 0, 0, 9, 9, "home", "gold", "Home");
    ShownDialog question = alex.lastDialog();

    question.click("Create anyway", question.responseWithInitialValues(), alex);

    assertNotNull(area("home"));
    assertEquals("Created claim home.", next());
  }

  @Test
  void cancellingTheQuestionCreatesNothing() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));
    command.createRect(source(), 0, 0, 9, 9, "home", "gold", "Home");
    ShownDialog question = alex.lastDialog();

    question.clickExit(question.responseWithInitialValues(), alex);

    assertNull(area("home"));
  }

  @Test
  void theConsoleIsNeverAskedAboutNearbyClaims() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));

    command.createRect(stack(console), 0, 0, 9, 9, "home", "gold", "Home");

    assertNotNull(area("home"));
  }

  // Circles

  @Test
  void aTypedCircleIsCreatedAroundTheMiddleOfItsCenterBlock() {
    command.createCircle(source(), 40, 50, 20, "pond", "blue", "The Pond");

    MapArea pond = area("pond");
    assertNotNull(pond);
    assertEquals(WORLD, pond.world());
    assertEquals(MapArea.CIRCLE, pond.shape());
    assertEquals(new MapPoint(40.5, 50.5), pond.center());
    assertEquals(20.0, pond.radius());
    assertEquals("blue", pond.color());
    assertEquals("Created claim pond.", next());
  }

  @Test
  void aCircleWithBadDetailsIsNotCreated() {
    command.createCircle(source(), 0, 0, 10, "Pond!", "gold", "Pond");
    command.createCircle(source(), 0, 0, 10, "pond", "#12", "Pond");

    assertEquals(BAD_ID, next());
    assertEquals(BAD_COLOR, next());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void aCircleNearAnotherClaimAsksAPlayerFirst() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));

    command.createCircle(source(), 0, 0, 10, "pond", "gold", "Pond");

    assertNull(area("pond"));
    assertEquals("Claims nearby", alex.lastDialog().title());
  }

  // Polygons

  @Test
  void aTypedPolygonIsCreatedThroughTheMiddlesOfItsBlocks() {
    command.createPolygon(source(), "0 0; 10 0; 10 10", "tri", "red", "Triangle");

    MapArea tri = area("tri");
    assertNotNull(tri);
    assertEquals(MapArea.POLYGON, tri.shape());
    assertEquals(WORLD, tri.world());
    assertEquals(
        List.of(new MapPoint(0.5, 0.5), new MapPoint(10.5, 0.5), new MapPoint(10.5, 10.5)),
        tri.points());
    assertEquals("Created claim tri.", next());
  }

  @Test
  void pointsThatDoNotMakeAShapeAreRefusedWithTheUsage() {
    String usage =
        "Use points in quotes, such as \"0 0; 10 0; 10 10\", with at least 3 points that are not in"
            + " one line.";

    command.createPolygon(source(), "0 0; 10 0", "tri", "red", "Triangle");
    command.createPolygon(source(), "0 0; 5 5; 10 10", "tri", "red", "Triangle");
    command.createPolygon(source(), "0 0; x 0; 10 10", "tri", "red", "Triangle");

    assertEquals(usage, next());
    assertEquals(usage, next());
    assertEquals(usage, next());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void aPolygonWithABadIdIsNotCreated() {
    command.createPolygon(source(), "0 0; 10 0; 10 10", "", "red", "Triangle");

    assertEquals(BAD_ID, next());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void aPolygonNearAnotherClaimAsksAPlayerFirst() {
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 30, 0, 39, 9));

    command.createPolygon(source(), "0 0; 10 0; 10 10", "tri", "red", "Triangle");

    assertNull(area("tri"));
    assertEquals("Claims nearby", alex.lastDialog().title());
  }
}
