package com.nearvanilla.iceCream.modules.mapTools;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Keeping the drawings up to date as players move: what {@code update} redraws and when, and
 * redrawing for everyone in edit mode.
 */
class AreaDisplaysUpdateTest {
  private static final Color GOLD = Color.fromRGB(0xFFAA00);

  private DisplayScenario drawing;
  private Viewer alex;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    drawing.world().loadChunks(-200, -200, 300, 300);
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    alex = drawing.viewer();
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private void view() {
    MapToolsUtils.startEditing(alex, EditSession.view());
  }

  /** What is drawn for the saved area, without its grid dots: ghost blocks and outline pieces. */
  private List<BlockDisplay> savedOutline() {
    return drawing.world().displays().stream()
        .filter(d -> GOLD.equals(DisplayTestUtils.glowColor(d)))
        .filter(d -> d.getTransformation().getScale().x() != 0.2f)
        .toList();
  }

  private List<BlockDisplay> savedDots() {
    return drawing.dots().stream().filter(d -> GOLD.equals(DisplayTestUtils.glowColor(d))).toList();
  }

  private List<BlockDisplay> previewGhosts() {
    return drawing.ghosts().stream()
        .filter(d -> Color.WHITE.equals(DisplayTestUtils.glowColor(d)))
        .toList();
  }

  private List<BlockDisplay> previewDots() {
    return drawing.dots().stream()
        .filter(d -> Color.WHITE.equals(DisplayTestUtils.glowColor(d)))
        .toList();
  }

  private static boolean allValid(List<BlockDisplay> displays) {
    return displays.stream().allMatch(BlockDisplay::isValid);
  }

  private static boolean noneValid(List<BlockDisplay> displays) {
    return displays.stream().noneMatch(BlockDisplay::isValid);
  }

  private void moveBy(double blocks) {
    drawing.moveTo(alex, blocks, 0);
  }

  /** A shape being drawn, so the player has a preview and a preview grid. */
  private EditSession drawnRectangle() {
    EditSession session = EditSession.create(Mode.RECT);
    session.setCorner1(drawing.block(20, 70, 20));
    session.setCorner2(drawing.block(39, 70, 39));
    return session;
  }

  // Areas and their outline

  @Test
  void aPlayerWhoHasNotMovedFarKeepsTheirDrawing() {
    view();
    List<BlockDisplay> outline = savedOutline();
    assertFalse(outline.isEmpty());

    moveBy(42);
    AreaDisplays.update(alex);

    assertTrue(allValid(outline));
    assertTrue(allValid(savedDots()));
  }

  @Test
  void aPlayerWhoMovedMoreThanAThirdOfTheViewDistanceGetsANewDrawing() {
    view();
    List<BlockDisplay> outline = savedOutline();
    List<BlockDisplay> dots = savedDots();

    moveBy(43);
    AreaDisplays.update(alex);

    assertTrue(noneValid(outline));
    assertTrue(noneValid(dots));
    assertFalse(savedOutline().isEmpty());
    assertTrue(allValid(savedOutline()));
  }

  @Test
  void movingAlongZCountsTooAndIsMeasuredFromWhereTheDrawingWasMade() {
    view();
    List<BlockDisplay> outline = savedOutline();

    drawing.moveTo(alex, 30, 30);
    AreaDisplays.update(alex);
    // About 42.4 blocks away: still within a third of the view distance.
    assertTrue(allValid(outline));

    drawing.moveTo(alex, 31, 31);
    AreaDisplays.update(alex);
    // About 43.8 blocks away.
    assertTrue(noneValid(outline));
  }

  @Test
  void aNewDrawingIsMeasuredFromWhereThePlayerWasWhenItWasMade() {
    view();
    moveBy(43);
    AreaDisplays.update(alex);
    List<BlockDisplay> second = savedOutline();

    // 43 more blocks from the start but only 2 from the second drawing.
    moveBy(45);
    AreaDisplays.update(alex);

    assertTrue(allValid(second));
  }

  @Test
  void movingToAnotherWorldRedrawsEvenWithoutMovingFar() {
    view();
    List<BlockDisplay> outline = savedOutline();

    alex.setLocation(new Location(drawing.scenario().overworld(), 0, 64, 0));
    AreaDisplays.update(alex);

    assertTrue(noneValid(outline));
    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aPlayerInEditModeWithoutADrawingGetsOne() {
    // The session is put in place without drawing anything, as when the sessions map changes
    // somewhere else.
    MapToolsModule.sessions.put(alex.getUniqueId(), EditSession.view());
    assertTrue(drawing.world().displays().isEmpty());

    AreaDisplays.update(alex);

    assertFalse(savedOutline().isEmpty());
    assertFalse(savedDots().isEmpty());
  }

  @Test
  void aPlayerWhoLeftEditModeLosesTheirAreasAtTheNextUpdate() {
    view();
    List<BlockDisplay> outline = savedOutline();
    List<BlockDisplay> dots = savedDots();
    MapToolsModule.sessions.remove(alex.getUniqueId());

    AreaDisplays.update(alex);

    assertTrue(noneValid(outline));
    assertTrue(noneValid(dots));
    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void aPlayerNotInEditModeWithNothingDrawnStaysEmptyHanded() {
    AreaDisplays.update(alex);

    assertTrue(drawing.world().displays().isEmpty());
  }

  // The grid, which is redrawn sooner

  @Test
  void theGridIsRedrawnAfterAThirdOfItsShorterDistanceWhileTheOutlineIsKept() {
    view();
    List<BlockDisplay> outline = savedOutline();
    List<BlockDisplay> dots = savedDots();
    assertFalse(dots.isEmpty());

    moveBy(26);
    AreaDisplays.update(alex);
    assertTrue(allValid(dots));

    moveBy(27);
    AreaDisplays.update(alex);

    assertTrue(allValid(outline), "the outline is good until 43 blocks");
    assertTrue(noneValid(dots));
    assertFalse(savedDots().isEmpty());
  }

  @Test
  void whenTheOutlineIsRedrawnTheGridIsRedrawnWithIt() {
    view();
    List<BlockDisplay> dots = savedDots();

    moveBy(43);
    AreaDisplays.update(alex);

    assertTrue(noneValid(dots));
  }

  // The preview

  @Test
  void aPreviewIsRedrawnAfterAThirdOfTheViewDistance() {
    MapToolsUtils.startEditing(alex, drawnRectangle());
    List<BlockDisplay> preview = previewGhosts();
    assertEquals(4, preview.size());

    moveBy(42);
    AreaDisplays.update(alex);
    assertTrue(allValid(preview));

    moveBy(43);
    AreaDisplays.update(alex);

    assertTrue(noneValid(preview));
    assertEquals(4, previewGhosts().size());
  }

  @Test
  void thePreviewGridIsRedrawnSoonerThanThePreview() {
    MapToolsUtils.startEditing(alex, drawnRectangle());
    List<BlockDisplay> ghosts = previewGhosts();
    List<BlockDisplay> dots = previewDots();
    assertFalse(dots.isEmpty());

    moveBy(26);
    AreaDisplays.update(alex);
    assertTrue(allValid(dots));

    moveBy(27);
    AreaDisplays.update(alex);

    assertTrue(allValid(ghosts));
    assertTrue(noneValid(dots));
    assertFalse(previewDots().isEmpty());
  }

  @Test
  void aPlayerJustViewingHasNoPreviewToRedraw() {
    view();

    moveBy(60);
    AreaDisplays.update(alex);

    assertTrue(previewGhosts().isEmpty());
    assertTrue(previewDots().isEmpty());
  }

  // Everyone in edit mode

  @Test
  void refreshAllAreasRedrawsThePlayersInEditModeAndNobodyElse() {
    Viewer sam = drawing.addViewer("Sam");
    Viewer kim = drawing.addViewer("Kim");
    MapToolsUtils.startEditing(alex, EditSession.view());
    MapToolsUtils.startEditing(sam, EditSession.view());
    List<BlockDisplay> alexBefore = alex.visibleDisplays();
    List<BlockDisplay> samBefore = sam.visibleDisplays();
    assertFalse(alexBefore.isEmpty());

    AreaDisplays.refreshAllAreas();

    assertTrue(noneValid(alexBefore));
    assertTrue(noneValid(samBefore));
    assertEquals(alexBefore.size(), alex.visibleDisplays().size());
    assertEquals(samBefore.size(), sam.visibleDisplays().size());
    assertTrue(kim.shown().isEmpty());
  }

  @Test
  void refreshAllAreasLeavesThePreviewAlone() {
    MapToolsUtils.startEditing(alex, drawnRectangle());
    List<BlockDisplay> preview = previewGhosts();
    List<BlockDisplay> saved = savedOutline();
    assertFalse(saved.isEmpty());

    AreaDisplays.refreshAllAreas();

    assertTrue(allValid(preview));
    assertTrue(noneValid(saved));
    assertFalse(savedOutline().isEmpty());
  }

  @Test
  void refreshAllAreasWithNobodyInEditModeDrawsNothing() {
    AreaDisplays.refreshAllAreas();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void savingAnAreaRedrawsItForEveryoneEditing() {
    Viewer sam = drawing.addViewer("Sam");
    MapToolsUtils.startEditing(alex, EditSession.view());
    MapToolsUtils.startEditing(sam, EditSession.view());
    List<BlockDisplay> before = drawing.world().displays();

    MapToolsUtils.saveArea(
        MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19).withColor("#0000ff"));

    assertTrue(noneValid(before));
    assertTrue(
        drawing.ghosts().stream()
            .allMatch(g -> Color.fromRGB(0x0000FF).equals(g.getGlowColorOverride())));
    assertEquals(8, drawing.ghosts().size());
  }

  // Removing everything

  @Test
  void clearAllRemovesPreviewsGridsAndOutlinesAlike() {
    MapToolsUtils.startEditing(alex, drawnRectangle());
    assertFalse(drawing.world().displays().isEmpty());

    AreaDisplays.clearAll();

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void afterClearAllUpdateStartsFromScratch() {
    view();
    AreaDisplays.clearAll();

    AreaDisplays.update(alex);

    assertFalse(savedOutline().isEmpty());
  }
}
