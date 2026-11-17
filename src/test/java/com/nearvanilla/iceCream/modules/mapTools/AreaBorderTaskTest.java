package com.nearvanilla.iceCream.modules.mapTools;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The task that runs every half second: the action bar of players in edit mode, redrawing for
 * players who moved, and ending edit mode for players who lost the permission.
 */
class AreaBorderTaskTest {
  private DisplayScenario drawing;
  private Viewer alex;
  private final AreaBorderTask task = new AreaBorderTask();

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    alex = drawing.viewer();
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private void put(Viewer player, EditSession session) {
    MapToolsModule.sessions.put(player.getUniqueId(), session);
  }

  private static String statusOf(PlayerMock player) {
    return MessageTestUtils.plainText(player.nextActionBar());
  }

  private EditSession editing(MapAreasStore.MapArea area) {
    drawing.stored(area);
    return EditSession.edit(area);
  }

  // The action bar

  @Test
  void aPlayerInEditModeSeesEditModeOnTheActionBar() {
    put(alex, EditSession.view());

    task.run();

    assertEquals("Edit mode", statusOf(alex));
    assertNull(alex.nextActionBar());
  }

  @Test
  void theStatusIsYellow() {
    put(alex, EditSession.view());

    task.run();

    assertEquals(
        MiniMessage.miniMessage().deserialize("<yellow>Edit mode</yellow>"), alex.nextActionBar());
  }

  @Test
  void aNewShapeShowsItsKindAsAMode() {
    put(alex, EditSession.create(Mode.POLYGON));

    task.run();

    assertEquals("Polygon mode", statusOf(alex));
  }

  @Test
  void everyKindOfShapeHasItsOwnLabel() {
    for (Mode mode : Mode.values()) {
      put(alex, EditSession.create(mode));

      task.run();

      assertEquals(mode.label() + " mode", statusOf(alex));
    }
  }

  @Test
  void aNewCircleShowsItsRadiusOnceItHasOne() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    put(alex, session);
    task.run();
    assertEquals("Circle mode", statusOf(alex));

    session.setRadius(15);
    task.run();

    assertEquals("Circle mode, radius 15", statusOf(alex));
  }

  @Test
  void anAreaBeingEditedShowsItsId() {
    put(alex, editing(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19)));

    task.run();

    assertEquals("Editing home", statusOf(alex));
  }

  @Test
  void aCircleBeingEditedShowsItsRadius() {
    put(alex, editing(MapToolsTestUtils.circle("pond", DRAWING, 40, 40, 20)));

    task.run();

    assertEquals("Editing pond, radius 20", statusOf(alex));
  }

  @Test
  void unsavedChangesAreMentionedWithHowToSaveThem() {
    EditSession session = editing(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    session.setChanged(true);
    put(alex, session);

    task.run();

    assertEquals("Editing home (unsaved changes, /maptools save)", statusOf(alex));
  }

  @Test
  void aCircleWithUnsavedChangesShowsTheRadiusBeforeTheWarning() {
    EditSession session = editing(MapToolsTestUtils.circle("pond", DRAWING, 40, 40, 20));
    session.setChanged(true);
    put(alex, session);

    task.run();

    assertEquals("Editing pond, radius 20 (unsaved changes, /maptools save)", statusOf(alex));
  }

  @Test
  void aPlayerNotInEditModeGetsNoActionBar() {
    task.run();

    assertNull(alex.nextActionBar());
  }

  @Test
  void everyPlayerInEditModeGetsTheirOwnStatus() {
    Viewer sam = drawing.addViewer("Sam");
    put(alex, EditSession.view());
    put(sam, EditSession.create(Mode.RECT));

    task.run();

    assertEquals("Edit mode", statusOf(alex));
    assertEquals("Rectangle mode", statusOf(sam));
  }

  @Test
  void theStatusFollowsTheSessionFromOneRunToTheNext() {
    put(alex, EditSession.view());
    task.run();
    assertEquals("Edit mode", statusOf(alex));

    put(alex, EditSession.create(Mode.MARKER));
    task.run();

    assertEquals("Marker mode", statusOf(alex));
  }

  // Players without the permission

  @Test
  void aPlayerWhoLostThePermissionIsTakenOutOfEditMode() {
    put(alex, EditSession.view());
    alex.addAttachment(IceCream.instance, MapToolsUtils.PERMISSION, false);

    task.run();

    assertFalse(MapToolsUtils.isEditing(alex));
    assertNull(alex.nextActionBar());
  }

  @Test
  void aPlayerWhoLostThePermissionLosesTheirDrawingToo() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);
    MapToolsUtils.startEditing(alex, EditSession.view());
    List<BlockDisplay> drawn = drawing.world().displays();
    assertFalse(drawn.isEmpty());
    alex.addAttachment(IceCream.instance, MapToolsUtils.PERMISSION, false);

    task.run();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aPlayerWhoNeverHadThePermissionIsTakenOutOfEditModeToo() {
    PlayerMock bob = drawing.server().addPlayer("Bob");
    MapToolsModule.sessions.put(bob.getUniqueId(), EditSession.view());

    task.run();

    assertFalse(MapToolsUtils.isEditing(bob));
    assertNull(bob.nextActionBar());
  }

  @Test
  void aPlayerWithoutThePermissionWhoIsNotEditingIsLeftAlone() {
    PlayerMock bob = drawing.server().addPlayer("Bob");

    task.run();

    assertFalse(MapToolsUtils.isEditing(bob));
    assertNull(bob.nextActionBar());
    assertNull(bob.nextMessage());
  }

  @Test
  void losingThePermissionWithUnsavedChangesTellsThePlayerTheyWereDiscarded() {
    PlayerMock bob = drawing.server().addPlayer("Bob");
    EditSession session = editing(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    session.setChanged(true);
    MapToolsModule.sessions.put(bob.getUniqueId(), session);

    task.run();

    assertEquals("Discarded the unsaved changes to home.", MessageTestUtils.nextMessage(bob));
  }

  @Test
  void onePlayerLosingThePermissionDoesNotAffectTheOthers() {
    PlayerMock bob = drawing.server().addPlayer("Bob");
    MapToolsModule.sessions.put(bob.getUniqueId(), EditSession.view());
    put(alex, EditSession.view());

    task.run();

    assertFalse(MapToolsUtils.isEditing(bob));
    assertTrue(MapToolsUtils.isEditing(alex));
    assertEquals("Edit mode", statusOf(alex));
  }

  // Redrawing

  @Test
  void aPlayerWhoMovedFarHasTheirDrawingRedrawn() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(-100, -100, 200, 200);
    MapToolsUtils.startEditing(alex, EditSession.view());
    List<BlockDisplay> before = drawing.world().displays();
    assertFalse(before.isEmpty());
    drawing.moveTo(alex, 60, 0);

    task.run();

    assertTrue(before.stream().noneMatch(BlockDisplay::isValid));
    assertFalse(drawing.world().displays().isEmpty());
  }

  @Test
  void aPlayerWhoStayedPutKeepsTheirDrawing() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(-100, -100, 200, 200);
    MapToolsUtils.startEditing(alex, EditSession.view());
    List<BlockDisplay> before = drawing.world().displays();

    task.run();

    assertTrue(before.stream().allMatch(BlockDisplay::isValid));
    assertNotNull(alex.nextActionBar());
  }

  @Test
  void aPlayerWhoCameInWithoutEditModeIsNotDrawnFor() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(-100, -100, 200, 200);

    task.run();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void theTaskDoesNothingWithoutAnyPlayers() {
    alex.disconnect();

    assertDoesNotThrow(task::run);
  }

  @Test
  void aPlayerWhoKeepsThePermissionKeepsTheSessionAcrossRuns() {
    put(alex, editing(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19)));

    task.run();
    task.run();

    assertTrue(MapToolsUtils.isEditing(alex));
    assertEquals("Editing home", statusOf(alex));
    assertEquals("Editing home", statusOf(alex));
  }
}
