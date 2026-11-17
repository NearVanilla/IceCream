package com.nearvanilla.iceCream.modules.mapTools;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.GROUND;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.LINE_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The saved areas drawn around a player in edit mode: ghost blocks on the corners and outlines
 * along the ground, in the world of MockBukkit's flat terrain.
 */
class AreaDisplaysAreasTest {
  private static final Color GOLD = Color.fromRGB(0xFFAA00);
  private static final double DELTA = 1e-3;

  private DisplayScenario drawing;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private MapArea stored(MapArea area) {
    return drawing.stored(area);
  }

  private void view() {
    MapToolsUtils.startEditing(drawing.viewer(), EditSession.view());
  }

  private static String block(BlockDisplay display) {
    return "%d,%d,%d"
        .formatted(
            display.getLocation().getBlockX(),
            display.getLocation().getBlockY(),
            display.getLocation().getBlockZ());
  }

  private static Set<String> blocks(List<BlockDisplay> displays) {
    return displays.stream()
        .map(AreaDisplaysAreasTest::block)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<String> starts(List<BlockDisplay> displays) {
    return displays.stream()
        .map(d -> "%.2f,%.2f".formatted(d.getLocation().getX(), d.getLocation().getZ()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private MapArea square(String id) {
    return MapToolsTestUtils.rect(id, DRAWING, 10, 10, 19, 19);
  }

  // Nothing to draw

  @Test
  void withoutASessionNothingIsDrawn() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    AreaDisplays.refresh(drawing.viewer());

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void anAreaInAnotherWorldIsNotDrawn() {
    stored(MapToolsTestUtils.rect("far", MapToolsTestUtils.OVERWORLD, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aHiddenAreaIsNotDrawn() {
    stored(square("home").withHidden(true));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void theAreaBeingEditedIsLeftToThePreview() {
    stored(square("home").withColor("#0000ff"));
    stored(MapToolsTestUtils.rect("shop", DRAWING, 30, 10, 39, 19));
    drawing.world().loadChunks(0, 0, 60, 40);

    MapToolsUtils.startEditing(
        drawing.viewer(), EditSession.edit(MapToolsModule.store.getArea("home")));

    // The edited claim is drawn by the preview, in white, and not as a saved area in its own blue.
    assertTrue(drawing.lines(Material.BLUE_CONCRETE).isEmpty());
    assertTrue(
        drawing.world().displays().stream()
            .noneMatch(d -> Color.fromRGB(0x0000FF).equals(d.getGlowColorOverride())));
    assertEquals(
        Set.of(
            "10,%d,10".formatted(GROUND),
            "19,%d,10".formatted(GROUND),
            "19,%d,19".formatted(GROUND),
            "10,%d,19".formatted(GROUND)),
        blocks(
            drawing.ghosts(Material.GLASS).stream()
                .filter(g -> Color.WHITE.equals(DisplayTestUtils.glowColor(g)))
                .toList()));
    // The other claim is still drawn as a saved area.
    assertFalse(drawing.lines(Material.ORANGE_CONCRETE).isEmpty());
  }

  @Test
  void anAreaInUnloadedChunksLeavesNothingToDrawOn() {
    stored(square("home"));

    view();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void onlyTheCornersInLoadedChunksGetGhostBlocks() {
    stored(square("home"));
    // Chunk 0, 0 covers blocks 0 to 15, so only the corner at 10, 10 is on loaded ground.
    drawing.world().loadChunk(0, 0);

    view();

    assertEquals(Set.of("10,%d,10".formatted(GROUND)), blocks(drawing.ghosts()));
  }

  // Ghost blocks

  @Test
  void aClaimGetsAGhostBlockOnTheGroundAtEachCorner() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(
        Set.of(
            "10,%d,10".formatted(GROUND),
            "19,%d,10".formatted(GROUND),
            "19,%d,19".formatted(GROUND),
            "10,%d,19".formatted(GROUND)),
        blocks(drawing.ghosts(Material.GLASS)));
    assertEquals(4, drawing.ghosts().size());
  }

  @Test
  void aCornerOnABlockEdgeUsesTheBlockInsideTheClaim() {
    stored(MapToolsTestUtils.rect("home", DRAWING, 0, 0, 0, 0));
    drawing.world().loadChunks(-16, -16, 16, 16);

    view();

    // The corners are on 0,0 to 1,1, and each takes the block that lies inside the claim.
    assertEquals(Set.of("0,%d,0".formatted(GROUND)), blocks(drawing.ghosts()));
    assertEquals(4, drawing.ghosts().size());
  }

  @Test
  void aPolygonCornerInsideABlockUsesThatBlock() {
    stored(MapToolsTestUtils.polygon("tri", DRAWING, 10.5, 10.5, 20.5, 10.5, 15.5, 20.5));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(
        Set.of(
            "10,%d,10".formatted(GROUND),
            "20,%d,10".formatted(GROUND),
            "15,%d,20".formatted(GROUND)),
        blocks(drawing.ghosts()));
  }

  @Test
  void aCircleGetsOneGhostBlockAtItsCenter() {
    stored(MapToolsTestUtils.circle("pond", DRAWING, 50, 50, 20));
    drawing.world().loadChunks(20, 20, 90, 90);

    view();

    assertEquals(Set.of("50,%d,50".formatted(GROUND)), blocks(drawing.ghosts(Material.GLASS)));
    assertEquals(1, drawing.ghosts().size());
    assertFalse(drawing.lines(Material.ORANGE_CONCRETE).isEmpty());
  }

  @Test
  void aMarkerIsALodestoneOnItsBlockWithoutAnOutline() {
    stored(MapToolsTestUtils.marker("spot", DRAWING, 10, 10));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(Set.of("10,%d,10".formatted(GROUND)), blocks(drawing.ghosts(Material.LODESTONE)));
    assertEquals(1, drawing.world().displays().size());
    assertEquals(GOLD, drawing.ghosts().getFirst().getGlowColorOverride());
  }

  @Test
  void aGhostBlockIsAGlowingOutlineOnlyThePlayerSees() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    for (BlockDisplay ghost : drawing.ghosts()) {
      assertTrue(ghost.isGlowing());
      assertEquals(GOLD, ghost.getGlowColorOverride());
      assertEquals(new Display.Brightness(15, 15), ghost.getBrightness());
      assertEquals(2.5f, ghost.getViewRange());
      assertFalse(ghost.isPersistent());
      assertFalse(ghost.isVisibleByDefault());
      assertTrue(drawing.viewer().visibleDisplays().contains(ghost));
    }
  }

  @Test
  void aGhostBlockIsALittleLargerThanTheBlockItOutlines() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    Transformation shape = drawing.ghosts().getFirst().getTransformation();
    assertEquals(-0.03f, shape.getTranslation().x(), 1e-6f);
    assertEquals(-0.03f, shape.getTranslation().y(), 1e-6f);
    assertEquals(-0.03f, shape.getTranslation().z(), 1e-6f);
    assertEquals(1.06f, shape.getScale().x(), 1e-6f);
    assertEquals(1.06f, shape.getScale().y(), 1e-6f);
    assertEquals(1.06f, shape.getScale().z(), 1e-6f);
  }

  @Test
  void theGhostBlockGlowsInTheColorOfItsClaim() {
    stored(square("home").withColor("#12ab34"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(Color.fromRGB(0x12AB34), drawing.ghosts().getFirst().getGlowColorOverride());
  }

  @Test
  void aColorThatCannotBeReadIsDrawnInTheDefaultColor() {
    stored(square("home").withColor("not a color"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(GOLD, drawing.ghosts().getFirst().getGlowColorOverride());
  }

  // Outlines

  @Test
  void anOutlineIsAClosedLoopOfPiecesFollowingTheGround() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    List<BlockDisplay> lines = drawing.lines(Material.ORANGE_CONCRETE);
    // Each side is 10 blocks, drawn in pieces of at most 4 blocks: 3 pieces, 4 sides.
    assertEquals(12, lines.size());
    assertEquals(12, drawing.lines().size());
    for (BlockDisplay line : lines) {
      assertEquals(LINE_HEIGHT, line.getLocation().getY(), DELTA);
      assertTrue(line.isGlowing());
      assertEquals(GOLD, line.getGlowColorOverride());
      assertFalse(line.isVisibleByDefault());
      assertTrue(drawing.viewer().visibleDisplays().contains(line));
    }
  }

  @Test
  void theOutlineRunsFromCornerToCornerAndBackToTheFirst() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    // Every side starts at its own corner, so the closing side starts at the last corner, 10, 20.
    Set<String> starts = starts(drawing.lines(Material.ORANGE_CONCRETE));
    assertTrue(starts.contains("10.00,10.00"));
    assertTrue(starts.contains("20.00,10.00"));
    assertTrue(starts.contains("20.00,20.00"));
    assertTrue(starts.contains("10.00,20.00"));
    // The first side is cut into thirds of 10 / 3 blocks.
    assertTrue(starts.contains("13.33,10.00"));
    assertTrue(starts.contains("16.67,10.00"));
  }

  @Test
  void anOutlinePieceIsAsLongAsItsPartOfTheSideAndAsThickAsAPencil() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    BlockDisplay piece =
        drawing.lines(Material.ORANGE_CONCRETE).stream()
            .filter(d -> Math.abs(d.getLocation().getX() - 10) < DELTA)
            .filter(d -> Math.abs(d.getLocation().getZ() - 10) < DELTA)
            .findFirst()
            .orElseThrow();
    var scale = piece.getTransformation().getScale();
    assertEquals(10f / 3, scale.x(), 1e-3f);
    assertEquals(0.15f, scale.y(), 1e-6f);
    assertEquals(0.15f, scale.z(), 1e-6f);
  }

  @ParameterizedTest
  @CsvSource({
    "gold, ORANGE_CONCRETE",
    "#0000ff, BLUE_CONCRETE",
    "white, WHITE_CONCRETE",
    "#ff0000, RED_CONCRETE",
    "#00ff00, LIME_CONCRETE"
  })
  void theOutlineUsesTheConcreteClosestToTheClaimColor(String color, Material concrete) {
    stored(square("home").withColor(color));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(12, drawing.lines(concrete).size());
    assertEquals(12, drawing.lines().size());
  }

  @Test
  void anOutlineOverAHillClimbsItInShorterPieces() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    for (int x = 12; x <= 15; x++) drawing.world().getBlockAt(x, 20, 10).setType(Material.STONE);

    view();

    List<BlockDisplay> lines = drawing.lines(Material.ORANGE_CONCRETE);
    assertTrue(lines.size() > 12, "the steep pieces are split in half");
    double top = lines.stream().mapToDouble(d -> d.getLocation().getY()).max().orElseThrow();
    assertEquals(20 + 1 + 0.15, top, DELTA);
  }

  @Test
  void aFlatOutlineIsNotSplitIntoMorePieces() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);

    view();

    assertEquals(12, drawing.lines(Material.ORANGE_CONCRETE).size());
  }

  @Test
  void aPieceOverAnUnloadedChunkIsLeftOut() {
    stored(square("home"));
    // Chunk 0, 0 only: the side along z = 10 from x = 10 to x = 16 is on loaded ground, the rest
    // is not.
    drawing.world().loadChunk(0, 0);

    view();

    List<BlockDisplay> lines = drawing.lines(Material.ORANGE_CONCRETE);
    assertFalse(lines.isEmpty());
    assertTrue(lines.size() < 12);
    assertTrue(lines.stream().allMatch(d -> d.getLocation().getX() < 16));
  }

  // How far around the player it is drawn

  @Test
  void anAreaFurtherThanTheViewDistanceIsNotDrawn() {
    stored(MapToolsTestUtils.rect("far", DRAWING, 129, 0, 138, 9));
    drawing.world().loadChunks(100, 0, 150, 20);

    view();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void anAreaWhoseNearEdgeIsWithinTheViewDistanceIsDrawn() {
    stored(MapToolsTestUtils.rect("edge", DRAWING, 127, 0, 136, 9));
    drawing.world().loadChunks(100, 0, 150, 20);

    view();

    // The near corners are in reach and the far ones, at 136, are not.
    assertEquals(
        Set.of("127,%d,0".formatted(GROUND), "127,%d,9".formatted(GROUND)),
        blocks(drawing.ghosts()));
    assertFalse(drawing.lines().isEmpty());
  }

  @Test
  void anAreaAroundThePlayerIsDrawnEvenWithItsCornersOutOfReach() {
    stored(MapToolsTestUtils.rect("wide", DRAWING, -200, -5, 200, 9));
    drawing.world().loadChunks(-140, -20, 140, 20);

    view();

    assertTrue(drawing.ghosts().isEmpty());
    assertFalse(drawing.lines().isEmpty());
  }

  @Test
  void outlinesAreCutOffWhereTheyLeaveTheViewDistance() {
    stored(MapToolsTestUtils.rect("long", DRAWING, 100, 0, 300, 9));
    drawing.world().loadChunks(90, 0, 180, 20);

    view();

    List<BlockDisplay> lines = drawing.lines(Material.ORANGE_CONCRETE);
    assertFalse(lines.isEmpty());
    for (BlockDisplay line : lines) {
      double distance = Math.hypot(line.getLocation().getX(), line.getLocation().getZ());
      assertTrue(distance <= 128 + DELTA, "a piece starts " + distance + " blocks away");
    }
    assertTrue(lines.stream().anyMatch(d -> d.getLocation().getX() > 120));
  }

  @Test
  void aSideBeyondTheViewDistanceIsNotDrawnEvenWhenItsLineCrossesTheView() {
    // The side from 150, 0 to 200, 0 is out of reach, but the line it lies on passes the player.
    stored(MapToolsTestUtils.polygon("hook", DRAWING, 0, 3, 150, 0, 200, 0, 200, 10, 0, 10));
    drawing.world().loadChunks(0, 0, 210, 20);

    view();

    List<BlockDisplay> lines = drawing.lines(Material.ORANGE_CONCRETE);
    assertFalse(lines.isEmpty());
    for (BlockDisplay line : lines) {
      double distance = Math.hypot(line.getLocation().getX(), line.getLocation().getZ());
      assertTrue(distance <= 128 + DELTA, "a piece starts " + distance + " blocks away");
    }
  }

  @Test
  void theAreasAreMeasuredFromWhereThePlayerStands() {
    stored(MapToolsTestUtils.rect("far", DRAWING, 300, 300, 309, 309));
    drawing.world().loadChunks(290, 290, 320, 320);
    view();
    assertTrue(drawing.world().displays().isEmpty());

    drawing.moveTo(drawing.viewer(), 300, 300);
    AreaDisplays.refresh(drawing.viewer());

    assertEquals(4, drawing.ghosts().size());
  }

  // Redrawing

  @Test
  void drawingAgainReplacesTheEarlierDrawing() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    view();
    List<BlockDisplay> before = drawing.world().displays();

    AreaDisplays.refresh(drawing.viewer());

    List<BlockDisplay> after = drawing.world().displays();
    assertEquals(before.size(), after.size());
    assertTrue(before.stream().noneMatch(BlockDisplay::isValid));
    assertTrue(after.stream().allMatch(BlockDisplay::isValid));
  }

  @Test
  void aSecondPlayerGetsTheirOwnDrawingOfTheSameArea() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    var sam = drawing.addViewer("Sam");

    view();
    int one = drawing.world().displays().size();
    MapToolsUtils.startEditing(sam, EditSession.view());

    assertEquals(2 * one, drawing.world().displays().size());
    assertEquals(one, drawing.viewer().visibleDisplays().size());
    assertEquals(one, sam.visibleDisplays().size());
    assertTrue(
        sam.visibleDisplays().stream().noneMatch(drawing.viewer().visibleDisplays()::contains));
  }

  @Test
  void leavingEditModeTakesTheDrawingAway() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    view();
    assertFalse(drawing.world().displays().isEmpty());

    MapToolsUtils.stopEditing(drawing.viewer());

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void clearAllRemovesTheDrawingOfEveryPlayer() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    var sam = drawing.addViewer("Sam");
    view();
    MapToolsUtils.startEditing(sam, EditSession.view());

    AreaDisplays.clearAll();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void drawingAfterClearAllStartsFresh() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 40, 40);
    view();
    AreaDisplays.clearAll();

    AreaDisplays.refresh(drawing.viewer());

    assertEquals(4, drawing.ghosts().size());
  }

  @Test
  void savingAnAreaRedrawsItForThePlayersInEditMode() {
    stored(square("home"));
    drawing.world().loadChunks(0, 0, 60, 60);
    view();

    MapToolsUtils.saveArea(MapToolsTestUtils.rect("home", DRAWING, 30, 30, 49, 49));

    assertEquals(
        Set.of(
            "30,%d,30".formatted(GROUND),
            "49,%d,30".formatted(GROUND),
            "49,%d,49".formatted(GROUND),
            "30,%d,49".formatted(GROUND)),
        blocks(drawing.ghosts()));
  }
}
