package com.nearvanilla.iceCream.modules.mapTools;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.GROUND;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.LINE_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The shape a player is drawing or editing, which is drawn in white as they click. */
class AreaDisplaysPreviewTest {
  private static final double DELTA = 1e-3;

  private DisplayScenario drawing;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    drawing.world().loadChunks(-16, -16, 80, 80);
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private Location at(int x, int z) {
    return drawing.block(x, 70, z);
  }

  private EditSession rect(int x1, int z1, int x2, int z2) {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(x1, z1));
    session.setCorner2(at(x2, z2));
    return session;
  }

  private EditSession polygon(int... coordinates) {
    EditSession session = EditSession.create(Mode.POLYGON);
    for (int i = 0; i + 1 < coordinates.length; i += 2) {
      session.addPoint(at(coordinates[i], coordinates[i + 1]));
    }
    return session;
  }

  private void edit(EditSession session) {
    MapToolsUtils.startEditing(drawing.viewer(), session);
  }

  private static Set<String> blocks(List<BlockDisplay> displays) {
    return displays.stream()
        .map(
            d ->
                "%d,%d,%d"
                    .formatted(
                        d.getLocation().getBlockX(),
                        d.getLocation().getBlockY(),
                        d.getLocation().getBlockZ()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<String> corners(int... xz) {
    Set<String> corners = new TreeSet<>();
    for (int i = 0; i + 1 < xz.length; i += 2) {
      corners.add("%d,%d,%d".formatted(xz[i], GROUND, xz[i + 1]));
    }
    return corners;
  }

  // Which sessions are drawn

  @Test
  void aPlayerJustViewingHasNoPreview() {
    edit(EditSession.view());

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aNewShapeWithNothingClickedDrawsNothing() {
    edit(EditSession.create(Mode.POLYGON));

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aShapeInAnotherWorldIsNotDrawnAroundAPlayerElsewhere() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.scenario().overworldBlock(10, 70, 10));
    session.setCorner2(drawing.scenario().overworldBlock(19, 70, 19));

    edit(session);

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void thePreviewFollowsThePlayerBackIntoTheWorldOfTheShape() {
    EditSession session = rect(10, 10, 19, 19);
    edit(session);
    drawing.viewer().setLocation(new Location(drawing.scenario().overworld(), 0, 64, 0));
    AreaDisplays.refreshPreview(drawing.viewer());
    assertTrue(drawing.world().displays().isEmpty());

    drawing.viewer().setLocation(new Location(drawing.world(), 0, 64, 0));
    AreaDisplays.refreshPreview(drawing.viewer());

    assertEquals(4, drawing.ghosts().size());
  }

  // Rectangles

  @Test
  void theFirstRectangleCornerIsOneGhostBlockWithoutLines() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(at(10, 10));

    edit(session);

    assertEquals(corners(10, 10), blocks(drawing.ghosts(Material.GLASS)));
    assertEquals(1, drawing.world().displays().size());
  }

  @Test
  void aFinishedRectangleHasFourGhostBlocksAndAWhiteOutline() {
    edit(rect(10, 10, 19, 19));

    assertEquals(corners(10, 10, 19, 10, 19, 19, 10, 19), blocks(drawing.ghosts(Material.GLASS)));
    List<BlockDisplay> lines = drawing.lines(Material.WHITE_CONCRETE);
    assertEquals(12, lines.size());
    for (BlockDisplay line : lines) {
      assertEquals(LINE_HEIGHT, line.getLocation().getY(), DELTA);
      assertEquals(Color.WHITE, DisplayTestUtils.glowColor(line));
      assertFalse(line.isVisibleByDefault());
      assertTrue(drawing.viewer().visibleDisplays().contains(line));
    }
  }

  @Test
  void theGhostBlocksOfAShapeGlowWhite() {
    edit(rect(10, 10, 19, 19));

    for (BlockDisplay ghost : drawing.ghosts()) {
      assertEquals(Color.WHITE, DisplayTestUtils.glowColor(ghost));
    }
  }

  @Test
  void aGhostBlockIsDrawnOnTheGroundWhateverHeightWasClicked() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.block(10, 120, 10));

    edit(session);

    assertEquals(corners(10, 10), blocks(drawing.ghosts()));
  }

  // Polygons

  @Test
  void onePolygonPointIsOneGhostBlock() {
    edit(polygon(10, 10));

    assertEquals(1, drawing.world().displays().size());
    assertEquals(corners(10, 10), blocks(drawing.ghosts()));
  }

  @Test
  void twoPolygonPointsAreJoinedByOneLineAndNotClosed() {
    edit(polygon(10, 10, 20, 10));

    assertEquals(2, drawing.ghosts().size());
    // The points are the middles of blocks 10 and 20, 10 blocks apart: 3 pieces, one side.
    assertEquals(3, drawing.lines(Material.WHITE_CONCRETE).size());
    assertTrue(drawing.gridLines().isEmpty());
  }

  @Test
  void threePolygonPointsCloseTheOutline() {
    edit(polygon(10, 10, 20, 10, 15, 20));

    assertEquals(3, drawing.ghosts().size());
    // Three sides of about 10 blocks: 3, 3 and 3 pieces.
    assertEquals(9, drawing.lines(Material.WHITE_CONCRETE).size());
  }

  @Test
  void theOutlineOfAPolygonPassesThroughTheMiddlesOfItsPointBlocks() {
    edit(polygon(10, 10, 20, 10, 15, 20));

    Set<String> starts =
        drawing.lines().stream()
            .map(d -> "%.2f,%.2f".formatted(d.getLocation().getX(), d.getLocation().getZ()))
            .collect(Collectors.toSet());
    assertTrue(starts.contains("10.50,10.50"));
    assertTrue(starts.contains("20.50,10.50"));
    assertTrue(starts.contains("15.50,20.50"));
  }

  // The picked up corner

  @Test
  void thePickedCornerIsASolidYellowBlockAndTheOthersAreGlass() {
    EditSession session = polygon(10, 10, 20, 10, 15, 20);
    session.setPicked(1);

    edit(session);

    List<BlockDisplay> yellow = drawing.ghosts(Material.YELLOW_CONCRETE);
    assertEquals(corners(20, 10), blocks(yellow));
    assertEquals(Color.YELLOW, yellow.getFirst().getGlowColorOverride());
    List<BlockDisplay> glass = drawing.ghosts(Material.GLASS);
    assertEquals(corners(10, 10, 15, 20), blocks(glass));
    for (BlockDisplay ghost : glass) {
      assertEquals(Color.WHITE, DisplayTestUtils.glowColor(ghost));
    }
  }

  @Test
  void pickingACornerMovesTheYellowOneOnTheNextRedraw() {
    EditSession session = polygon(10, 10, 20, 10, 15, 20);
    session.setPicked(0);
    edit(session);

    session.setPicked(2);
    AreaDisplays.refreshPreview(drawing.viewer());

    assertEquals(corners(15, 20), blocks(drawing.ghosts(Material.YELLOW_CONCRETE)));
    assertEquals(1, drawing.ghosts(Material.YELLOW_CONCRETE).size());
  }

  @Test
  void puttingTheCornerDownTurnsItBackToGlass() {
    EditSession session = polygon(10, 10, 20, 10, 15, 20);
    session.setPicked(1);
    edit(session);

    session.setPicked(null);
    AreaDisplays.refreshPreview(drawing.viewer());

    assertTrue(drawing.ghosts(Material.YELLOW_CONCRETE).isEmpty());
    assertEquals(3, drawing.ghosts(Material.GLASS).size());
  }

  // Circles and markers

  @Test
  void aCircleBeingDrawnHasItsCenterAsAGhostBlockAndTheCircleAsOutline() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    session.setCenter(at(40, 40));
    session.setRadius(15);

    edit(session);

    assertEquals(corners(40, 40), blocks(drawing.ghosts(Material.GLASS)));
    assertEquals(1, drawing.ghosts().size());
    assertFalse(drawing.lines(Material.WHITE_CONCRETE).isEmpty());
  }

  @Test
  void aCircleWithoutARadiusYetIsOnlyItsCenter() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    session.setCenter(at(40, 40));

    edit(session);

    assertEquals(1, drawing.world().displays().size());
    assertEquals(corners(40, 40), blocks(drawing.ghosts()));
  }

  @Test
  void aMarkerBeingPlacedIsALodestone() {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(at(30, 30));

    edit(session);

    assertEquals(corners(30, 30), blocks(drawing.ghosts(Material.LODESTONE)));
    assertEquals(1, drawing.world().displays().size());
    assertEquals(Color.WHITE, DisplayTestUtils.glowColor(drawing.ghosts().getFirst()));
  }

  @Test
  void aPickedUpMarkerIsYellow() {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(at(30, 30));
    session.setPicked(0);

    edit(session);

    assertEquals(corners(30, 30), blocks(drawing.ghosts(Material.YELLOW_CONCRETE)));
    assertTrue(drawing.ghosts(Material.LODESTONE).isEmpty());
  }

  // Editing a saved area

  @Test
  void anAreaBeingEditedIsDrawnWhiteOnItsGroundCorners() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));

    edit(EditSession.edit(MapToolsModule.store.getArea("home")));

    assertEquals(corners(10, 10, 19, 10, 19, 19, 10, 19), blocks(drawing.ghosts(Material.GLASS)));
    assertEquals(12, drawing.lines(Material.WHITE_CONCRETE).size());
  }

  // Far and unloaded places

  @Test
  void aGhostBlockOutOfViewDistanceIsLeftOut() {
    // The point at 200 is further than 128 blocks away.
    EditSession session = polygon(10, 10, 200, 10);
    drawing.world().loadChunks(190, 0, 210, 20);

    edit(session);

    assertEquals(corners(10, 10), blocks(drawing.ghosts()));
  }

  @Test
  void aGhostBlockInAnUnloadedChunkIsDrawnWhereItWasClicked() {
    drawing.viewer().setLocation(new Location(drawing.world(), 1000, 64, 1000));
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(drawing.block(1000, 70, 1000));

    edit(session);

    assertEquals(Set.of("1000,70,1000"), blocks(drawing.ghosts()));
  }

  @Test
  void aGhostBlockOnLoadedGroundIsMovedDownToTheGround() {
    EditSession session = EditSession.create(Mode.MARKER);
    session.setCenter(drawing.block(30, 70, 30));

    edit(session);

    assertEquals(corners(30, 30), blocks(drawing.ghosts()));
  }

  // Redrawing

  @Test
  void refreshingThePreviewReplacesItAndLeavesTheSavedAreasAlone() {
    drawing.stored(MapToolsTestUtils.rect("shop", DRAWING, 40, 40, 49, 49));
    EditSession session = polygon(10, 10, 20, 10, 15, 20);
    edit(session);
    List<BlockDisplay> saved = glowing(Color.fromRGB(0xFFAA00));
    List<BlockDisplay> preview = glowing(Color.WHITE);
    assertFalse(saved.isEmpty());
    assertFalse(preview.isEmpty());

    session.addPoint(at(25, 25));
    AreaDisplays.refreshPreview(drawing.viewer());

    assertTrue(saved.stream().allMatch(BlockDisplay::isValid), "the saved area was redrawn");
    assertTrue(preview.stream().noneMatch(BlockDisplay::isValid), "the old preview stayed");
    assertEquals(4, drawing.ghosts(Material.GLASS).stream().filter(this::isWhite).count());
  }

  private List<BlockDisplay> glowing(Color color) {
    return drawing.world().displays().stream()
        .filter(d -> color.equals(DisplayTestUtils.glowColor(d)))
        .toList();
  }

  private boolean isWhite(BlockDisplay display) {
    return Color.WHITE.equals(DisplayTestUtils.glowColor(display));
  }

  @Test
  void aFullRefreshRedrawsThePreviewToo() {
    EditSession session = polygon(10, 10, 20, 10, 15, 20);
    edit(session);
    List<BlockDisplay> before = drawing.world().displays();

    AreaDisplays.refresh(drawing.viewer());

    assertEquals(before.size(), drawing.world().displays().size());
    assertTrue(before.stream().noneMatch(BlockDisplay::isValid));
  }

  @Test
  void whenTheSessionEndsThePreviewIsGone() {
    edit(rect(10, 10, 19, 19));

    MapToolsUtils.stopEditing(drawing.viewer());

    assertTrue(drawing.world().displays().isEmpty());
  }
}
