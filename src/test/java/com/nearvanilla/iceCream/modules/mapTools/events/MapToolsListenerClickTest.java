package com.nearvanilla.iceCream.modules.mapTools.events;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Left clicks and the swap hands key turned into edits, in a world with flat ground. */
class MapToolsListenerClickTest {
  private DisplayScenario drawing;
  private Viewer alex;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    drawing.world().loadChunks(-20, -20, 80, 80);
    alex = drawing.viewer();
    // Standing at the origin looking along z, with the eyes just above the ground blocks.
    alex.setLocation(new Location(drawing.world(), 0.5, 3.0, 0.5, 0f, 0f));
    drawing.server().getPluginManager().registerEvents(new MapToolsListener(), IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private EditSession editing(EditSession session) {
    MapToolsUtils.startEditing(alex, session);
    return session;
  }

  private Block block(int x, int y, int z) {
    return drawing.world().getBlockAt(x, y, z);
  }

  private PlayerInteractEvent fire(Action action, Block clicked, EquipmentSlot hand) {
    PlayerInteractEvent event =
        new PlayerInteractEvent(alex, action, null, clicked, BlockFace.UP, hand);
    drawing.server().getPluginManager().callEvent(event);
    return event;
  }

  private PlayerInteractEvent leftClick(Block clicked) {
    return fire(Action.LEFT_CLICK_BLOCK, clicked, EquipmentSlot.HAND);
  }

  private EditSession newSession(Mode mode) {
    return editing(EditSession.create(mode));
  }

  private Location corner(EditSession session, int index) {
    return session.definingBlocks().get(index);
  }

  // Who is affected

  @Test
  void aClickFromAPlayerNotInEditModeIsLeftAlone() {
    PlayerInteractEvent event = leftClick(block(5, 64, 5));

    assertFalse(event.isCancelled());
    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void aClickInEditModeNeverUsesTheItemOrBreaksTheBlock() {
    editing(EditSession.view());

    PlayerInteractEvent event = leftClick(block(5, 64, 5));

    assertTrue(event.isCancelled());
  }

  // Which clicks count

  @ParameterizedTest
  @EnumSource(
      value = Action.class,
      names = {"RIGHT_CLICK_BLOCK", "RIGHT_CLICK_AIR", "PHYSICAL"})
  void clicksThatAreNotLeftClicksAreCancelledWithoutEditing(Action action) {
    EditSession session = newSession(Mode.RECT);

    PlayerInteractEvent event = fire(action, block(5, 64, 5), EquipmentSlot.HAND);

    assertTrue(event.isCancelled());
    assertTrue(session.definingBlocks().isEmpty());
  }

  @Test
  void aLeftClickWithTheOffHandIsCancelledWithoutEditing() {
    EditSession session = newSession(Mode.RECT);

    PlayerInteractEvent event =
        fire(Action.LEFT_CLICK_BLOCK, block(5, 64, 5), EquipmentSlot.OFF_HAND);

    assertTrue(event.isCancelled());
    assertTrue(session.definingBlocks().isEmpty());
  }

  @Test
  void aLeftClickOnABlockSetsTheFirstRectangleCornerOnTopOfItsColumn() {
    EditSession session = newSession(Mode.RECT);
    block(5, 70, 5).setType(Material.STONE);

    PlayerInteractEvent event = leftClick(block(5, 64, 5));

    assertTrue(event.isCancelled());
    assertEquals(1, session.definingBlocks().size());
    Location corner = corner(session, 0);
    assertEquals(5, corner.getBlockX());
    assertEquals(70, corner.getBlockY());
    assertEquals(5, corner.getBlockZ());
  }

  @Test
  void theColumnTopIsUsedWhenTheSideOfACliffIsClicked() {
    EditSession session = newSession(Mode.MARKER);
    block(5, 40, 5).setType(Material.STONE);

    leftClick(block(5, 10, 5));

    assertEquals(40, session.getCenter().getBlockY());
  }

  @Test
  void aShiftLeftClickIsTheSecondAction() {
    EditSession session = newSession(Mode.RECT);
    leftClick(block(5, 64, 5));
    alex.setSneaking(true);

    leftClick(block(15, 64, 25));

    assertEquals(4, session.definingBlocks().size());
    assertEquals(15, corner(session, 2).getBlockX());
    assertEquals(25, corner(session, 2).getBlockZ());
    assertEquals(5, corner(session, 0).getBlockX());
  }

  @Test
  void aLeftClickInViewModeStartsEditingTheAreaUnderTheBlock() {
    MapArea home = drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    editing(EditSession.view());

    leftClick(block(15, 64, 15));

    EditSession session = MapToolsModule.sessions.get(alex.getUniqueId());
    assertTrue(session.isEditingArea());
    assertEquals(home.id(), session.getAreaId());
  }

  @Test
  void aLeftClickInViewModeOutsideEveryAreaChangesNothing() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    EditSession session = editing(EditSession.view());

    PlayerInteractEvent event = leftClick(block(40, 64, 40));

    assertTrue(event.isCancelled());
    assertEquals(session, MapToolsModule.sessions.get(alex.getUniqueId()));
    assertTrue(session.isViewing());
  }

  // Clicking at the sky and far away

  @Test
  void aLeftClickAtTheAirUsesTheBlockThePlayerLooksAt() {
    EditSession session = newSession(Mode.MARKER);
    block(30, 70, 12).setType(Material.STONE);
    alex.lookAt(block(30, 69, 12));

    PlayerInteractEvent event = fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertTrue(event.isCancelled());
    assertEquals(30, session.getCenter().getBlockX());
    assertEquals(12, session.getCenter().getBlockZ());
    assertEquals(70, session.getCenter().getBlockY());
  }

  @Test
  void theLineOfSightReachesAsFarAsOutlinesAreDrawnAndStopsAtWater() {
    newSession(Mode.MARKER);

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertEquals(128, alex.requestedDistance());
    assertEquals(FluidCollisionMode.ALWAYS, alex.requestedMode());
  }

  @Test
  void aClickedBlockIsUsedWithoutLookingForAnother() {
    newSession(Mode.MARKER);

    leftClick(block(5, 64, 5));

    assertEquals(-1, alex.requestedDistance());
  }

  @Test
  void aClickAtTheSkyDoesNothingWhileDrawingANewShape() {
    EditSession session = newSession(Mode.RECT);

    PlayerInteractEvent event = fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertTrue(event.isCancelled());
    assertTrue(session.definingBlocks().isEmpty());
  }

  @Test
  void aClickAtTheSkyPutsThePickedUpCornerBackWhereItWas() {
    EditSession session = editing(EditSession.create(Mode.POLYGON));
    session.addPoint(drawing.block(10, 70, 10));
    session.addPoint(drawing.block(20, 70, 10));
    session.addPoint(drawing.block(20, 70, 20));
    session.setPicked(1);

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertNull(session.getPicked());
    assertEquals(3, session.getPointCount());
    assertEquals(20, corner(session, 1).getBlockX());
  }

  @Test
  void aClickAtTheSkyWhileEditingAClaimLeavesIt() {
    MapArea home = drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    editing(EditSession.edit(home));

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertTrue(MapToolsModule.sessions.get(alex.getUniqueId()).isViewing());
  }

  @Test
  void aShiftClickAtTheSkyDoesNothing() {
    MapArea home = drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    EditSession session = editing(EditSession.edit(home));
    alex.setSneaking(true);

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertEquals(session, MapToolsModule.sessions.get(alex.getUniqueId()));
  }

  // Clicking corners that have no hitbox

  /** A polygon being drawn with a point at block 0, 20, straight ahead of the player. */
  private EditSession polygonAhead() {
    EditSession session = editing(EditSession.create(Mode.POLYGON));
    session.addPoint(drawing.block(0, 70, 20));
    return session;
  }

  @Test
  void aCornerInTheLineOfSightIsPickedUpEvenWhenNoBlockIsHit() {
    EditSession session = polygonAhead();

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertEquals(0, session.getPicked());
    assertEquals(1, session.getPointCount());
  }

  @Test
  void aCornerInTheLineOfSightIsPickedUpInsteadOfTheBlockBehindIt() {
    EditSession session = polygonAhead();

    leftClick(block(0, GROUND, 40));

    assertEquals(0, session.getPicked());
    assertEquals(1, session.getPointCount());
  }

  @ParameterizedTest
  @CsvSource({"20", "19"})
  void aBlockNextToTheCornerOrJustInFrontOfItDoesNotHideIt(int z) {
    EditSession session = polygonAhead();

    leftClick(block(0, GROUND, z));

    assertEquals(0, session.getPicked(), "the corner is within a block and a half of the block");
  }

  @ParameterizedTest
  @CsvSource({"18", "10", "5"})
  void aBlockMoreThanAnAndAHalfBlocksNearerThanTheCornerIsClickedInstead(int z) {
    EditSession session = polygonAhead();

    leftClick(block(0, GROUND, z));

    assertNull(session.getPicked());
    assertEquals(2, session.getPointCount(), "a point was added where the block was clicked");
    assertEquals(z, corner(session, 1).getBlockZ());
  }

  @Test
  void aCornerOutOfTheLineOfSightIsNotPickedUp() {
    EditSession session = polygonAhead();
    // Looking along x now, so the corner at 0, 20 is far off to the side.
    alex.setLocation(new Location(drawing.world(), 0.5, 3.0, 0.5, -90f, 0f));

    leftClick(block(30, GROUND, 0));

    assertNull(session.getPicked());
    assertEquals(2, session.getPointCount());
  }

  @Test
  void aCornerBehindThePlayerIsNotPickedUp() {
    EditSession session = polygonAhead();
    alex.setLocation(new Location(drawing.world(), 0.5, 3.0, 0.5, 180f, 0f));

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertNull(session.getPicked());
  }

  @Test
  void cornersAreOnlyAimedAtWhileDrawingPolygonsOrEditingAnArea() {
    EditSession session = editing(EditSession.create(Mode.RECT));
    session.setCorner1(drawing.block(0, 70, 20));

    // The corner is in the line of sight and the click is next to it, so aiming at it would have
    // picked it up and left it where it was, on the height it was clicked at.
    leftClick(block(0, GROUND, 20));

    assertEquals(GROUND, corner(session, 0).getBlockY(), "the click moved the first corner");
    assertNull(session.getPicked());
  }

  @Test
  void aCornerOfAnAreaBeingEditedIsPickedUpFromAFar() {
    MapArea tri =
        drawing.stored(
            MapToolsTestUtils.polygon("tri", DRAWING, 0.5, 20.5, 10.5, 25.5, -9.5, 25.5));
    EditSession session = editing(EditSession.edit(tri));

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertEquals(0, session.getPicked());
  }

  @Test
  void aShiftClickOnACornerInSightRemovesIt() {
    EditSession session = polygonAhead();
    session.addPoint(drawing.block(10, 70, 20));
    session.addPoint(drawing.block(10, 70, 40));
    session.addPoint(drawing.block(0, 70, 40));
    alex.setSneaking(true);

    fire(Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

    assertEquals(3, session.getPointCount());
  }

  // The swap hands key

  @Test
  void swappingHandsIsLeftAloneOutsideEditMode() {
    PlayerSwapHandItemsEvent event = swap();

    assertFalse(event.isCancelled());
  }

  @Test
  void swappingHandsIsLeftAloneInViewMode() {
    editing(EditSession.view());

    PlayerSwapHandItemsEvent event = swap();

    assertFalse(event.isCancelled());
  }

  @Test
  void swappingHandsUndoesTheLastChange() {
    EditSession session = newSession(Mode.POLYGON);
    leftClick(block(5, 64, 5));
    leftClick(block(15, 64, 5));
    assertEquals(2, session.getPointCount());

    PlayerSwapHandItemsEvent event = swap();

    assertTrue(event.isCancelled());
    assertEquals(1, session.getPointCount());
  }

  @Test
  void swappingHandsWithShiftRedoesIt() {
    EditSession session = newSession(Mode.POLYGON);
    leftClick(block(5, 64, 5));
    leftClick(block(15, 64, 5));
    swap();
    assertEquals(1, session.getPointCount());
    alex.setSneaking(true);

    PlayerSwapHandItemsEvent event = swap();

    assertTrue(event.isCancelled());
    assertEquals(2, session.getPointCount());
  }

  @Test
  void swappingHandsWithNothingToUndoSaysSo() {
    newSession(Mode.POLYGON);

    PlayerSwapHandItemsEvent event = swap();

    assertTrue(event.isCancelled());
    assertEquals("Nothing to undo.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void swappingHandsWithShiftAndNothingToRedoSaysSo() {
    newSession(Mode.POLYGON);
    alex.setSneaking(true);

    PlayerSwapHandItemsEvent event = swap();

    assertTrue(event.isCancelled());
    assertEquals("Nothing to redo.", MessageTestUtils.nextMessage(alex));
  }

  private PlayerSwapHandItemsEvent swap() {
    PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(alex, null, null);
    drawing.server().getPluginManager().callEvent(event);
    return event;
  }
}
