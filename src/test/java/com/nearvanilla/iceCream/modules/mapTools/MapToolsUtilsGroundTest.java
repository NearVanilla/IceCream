package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Finding the ground at a spot. The world is a Mockito mock, since MockBukkit cannot say whether a
 * block is passable.
 */
class MapToolsUtilsGroundTest {
  private World world;

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
    world = mock(World.class);
    when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    when(world.getMinHeight()).thenReturn(0);
    when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES)))
        .thenReturn(70);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  /** Puts a block of a type at a height of the column at 5, 5. */
  private void block(int y, Material type, boolean passable, boolean liquid) {
    blockAt(5, y, 5, type, passable, liquid);
  }

  private void blockAt(int x, int y, int z, Material type, boolean passable, boolean liquid) {
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(type);
    when(block.isPassable()).thenReturn(passable);
    when(block.isLiquid()).thenReturn(liquid);
    when(world.getBlockAt(x, y, z)).thenReturn(block);
  }

  private void solid(int y) {
    block(y, Material.STONE, false, false);
  }

  private void log(int y) {
    block(y, Material.OAK_LOG, false, false);
  }

  private void air(int y) {
    block(y, Material.AIR, true, false);
  }

  private void water(int y) {
    block(y, Material.WATER, true, true);
  }

  @Test
  void groundBlockIsNullWhereTheChunkIsNotLoaded() {
    when(world.isChunkLoaded(0, 0)).thenReturn(false);

    assertNull(MapToolsUtils.groundBlock(world, 5.5, 5.5));
  }

  @Test
  void groundBlockLooksUpTheChunkOfTheBlockEvenForNegativeCoordinates() {
    when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
    when(world.isChunkLoaded(-1, -1)).thenReturn(true);
    when(world.isChunkLoaded(0, 0)).thenReturn(false);
    blockAt(-1, 70, -1, Material.STONE, false, false);

    // Block -0.5 is block -1, which is in chunk -1, not chunk 0.
    Location ground = MapToolsUtils.groundBlock(world, -0.5, -0.5);

    assertEquals(new Location(world, -1, 70, -1), ground);
    assertNull(MapToolsUtils.groundBlock(world, 0.5, 0.5));
  }

  @Test
  void groundBlockIsTheHighestBlockOfTheColumn() {
    solid(70);

    assertEquals(new Location(world, 5, 70, 5), MapToolsUtils.groundBlock(world, 5.9, 5.1));
  }

  @Test
  void groundBlockIsTheBlockTheSpotIsIn() {
    solid(70);
    when(world.getHighestBlockYAt(4, 5, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(12);
    blockAt(4, 12, 5, Material.STONE, false, false);

    // 5.0 is the start of block 5, and 4.99 is still in block 4.
    assertEquals(70, MapToolsUtils.groundBlock(world, 5.0, 5.0).getBlockY());
    assertEquals(4, MapToolsUtils.groundBlock(world, 4.99, 5.0).getBlockX());
  }

  @Test
  void aPassableBlockOnTopStaysTheGroundWhenNoTreeIsAbove() {
    // Snow, grass or a flower on the ground is not a tree, so it is not walked through.
    block(70, Material.SHORT_GRASS, true, false);

    assertEquals(70, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void aTreeTrunkIsWalkedDownToTheGround() {
    log(70);
    log(69);
    log(68);
    solid(67);

    assertEquals(67, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void leavesCountAsPartOfTheTree() {
    block(70, Material.OAK_LEAVES, false, false);
    log(69);
    solid(68);

    assertEquals(68, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void theAirUnderABranchIsWalkedThrough() {
    log(70);
    air(69);
    air(68);
    solid(67);

    assertEquals(67, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void aTreeStandingInWaterEndsAtTheWater() {
    log(70);
    log(69);
    water(68);

    assertEquals(68, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void aTreeStopsAtAnyOtherSolidBlock() {
    log(70);
    block(69, Material.DIRT, false, false);

    assertEquals(69, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void theWalkDownEndsAtTheBottomOfTheWorld() {
    when(world.getHighestBlockYAt(5, 5, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(3);
    log(3);
    log(2);
    log(1);
    log(0);

    assertEquals(0, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void theWalkDownEndsAtTheLowestBlockOfADeeperWorld() {
    when(world.getMinHeight()).thenReturn(-64);
    when(world.getHighestBlockYAt(5, 5, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(-61);
    for (int y = -64; y <= -61; y++) log(y);

    assertEquals(-64, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }

  @Test
  void aSpotAlreadyAtTheBottomOfTheWorldIsItsOwnGround() {
    when(world.getHighestBlockYAt(5, 5, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(0);

    assertEquals(0, MapToolsUtils.groundBlock(world, 5.5, 5.5).getBlockY());
  }
}
