package com.nearvanilla.iceCream.modules.growthFreeze;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Hangable;

/**
 * FreezableBlocks decides which blocks a golden dandelion can freeze, and which block of a plant
 * holds the frozen marker.
 *
 * <p>Plants that span several blocks are frozen as a whole, so their marker always sits on one
 * anchor block: the bottom half of a pitcher crop, and the lowest block of a bamboo stalk. Every
 * other freezable block is its own anchor.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 * @see FrozenBlockStore
 */
public final class FreezableBlocks {
  private static final Set<Material> GROWING =
      EnumSet.of(
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
          Material.BAMBOO_SAPLING,
          Material.BAMBOO);

  private FreezableBlocks() {}

  /**
   * Checks whether a block is one a golden dandelion can freeze.
   *
   * <p>A hanging mangrove propagule is excluded: it ages without any server event to stop, and it
   * cannot grow into a tree until it is planted.
   *
   * @param block the block to test
   * @return true when the block grows or hatches in a way this module can stop
   */
  public static boolean isFreezable(Block block) {
    Material type = block.getType();
    if (GROWING.contains(type)) {
      return true;
    }
    if (!Tag.SAPLINGS.isTagged(type)) {
      return false;
    }
    return !(block.getBlockData() instanceof Hangable hangable && hangable.isHanging());
  }

  /**
   * Checks whether a block is part of a bamboo stalk, including the sapling at its foot.
   *
   * @param type the block type to test
   * @return true for bamboo and bamboo saplings
   */
  public static boolean isBamboo(Material type) {
    return type == Material.BAMBOO || type == Material.BAMBOO_SAPLING;
  }

  /**
   * Finds the block that holds the frozen marker for the plant a block belongs to.
   *
   * @param block a block of the plant
   * @return the plant's anchor, which is the block itself for single-block plants
   */
  public static Block anchor(Block block) {
    Material type = block.getType();
    if (type == Material.PITCHER_CROP
        && block.getBlockData() instanceof Bisected bisected
        && bisected.getHalf() == Bisected.Half.TOP) {
      return block.getRelative(BlockFace.DOWN);
    }
    if (type == Material.BAMBOO) {
      int minHeight = block.getWorld().getMinHeight();
      Block base = block;
      while (base.getY() > minHeight && isBamboo(base.getRelative(BlockFace.DOWN).getType())) {
        base = base.getRelative(BlockFace.DOWN);
      }
      return base;
    }
    return block;
  }
}
