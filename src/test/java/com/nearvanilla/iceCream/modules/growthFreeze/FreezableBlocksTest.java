package com.nearvanilla.iceCream.modules.growthFreeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.MangrovePropagule;
import org.bukkit.block.data.type.PitcherCrop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

public class FreezableBlocksTest {
  private WorldMock world;

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
    world = new WorldMock(Material.DIRT, -64, 320, 4);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private Block place(int x, int y, int z, Material type) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(type);
    return block;
  }

  @Test
  void cropsEggsSaplingsAndBambooAreFreezable() {
    for (Material type :
        new Material[] {
          Material.WHEAT,
          Material.CARROTS,
          Material.POTATOES,
          Material.BEETROOTS,
          Material.TORCHFLOWER_CROP,
          Material.PITCHER_CROP,
          Material.SWEET_BERRY_BUSH,
          Material.MELON_STEM,
          Material.PUMPKIN_STEM,
          Material.COCOA,
          Material.NETHER_WART,
          Material.FROGSPAWN,
          Material.SNIFFER_EGG,
          Material.OAK_SAPLING,
          Material.CHERRY_SAPLING,
          Material.MANGROVE_PROPAGULE,
          Material.BAMBOO_SAPLING,
          Material.BAMBOO
        }) {
      assertTrue(FreezableBlocks.isFreezable(place(0, 10, 0, type)), type + " should be freezable");
    }
  }

  @Test
  void blocksThatNeverGrowAreNotFreezable() {
    assertFalse(FreezableBlocks.isFreezable(place(0, 10, 0, Material.STONE)));
    assertFalse(FreezableBlocks.isFreezable(place(0, 10, 0, Material.ATTACHED_MELON_STEM)));
    assertFalse(FreezableBlocks.isFreezable(place(0, 10, 0, Material.DANDELION)));
  }

  @Test
  void hangingPropaguleIsNotFreezable() {
    Block block = place(0, 10, 0, Material.MANGROVE_PROPAGULE);
    MangrovePropagule data = (MangrovePropagule) block.getBlockData();
    data.setHanging(true);
    block.setBlockData(data);

    assertFalse(FreezableBlocks.isFreezable(block));
  }

  /**
   * Stands in for the top half of a pitcher crop, since MockBukkit has no pitcher crop block data.
   */
  public static Block pitcherTop(Block bottom) {
    PitcherCrop data = mock(PitcherCrop.class);
    when(data.getHalf()).thenReturn(Bisected.Half.TOP);
    Block top = mock(Block.class);
    when(top.getType()).thenReturn(Material.PITCHER_CROP);
    when(top.getBlockData()).thenReturn(data);
    when(top.getRelative(BlockFace.DOWN)).thenReturn(bottom);
    when(top.getWorld()).thenReturn(bottom.getWorld());
    // MockBukkit hands out a block's own location, so build a new one rather than offset it.
    when(top.getLocation())
        .thenAnswer(
            invocation ->
                new Location(bottom.getWorld(), bottom.getX(), bottom.getY() + 1, bottom.getZ()));
    return top;
  }

  @Test
  void pitcherCropTopHalfAnchorsToBottomHalf() {
    Block bottom = place(0, 10, 0, Material.PITCHER_CROP);
    Block top = pitcherTop(bottom);

    assertEquals(bottom, FreezableBlocks.anchor(top));
    assertEquals(bottom, FreezableBlocks.anchor(bottom));
  }

  @Test
  void bambooAnchorsToTheBaseOfItsStalk() {
    Block base = place(0, 10, 0, Material.BAMBOO_SAPLING);
    place(0, 11, 0, Material.BAMBOO);
    Block top = place(0, 12, 0, Material.BAMBOO);

    assertEquals(base, FreezableBlocks.anchor(top));
    assertEquals(base, FreezableBlocks.anchor(base));
  }

  @Test
  void otherBlocksAnchorToThemselves() {
    Block wheat = place(0, 10, 0, Material.WHEAT);

    assertEquals(wheat, FreezableBlocks.anchor(wheat));
  }
}
