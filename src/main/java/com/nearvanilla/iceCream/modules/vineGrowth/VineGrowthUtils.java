package com.nearvanilla.iceCream.modules.vineGrowth;

import com.nearvanilla.iceCream.IceCream;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/**
 * VineGrowthUtils stores and reads the sheared marker for individual vine blocks.
 *
 * <p>Vines are not tile entities, so they cannot hold a persistent data container of their own.
 * Each marker is instead kept as its own key on the containing chunk, named after the block's
 * position, which saves with the region file and makes a lookup a single key check.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-09-15
 * @see VineGrowthModule
 */
public final class VineGrowthUtils {
  private static final String KEY_PREFIX = "vinegrowth.sheared.";
  private static final int RELATIVE_MASK = 15;

  private VineGrowthUtils() {}

  /**
   * Builds the marker key for a block, named by its position within its own chunk.
   *
   * @param block the block to build a key for
   * @return the key holding that block's marker
   */
  private static NamespacedKey keyFor(Block block) {
    return new NamespacedKey(
        IceCream.instance,
        KEY_PREFIX
            + (block.getX() & RELATIVE_MASK)
            + "_"
            + block.getY()
            + "_"
            + (block.getZ() & RELATIVE_MASK));
  }

  /**
   * Checks whether a vine block has been sheared and should no longer grow.
   *
   * @param block the block to test
   * @return true when the block is a vine carrying a sheared marker
   */
  public static boolean isSheared(Block block) {
    if (block.getType() != Material.VINE) {
      return false;
    }
    return block.getChunk().getPersistentDataContainer().has(keyFor(block));
  }

  /**
   * Removes any marker left at a position by a previous vine.
   *
   * <p>Called when a vine appears at a position, so a marker outliving the vine that earned it is
   * never inherited by its replacement. This is why the module does not need to watch every way a
   * vine can be destroyed.
   *
   * @param block the position a vine is appearing at
   */
  public static void clearStaleMarker(Block block) {
    var container = block.getChunk().getPersistentDataContainer();
    NamespacedKey key = keyFor(block);
    if (container.has(key)) {
      container.remove(key);
    }
  }

  /**
   * Drops markers in a chunk whose block is no longer a vine.
   *
   * <p>Run when a chunk loads, this is what keeps markers from outliving their vines without the
   * module having to watch every way a vine can be destroyed. Chunks holding no data at all skip
   * the scan entirely, which is almost all of them.
   *
   * @param chunk the chunk to tidy
   */
  public static void pruneChunk(Chunk chunk) {
    var container = chunk.getPersistentDataContainer();
    if (container.isEmpty()) {
      return;
    }
    for (NamespacedKey key : container.getKeys()) {
      if (!key.getKey().startsWith(KEY_PREFIX)) {
        continue;
      }
      String[] parts = key.getKey().substring(KEY_PREFIX.length()).split("_");
      if (parts.length != 3) {
        continue;
      }
      try {
        if (chunk
                .getBlock(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]))
                .getType()
            != Material.VINE) {
          container.remove(key);
        }
      } catch (NumberFormatException exception) {
        // Not a marker this module wrote; leave it alone.
      }
    }
  }

  /**
   * Adds or removes the sheared marker for a vine block.
   *
   * @param block the block to mark
   * @param sheared true to stop the block growing, false to allow it again
   */
  public static void setSheared(Block block, boolean sheared) {
    var container = block.getChunk().getPersistentDataContainer();
    if (sheared) {
      container.set(keyFor(block), PersistentDataType.BOOLEAN, true);
    } else {
      container.remove(keyFor(block));
    }
  }
}
