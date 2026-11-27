package com.nearvanilla.iceCream.modules.growthFreeze;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * FrozenBlockStore keeps the frozen marker for individual blocks.
 *
 * <p>Crops, saplings and eggs are not tile entities, so they cannot hold a persistent data
 * container of their own. Each marker is instead kept as its own key on the containing chunk, named
 * after the block's position, which saves with the region file and makes a lookup a single key
 * check.
 *
 * <p>Callers pass the plant's anchor, from {@link FreezableBlocks#anchor(Block)}, so a plant that
 * spans several blocks has exactly one marker.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 * @see FreezableBlocks
 */
public final class FrozenBlockStore {
  private static final String KEY_PREFIX = "growthfreeze.frozen.";
  private static final int RELATIVE_MASK = 15;

  private final String namespace;

  /**
   * Creates a store writing keys under a namespace.
   *
   * @param namespace the plugin's key namespace
   */
  public FrozenBlockStore(String namespace) {
    this.namespace = namespace;
  }

  /**
   * Builds the marker key for a block, named by its position within its own chunk.
   *
   * @param block the block to build a key for
   * @return the key holding that block's marker
   */
  private NamespacedKey keyFor(Block block) {
    return new NamespacedKey(
        namespace,
        KEY_PREFIX
            + (block.getX() & RELATIVE_MASK)
            + "_"
            + block.getY()
            + "_"
            + (block.getZ() & RELATIVE_MASK));
  }

  /**
   * Checks whether a position carries a frozen marker.
   *
   * @param anchor the plant's anchor block
   * @return true when the plant is frozen
   */
  public boolean isFrozen(Block anchor) {
    PersistentDataContainer container = anchor.getChunk().getPersistentDataContainer();
    return !container.isEmpty() && container.has(keyFor(anchor));
  }

  /**
   * Adds or removes the frozen marker for a plant.
   *
   * @param anchor the plant's anchor block
   * @param frozen true to stop the plant growing, false to allow it again
   */
  public void setFrozen(Block anchor, boolean frozen) {
    PersistentDataContainer container = anchor.getChunk().getPersistentDataContainer();
    if (frozen) {
      container.set(keyFor(anchor), PersistentDataType.BOOLEAN, true);
    } else {
      container.remove(keyFor(anchor));
    }
  }

  /**
   * Drops markers in a chunk whose block is no longer a freezable anchor.
   *
   * <p>Run when a chunk loads, this catches markers left behind by plants destroyed without a
   * player breaking them, such as trampled crops, washed away saplings or grown trees. Chunks
   * holding no data at all skip the scan entirely, which is almost all of them.
   *
   * @param chunk the chunk to tidy
   */
  public void prune(Chunk chunk) {
    PersistentDataContainer container = chunk.getPersistentDataContainer();
    if (container.isEmpty()) {
      return;
    }
    for (NamespacedKey key : container.getKeys()) {
      if (!key.getNamespace().equals(namespace) || !key.getKey().startsWith(KEY_PREFIX)) {
        continue;
      }
      String[] parts = key.getKey().substring(KEY_PREFIX.length()).split("_");
      if (parts.length != 3) {
        continue;
      }
      Block block;
      try {
        block =
            chunk.getBlock(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (NumberFormatException exception) {
        // Not a marker this module wrote; leave it alone.
        continue;
      }
      if (!FreezableBlocks.isFreezable(block) || !FreezableBlocks.anchor(block).equals(block)) {
        container.remove(key);
      }
    }
  }
}
