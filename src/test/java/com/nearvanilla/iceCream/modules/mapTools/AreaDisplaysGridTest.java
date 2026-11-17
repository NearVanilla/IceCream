package com.nearvanilla.iceCream.modules.mapTools;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static com.nearvanilla.iceCream.testutil.DisplayScenario.LINE_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
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
import org.bukkit.entity.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The faint diagonal grid and the dots at its crossings, inside saved areas and the preview. */
class AreaDisplaysGridTest {
  private static final Color GOLD = Color.fromRGB(0xFFAA00);
  private static final double DELTA = 1e-3;

  private DisplayScenario drawing;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    drawing.world().loadChunks(-16, -16, 140, 140);
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

  private static Set<String> spots(List<BlockDisplay> dots) {
    return dots.stream()
        .map(d -> "%.1f,%.1f".formatted(d.getLocation().getX(), d.getLocation().getZ()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** The square of blocks 10 to 19, whose grid is easy to work out by hand. */
  private MapArea square() {
    return MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19);
  }

  // Saved areas

  @Test
  void dotsMarkWhereTheDiagonalLinesCrossInsideTheArea() {
    stored(square());

    view();

    // Lines run where x + z and x - z are multiples of 12. Inside 10 to 20 that is only 12, 12 and
    // 18, 18.
    assertEquals(Set.of("12.0,12.0", "18.0,18.0"), spots(drawing.dots()));
  }

  @Test
  void aDotIsASmallBrightBlockInTheColorOfTheArea() {
    stored(square());

    view();

    for (BlockDisplay dot : drawing.dots()) {
      assertEquals(Material.WHITE_CONCRETE, dot.getBlock().getMaterial());
      assertEquals(LINE_HEIGHT + 0.1, dot.getLocation().getY(), DELTA);
      assertTrue(dot.isGlowing());
      assertEquals(GOLD, dot.getGlowColorOverride());
      assertEquals(new Display.Brightness(15, 15), dot.getBrightness());
      assertFalse(dot.isVisibleByDefault());
      assertTrue(drawing.viewer().visibleDisplays().contains(dot));
      var transformation = dot.getTransformation();
      assertEquals(-0.1f, transformation.getTranslation().x(), 1e-6f);
      assertEquals(0.2f, transformation.getScale().y(), 1e-6f);
      assertEquals(0.2f, transformation.getScale().z(), 1e-6f);
    }
  }

  @Test
  void theGridLinesCoverTheDiagonalsInsideTheAreaAndNothingMore() {
    stored(square());

    view();

    // x + z = 24 and 36 cross the square in lines of 4 and 4 root two, and x - z = 0 is its
    // whole diagonal, 10 root two; lines are cut in pieces of at most 12 blocks.
    List<BlockDisplay> lines = drawing.gridLines();
    assertEquals(4, lines.size());
    double length = lines.stream().mapToDouble(d -> d.getTransformation().getScale().x()).sum();
    assertEquals(18 * Math.sqrt(2), length, 0.01);
  }

  @Test
  void aGridLineIsFaintGlassThatDoesNotGlow() {
    stored(square());

    view();

    for (BlockDisplay line : drawing.gridLines()) {
      assertEquals(Material.WHITE_STAINED_GLASS, line.getBlock().getMaterial());
      assertFalse(line.isGlowing());
      assertEquals(LINE_HEIGHT, line.getLocation().getY(), DELTA);
      assertEquals(0.04f, line.getTransformation().getScale().y(), 1e-6f);
      assertFalse(line.isVisibleByDefault());
      assertTrue(drawing.viewer().visibleDisplays().contains(line));
    }
  }

  @Test
  void aCircleHasGridDotsOnlyInsideIt() {
    stored(MapToolsTestUtils.circle("pond", DRAWING, 40, 40, 20));

    view();

    List<BlockDisplay> dots = drawing.dots();
    assertFalse(dots.isEmpty());
    for (BlockDisplay dot : dots) {
      double distance =
          Math.hypot(dot.getLocation().getX() - 40.5, dot.getLocation().getZ() - 40.5);
      assertTrue(distance <= 20, "a dot lies " + distance + " blocks from the center");
      assertEquals(GOLD, dot.getGlowColorOverride());
    }
  }

  @Test
  void aMarkerHasNoGrid() {
    stored(MapToolsTestUtils.marker("spot", DRAWING, 40, 40));

    view();

    assertTrue(drawing.dots().isEmpty());
    assertTrue(drawing.gridLines().isEmpty());
  }

  @Test
  void eachAreaGetsDotsInItsOwnColor() {
    stored(square().withColor("#0000ff"));
    stored(MapToolsTestUtils.rect("shop", DRAWING, 40, 40, 59, 59));

    view();

    long blue =
        drawing.dots().stream()
            .filter(d -> Color.fromRGB(0x0000FF).equals(d.getGlowColorOverride()))
            .count();
    long gold = drawing.dots().stream().filter(d -> GOLD.equals(d.getGlowColorOverride())).count();
    assertEquals(2, blue);
    assertTrue(gold > 0);
    assertEquals(drawing.dots().size(), blue + gold);
  }

  // How far around the player

  @Test
  void aGridIsNotDrawnForAnAreaBeyondEightyBlocks() {
    stored(MapToolsTestUtils.rect("far", DRAWING, 90, 0, 119, 29));

    view();

    // Its outline is within the view distance of 128, the grid is not within 80.
    assertFalse(drawing.ghosts().isEmpty());
    assertTrue(drawing.dots().isEmpty());
    assertTrue(drawing.gridLines().isEmpty());
  }

  @Test
  void aGridIsDrawnForAnAreaWithinEightyBlocks() {
    stored(MapToolsTestUtils.rect("near", DRAWING, 70, 0, 99, 29));

    view();

    assertFalse(drawing.dots().isEmpty());
  }

  @Test
  void dotsFurtherThanEightyBlocksFromThePlayerAreLeftOut() {
    stored(MapToolsTestUtils.rect("wide", DRAWING, 0, 0, 119, 29));

    view();

    List<BlockDisplay> dots = drawing.dots();
    assertFalse(dots.isEmpty());
    for (BlockDisplay dot : dots) {
      double distance = Math.hypot(dot.getLocation().getX(), dot.getLocation().getZ());
      assertTrue(distance <= 80 + DELTA, "a dot lies " + distance + " blocks away");
    }
    assertTrue(dots.stream().anyMatch(d -> d.getLocation().getX() > 60));
  }

  @Test
  void dotsFollowTheGround() {
    stored(square());
    drawing.world().getBlockAt(12, 30, 12).setType(Material.STONE);

    view();

    BlockDisplay dot =
        drawing.dots().stream()
            .filter(d -> d.getLocation().getBlockX() == 12)
            .findFirst()
            .orElseThrow();
    assertEquals(30 + 1 + 0.15 + 0.1, dot.getLocation().getY(), DELTA);
  }

  @Test
  void aDotOverAnUnloadedChunkIsLeftOut() {
    stored(square());
    // The dot at 12, 12 is in chunk 0, 0 and the dot at 18, 18 in chunk 1, 1.
    drawing.world().unloadChunk(1, 1);

    view();

    assertEquals(Set.of("12.0,12.0"), spots(drawing.dots()));
  }

  // The edited area and the preview

  @Test
  void theAreaBeingEditedHasItsGridDrawnByThePreviewInWhite() {
    stored(square());

    MapToolsUtils.startEditing(
        drawing.viewer(), EditSession.edit(MapToolsModule.store.getArea("home")));

    assertEquals(Set.of("12.0,12.0", "18.0,18.0"), spots(drawing.dots()));
    for (BlockDisplay dot : drawing.dots()) {
      assertEquals(Color.WHITE, DisplayTestUtils.glowColor(dot));
    }
  }

  @Test
  void aShapeBeingDrawnGetsAWhiteGridOnceItHasThreeSides() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(drawing.block(10, 70, 10));
    session.addPoint(drawing.block(30, 70, 10));
    MapToolsUtils.startEditing(drawing.viewer(), session);
    assertTrue(drawing.dots().isEmpty());
    assertTrue(drawing.gridLines().isEmpty());

    session.addPoint(drawing.block(20, 70, 30));
    AreaDisplays.refreshPreview(drawing.viewer());

    assertFalse(drawing.dots().isEmpty());
    assertFalse(drawing.gridLines().isEmpty());
    for (BlockDisplay dot : drawing.dots()) {
      assertEquals(Color.WHITE, DisplayTestUtils.glowColor(dot));
    }
  }

  @Test
  void theDotsOfAShapeBeingDrawnAreInsideIt() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.block(10, 70, 10));
    session.setCorner2(drawing.block(19, 70, 19));

    MapToolsUtils.startEditing(drawing.viewer(), session);

    assertEquals(Set.of("12.0,12.0", "18.0,18.0"), spots(drawing.dots()));
  }

  @Test
  void thePreviewGridIsReplacedWhenTheShapeChanges() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.block(10, 70, 10));
    session.setCorner2(drawing.block(19, 70, 19));
    MapToolsUtils.startEditing(drawing.viewer(), session);
    List<BlockDisplay> before = drawing.dots();

    session.setCorner2(drawing.block(39, 70, 39));
    AreaDisplays.refreshPreview(drawing.viewer());

    assertTrue(before.stream().noneMatch(BlockDisplay::isValid));
    assertTrue(drawing.dots().size() > before.size());
  }

  @Test
  void aShapeInAnotherWorldHasNoPreviewGrid() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.scenario().overworldBlock(10, 70, 10));
    session.setCorner2(drawing.scenario().overworldBlock(39, 70, 39));

    MapToolsUtils.startEditing(drawing.viewer(), session);

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void thePreviewGridIsLimitedToEightyBlocksAroundThePlayer() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.block(0, 70, 0));
    session.setCorner2(drawing.block(119, 70, 29));

    MapToolsUtils.startEditing(drawing.viewer(), session);

    List<BlockDisplay> dots = drawing.dots();
    assertFalse(dots.isEmpty());
    for (BlockDisplay dot : dots) {
      assertTrue(Math.hypot(dot.getLocation().getX(), dot.getLocation().getZ()) <= 80 + DELTA);
    }
  }

  @Test
  void leavingEditModeTakesTheGridAway() {
    stored(square());
    view();
    assertFalse(drawing.dots().isEmpty());

    MapToolsUtils.stopEditing(drawing.viewer());

    assertTrue(drawing.dots().isEmpty());
    assertTrue(drawing.gridLines().isEmpty());
  }

  @Test
  void aSmallAreaGetsOnlyTheDiagonalsThatCrossIt() {
    // A 4 by 4 square between 13 and 17 has no diagonal through it: 26 to 34 holds no multiple of
    // 12 for x + z and -4 to 4 holds only 0 for x - z, so just that one diagonal is drawn.
    stored(MapToolsTestUtils.rect("tiny", DRAWING, 13, 13, 16, 16));

    view();

    assertTrue(drawing.dots().isEmpty());
    assertEquals(1, drawing.gridLines().size());
    Location line = drawing.gridLines().getFirst().getLocation();
    assertEquals(13.0, line.getX(), DELTA);
  }
}
