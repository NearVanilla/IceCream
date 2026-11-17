package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.KeyedWorld;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Starting to edit a saved area: its shape is placed on the ground of its world. */
class EditSessionEditTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private KeyedWorld overworld;

  @BeforeEach
  void setUp() {
    var server = MockBukkit.mock();
    overworld = MapToolsTestUtils.addWorld(server, WORLD);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private Location at(int x, int y, int z) {
    return new Location(overworld, x, y, z);
  }

  private void ground(int x, int y, int z) {
    overworld.loadChunk(x >> 4, z >> 4);
    overworld.getBlockAt(x, y, z).setType(Material.STONE);
  }

  @Test
  void editingARectangleKeepsItsIdWorldAndNoChanges() {
    EditSession session = EditSession.edit(MapToolsTestUtils.rect("home", WORLD, 2, 3, 5, 7));

    assertEquals(Mode.RECT, session.getMode());
    assertEquals("home", session.getAreaId());
    assertEquals(WORLD, session.getWorld());
    assertTrue(session.isEditingArea());
    assertFalse(session.isNew());
    assertFalse(session.isViewing());
    assertFalse(session.hasChanges());
    assertNull(session.getPicked());
  }

  @Test
  void editingARectangleGivesBackTheSameShape() {
    MapArea area = MapToolsTestUtils.rect("home", WORLD, -12, 3, 5, -7);

    AreaGeometry geometry = EditSession.edit(area).toGeometry();

    assertNotNull(geometry);
    assertEquals(area.points(), geometry.points());
    assertEquals(MapArea.RECT, geometry.shape());
    assertEquals(WORLD, geometry.world());
  }

  @Test
  void theCornersOfARectangleAreItsFirstAndLastBlocks() {
    EditSession session = EditSession.edit(MapToolsTestUtils.rect("home", WORLD, 2, 3, 5, 7));

    List<Location> corners = session.definingBlocks();

    assertEquals(4, corners.size());
    assertEquals(2, corners.get(0).getBlockX());
    assertEquals(3, corners.get(0).getBlockZ());
    assertEquals(5, corners.get(2).getBlockX());
    assertEquals(7, corners.get(2).getBlockZ());
  }

  @Test
  void cornersInUnloadedChunksStandAtSeaLevel() {
    EditSession session = EditSession.edit(MapToolsTestUtils.rect("home", WORLD, 2, 3, 5, 7));

    assertEquals(
        List.of(at(2, 63, 3), at(5, 63, 3), at(5, 63, 7), at(2, 63, 7)), session.definingBlocks());
  }

  @Test
  void cornersInLoadedChunksStandOnTheGround() {
    ground(2, 70, 3);
    ground(5, 74, 7);

    EditSession session = EditSession.edit(MapToolsTestUtils.rect("home", WORLD, 2, 3, 5, 7));

    assertEquals(
        List.of(at(2, 70, 3), at(5, 70, 3), at(5, 74, 7), at(2, 70, 7)), session.definingBlocks());
  }

  @Test
  void aCornerInAnUnloadedChunkTakesTheLowestHeightOfTheLoadedOnes() {
    ground(2, 70, 3);

    // The far corner is two chunks away, so it is not loaded.
    EditSession session = EditSession.edit(MapToolsTestUtils.rect("home", WORLD, 2, 3, 40, 3));

    Location far = session.definingBlocks().get(2);
    assertEquals(40, far.getBlockX());
    assertEquals(70, far.getBlockY());
  }

  @Test
  void editingAPolygonPutsEachPointOnTheGroundOfItsBlock() {
    ground(1, 70, 1);
    ground(3, 65, 3);

    EditSession session =
        EditSession.edit(
            MapToolsTestUtils.polygon("shape", WORLD, 1.5, 1.5, 3.5, 3.5, 100.5, 100.5));

    assertEquals(Mode.POLYGON, session.getMode());
    assertEquals(3, session.getPointCount());
    assertEquals(List.of(at(1, 70, 1), at(3, 65, 3), at(100, 65, 100)), session.definingBlocks());
  }

  @Test
  void editingAPolygonGivesBackThePointsAsBlockCenters() {
    MapArea area = MapToolsTestUtils.polygon("shape", WORLD, 0, 0, 10.5, 0.5, -3.5, 10.9);

    AreaGeometry geometry = EditSession.edit(area).toGeometry();

    assertEquals(
        List.of(new MapPoint(0.5, 0.5), new MapPoint(10.5, 0.5), new MapPoint(-3.5, 10.5)),
        geometry.points());
  }

  @Test
  void editingACircleGivesBackItsCenterAndRoundedRadius() {
    MapArea area = MapToolsTestUtils.circle("pond", WORLD, 5, -6, 12);

    EditSession session = EditSession.edit(area);

    assertEquals(Mode.CIRCLE, session.getMode());
    assertEquals(12, session.getRadius());
    assertEquals(5, session.getCenter().getBlockX());
    assertEquals(-6, session.getCenter().getBlockZ());
    AreaGeometry geometry = session.toGeometry();
    assertEquals(area.center(), geometry.center());
    assertEquals(12.0, geometry.radius());
    assertEquals(area.points(), geometry.points());
  }

  @Test
  void editingACircleRoundsAFractionalRadius() {
    MapArea area =
        MapToolsTestUtils.circle("pond", WORLD, 0, 0, 10)
            .withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(0.5, 0.5), 10.5));

    assertEquals(11, EditSession.edit(area).getRadius());
    assertEquals(
        10,
        EditSession.edit(
                area.withGeometry(MapToolsUtils.circleShape(WORLD, new MapPoint(0.5, 0.5), 10.4)))
            .getRadius());
  }

  @Test
  void theCenterOfACircleStandsOnTheGround() {
    ground(5, 72, -6);

    EditSession session = EditSession.edit(MapToolsTestUtils.circle("pond", WORLD, 5, -6, 12));

    assertEquals(at(5, 72, -6), session.getCenter());
  }

  @Test
  void editingAMarkerGivesBackItsSpot() {
    MapArea area = MapToolsTestUtils.marker("spot", WORLD, -3, 8);
    ground(-3, 68, 8);

    EditSession session = EditSession.edit(area);

    assertEquals(Mode.MARKER, session.getMode());
    assertEquals(List.of(at(-3, 68, 8)), session.definingBlocks());
    assertEquals(area.points(), session.toGeometry().points());
    assertEquals(area.center(), session.toGeometry().center());
    assertNull(session.getRadius());
  }

  @Test
  void anAreaInAWorldThatIsNotLoadedHasNoShapeToEdit() {
    for (MapArea area :
        List.of(
            MapToolsTestUtils.rect("a", "minecraft:the_end", 0, 0, 5, 5),
            MapToolsTestUtils.polygon("b", "minecraft:the_end", 0, 0, 5, 0, 5, 5),
            MapToolsTestUtils.circle("c", "minecraft:the_end", 0, 0, 5),
            MapToolsTestUtils.marker("d", "minecraft:the_end", 0, 0))) {
      EditSession session = EditSession.edit(area);

      assertEquals(area.id(), session.getAreaId());
      assertEquals("minecraft:the_end", session.getWorld());
      assertNull(session.toGeometry());
      assertTrue(session.definingBlocks().isEmpty());
      assertTrue(session.previewOutline().isEmpty());
    }
  }
}
