package com.nearvanilla.iceCream.modules.growthFreeze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

class FrozenBlockStoreTest {
  private final FrozenBlockStore store = new FrozenBlockStore("icecream");
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
  void freezingAndUnfreezingRoundTrips() {
    Block wheat = place(3, 10, 5, Material.WHEAT);

    store.setFrozen(wheat, true);
    assertTrue(store.isFrozen(wheat));

    store.setFrozen(wheat, false);
    assertFalse(store.isFrozen(wheat));
  }

  @Test
  void markersAreKeptPerPosition() {
    Block frozen = place(3, 10, 5, Material.WHEAT);
    Block other = place(4, 10, 5, Material.WHEAT);

    store.setFrozen(frozen, true);

    assertFalse(store.isFrozen(other));
  }

  @Test
  void markersWorkBelowZeroAndInNegativeChunks() {
    Block wheat = place(-20, -30, -7, Material.WHEAT);

    store.setFrozen(wheat, true);

    assertTrue(store.isFrozen(wheat));
  }

  @Test
  void pruneDropsMarkersForBlocksThatAreNoLongerFreezable() {
    Block gone = place(1, 10, 1, Material.WHEAT);
    Block kept = place(2, 10, 1, Material.CARROTS);
    store.setFrozen(gone, true);
    store.setFrozen(kept, true);
    gone.setType(Material.AIR);

    store.prune(gone.getChunk());

    assertFalse(store.isFrozen(gone));
    assertTrue(store.isFrozen(kept));
  }

  @Test
  void pruneDropsMarkersThatAreNotAtTheirBlocksAnchor() {
    place(1, 10, 1, Material.BAMBOO);
    Block upper = place(1, 11, 1, Material.BAMBOO);
    store.setFrozen(upper, true);

    store.prune(upper.getChunk());

    assertFalse(store.isFrozen(upper));
  }

  @Test
  void pruneLeavesOtherModulesKeysAlone() {
    Block wheat = place(1, 10, 1, Material.WHEAT);
    var key = new org.bukkit.NamespacedKey("icecream", "vinegrowth.sheared.1_10_1");
    wheat
        .getChunk()
        .getPersistentDataContainer()
        .set(key, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

    store.prune(wheat.getChunk());

    assertTrue(wheat.getChunk().getPersistentDataContainer().has(key));
  }
}
