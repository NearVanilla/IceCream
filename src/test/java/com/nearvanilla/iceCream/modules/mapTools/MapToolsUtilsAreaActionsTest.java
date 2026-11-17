package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Creating, saving, renaming and deleting areas, and what that does to players editing them. */
class MapToolsUtilsAreaActionsTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private Scenario scenario;
  private DialogPlayer alex;
  private DialogPlayer bob;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    bob = scenario.editor("Bob");
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private MapArea stored(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private EditSession session(DialogPlayer player) {
    return MapToolsModule.sessions.get(player.getUniqueId());
  }

  private MapAreasStore reloaded() throws IOException {
    MapAreasStore fresh = new MapAreasStore(MapToolsTestUtils.storeFile());
    fresh.load();
    return fresh;
  }

  private static AreaGeometry rectShape(String world, int x1, int z1, int x2, int z2) {
    return new AreaGeometry(
        world, MapToolsUtils.rectangle(x1, z1, x2, z2), MapArea.RECT, null, null);
  }

  private boolean create(String id, String color, String label) {
    return MapToolsUtils.createArea(
        alex, id, rectShape(WORLD, 0, 0, 9, 9), color, label, List.of(), null, null, null);
  }

  @Test
  void createAreaStoresAClaimWithTheGivenDetails() throws IOException {
    boolean created =
        MapToolsUtils.createArea(
            alex,
            "home",
            rectShape(WORLD, 0, 0, 9, 9),
            "FF8800",
            "Home sweet home",
            List.of("Alex", "Sam"),
            "A cozy place",
            0.4,
            null);

    assertTrue(created);
    MapArea area = scenario.store().getArea("home");
    assertNotNull(area);
    assertEquals(WORLD, area.world());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), area.points());
    assertEquals(MapArea.RECT, area.shape());
    assertEquals("Home sweet home", area.label());
    assertEquals("A cozy place", area.description());
    assertEquals(List.of("Alex", "Sam"), area.owners());
    assertEquals("#ff8800", area.color());
    assertEquals(0.4, area.opacity());
    assertNull(area.icon());
    assertFalse(area.isHidden());
    assertEquals(area, reloaded().getArea("home").withHidden(false));
  }

  @Test
  void createAreaTellsTheSenderWhatWasCreated() {
    create("home", "gold", "Home");

    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void createAreaCallsAMarkerAMarker() {
    MapPoint spot = MapToolsUtils.blockCenter(5, 5);
    AreaGeometry geometry = new AreaGeometry(WORLD, List.of(spot), MapArea.MARKER, spot, null);

    boolean created =
        MapToolsUtils.createArea(
            alex, "spot", geometry, "gold", "Spot", List.of(), null, null, "pin");

    assertTrue(created);
    assertEquals("Created marker spot.", MessageTestUtils.nextMessage(alex));
    assertEquals("pin", scenario.store().getArea("spot").icon());
    assertTrue(scenario.store().getArea("spot").isMarker());
  }

  @Test
  void createAreaWarnsWhenSquaremapDoesNotRenderTheWorld() {
    create("home", "gold", "Home");

    assertEquals("Created claim home.", MessageTestUtils.nextMessage(alex));
    assertEquals(
        "squaremap does not render this world, so it will not show on the map.",
        MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void createAreaRefusesAnInvalidIdAndTellsWhy() {
    assertFalse(create("Bad Id", "gold", "Home"));

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void createAreaRefusesAnIdThatIsTaken() {
    MapArea existing = stored(MapToolsTestUtils.rect("home", WORLD, 50, 50, 59, 59));

    assertFalse(create("home", "gold", "Other"));

    assertEquals("A claim with id home already exists.", MessageTestUtils.nextMessage(alex));
    assertSame(existing, scenario.store().getArea("home"));
  }

  @Test
  void createAreaRefusesAnEmptyLabel() {
    assertFalse(create("home", "gold", "  "));

    assertEquals("The label cannot be empty.", MessageTestUtils.nextMessage(alex));
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void createAreaRefusesAColorThatIsNotOne() {
    assertFalse(create("home", "banana", "Home"));

    assertEquals(
        "Use a color name such as gold, or a hex code such as ff8800.",
        MessageTestUtils.nextMessage(alex));
    assertTrue(scenario.store().getAreas().isEmpty());
  }

  @Test
  void createAreaRefusesALabelThatIsTooLong() {
    assertFalse(
        MapToolsUtils.createArea(
            alex,
            "home",
            rectShape(WORLD, 0, 0, 9, 9),
            "gold",
            "x".repeat(65),
            List.of(),
            null,
            null,
            null));

    assertEquals("Labels can be up to 64 characters.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void createAreaCanBeCalledByTheConsole() {
    ConsoleCommandSenderMock console = scenario.server().getConsoleSender();

    boolean created =
        MapToolsUtils.createArea(
            console,
            "home",
            rectShape(WORLD, 0, 0, 9, 9),
            "gold",
            "Home",
            List.of(),
            null,
            null,
            null);

    assertTrue(created);
    assertEquals("Created claim home.", MessageTestUtils.nextMessage(console));
  }

  @Test
  void createAreaWritesTheFile() throws IOException {
    create("home", "gold", "Home");

    assertEquals(scenario.store().getArea("home"), reloaded().getArea("home").withHidden(false));
  }

  @Test
  void saveAreaStoresTheAreaAndWritesTheFile() throws IOException {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea changed = home.withLabel("Renamed label");

    MapToolsUtils.saveArea(changed);

    assertSame(changed, scenario.store().getArea("home"));
    assertEquals("Renamed label", reloaded().getArea("home").label());
  }

  @Test
  void saveAreaRestartsTheEditOfAPlayerWithoutChangesOnTheSavedShape() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);
    EditSession before = session(alex);
    MapArea bigger =
        home.withGeometry(
            new AreaGeometry(
                WORLD, MapToolsUtils.rectangle(0, 0, 19, 19), MapArea.RECT, null, null));

    MapToolsUtils.saveArea(bigger);

    assertNotSame(before, session(alex));
    assertEquals("home", session(alex).getAreaId());
    assertEquals(MapToolsUtils.rectangle(0, 0, 19, 19), session(alex).toGeometry().points());
    assertFalse(session(alex).hasChanges());
  }

  @Test
  void saveAreaLeavesTheEditOfAPlayerWithUnsavedChangesAlone() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);
    EditSession before = session(alex);
    before.moveRectCorner(0, scenario.overworldBlock(-5, 63, -5));
    before.setChanged(true);

    MapToolsUtils.saveArea(home.withLabel("New"));

    assertSame(before, session(alex));
    assertTrue(before.hasChanges());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void saveAreaLeavesPlayersEditingOtherAreasAlone() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(bob, shop);
    MapToolsUtils.startEditing(alex, EditSession.view());
    EditSession bobs = session(bob);
    EditSession alexs = session(alex);

    MapToolsUtils.saveArea(home.withLabel("New"));

    assertSame(bobs, session(bob));
    assertSame(alexs, session(alex));
  }

  @Test
  void deleteAreaRemovesTheAreaFromTheStoreAndTheFile() throws IOException {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));

    MapArea deleted = MapToolsUtils.deleteArea("home");

    assertSame(home, deleted);
    assertNull(scenario.store().getArea("home"));
    assertNotNull(scenario.store().getArea("shop"));
    MapAreasStore fromFile = reloaded();
    assertNull(fromFile.getArea("home"));
    assertNotNull(fromFile.getArea("shop"));
  }

  @Test
  void deleteAreaOfAnIdThatDoesNotExistChangesNothing() {
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(alex, shop);
    EditSession before = session(alex);

    assertNull(MapToolsUtils.deleteArea("home"));

    assertSame(before, session(alex));
    assertEquals(1, scenario.store().getAreas().size());
  }

  @Test
  void deleteAreaTakesEditorsBackToViewMode() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);

    MapToolsUtils.deleteArea("home");

    assertTrue(session(alex).isViewing());
    assertTrue(MapToolsUtils.isEditing(alex));
  }

  @Test
  void deleteAreaDoesNotWarnAboutTheUnsavedChangesOfTheDeletedArea() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);
    session(alex).moveRectCorner(0, scenario.overworldBlock(-5, 63, -5));
    session(alex).setChanged(true);

    MapToolsUtils.deleteArea("home");

    assertTrue(session(alex).isViewing());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void deleteAreaLeavesPlayersEditingOtherAreasAlone() {
    stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(bob, shop);
    EditSession bobs = session(bob);

    MapToolsUtils.deleteArea("home");

    assertSame(bobs, session(bob));
  }

  @Test
  void renameAreaMovesTheAreaToTheNewIdInTheStoreAndTheFile() throws IOException {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    MapArea renamed = MapToolsUtils.renameArea(home, "house");

    assertEquals(home.withId("house"), renamed);
    assertNull(scenario.store().getArea("home"));
    assertSame(renamed, scenario.store().getArea("house"));
    MapAreasStore fromFile = reloaded();
    assertNull(fromFile.getArea("home"));
    assertNotNull(fromFile.getArea("house"));
  }

  @Test
  void renameAreaContinuesTheEditOfAPlayerWithoutChangesUnderTheNewId() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);

    MapToolsUtils.renameArea(home, "house");

    assertEquals("house", session(alex).getAreaId());
    assertFalse(session(alex).hasChanges());
  }

  @Test
  void renameAreaLeavesTheEditOfAPlayerWithUnsavedChangesAlone() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);
    EditSession before = session(alex);
    before.setChanged(true);

    MapToolsUtils.renameArea(home, "house");

    assertSame(before, session(alex));
    assertEquals("home", session(alex).getAreaId());
  }

  @Test
  void renameAreaLeavesPlayersEditingOtherAreasAlone() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(bob, shop);
    EditSession bobs = session(bob);

    MapToolsUtils.renameArea(home, "house");

    assertSame(bobs, session(bob));
    assertEquals("shop", session(bob).getAreaId());
  }

  @Test
  void renameAreaToTheSameIdJustSavesIt() throws IOException {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    MapArea renamed = MapToolsUtils.renameArea(home.withLabel("New"), "home");

    assertEquals("New", renamed.label());
    assertEquals(1, scenario.store().getAreas().size());
    assertEquals("New", reloaded().getArea("home").label());
  }
}
