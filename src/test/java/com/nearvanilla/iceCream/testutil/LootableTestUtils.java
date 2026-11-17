package com.nearvanilla.iceCream.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.loot.LootTable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.block.state.ChestStateMock;
import org.mockbukkit.mockbukkit.entity.StorageMinecartMock;

/**
 * Helpers for testing code that reads loot tables, which MockBukkit does not implement: a chest's
 * state and a storage minecart both throw an unimplemented operation exception from {@code
 * getLootTable()} and {@code setLootTable(...)}. The chests and minecarts made here keep the table
 * they were given, and a chest's snapshots keep it too, like a real chest's.
 */
public final class LootableTestUtils {

  private LootableTestUtils() {}

  /**
   * Creates a loot table that only knows its key, which is all the plugin reads from one.
   *
   * @param path the path inside the {@code minecraft} namespace, for example {@code
   *     chests/simple_dungeon}
   * @return a mocked loot table with that key
   */
  public static LootTable lootTable(String path) {
    LootTable table = mock(LootTable.class);
    when(table.getKey()).thenReturn(NamespacedKey.minecraft(path));
    return table;
  }

  /**
   * Places a chest in the world. Its state supports {@code getLootTable()}, so a chest without a
   * loot table behaves like a chest a player placed.
   *
   * @param world the world to place the chest in
   * @param x the block x coordinate
   * @param y the block y coordinate
   * @param z the block z coordinate
   * @param lootTable the table the chest should have, or null for an ordinary chest
   * @return the block the chest was placed at
   */
  public static Block chest(World world, int x, int y, int z, LootTable lootTable) {
    BlockMock block = (BlockMock) world.getBlockAt(x, y, z);
    block.setType(Material.CHEST);
    block.setState(new LootableChestState(block, lootTable));
    return block;
  }

  /**
   * Creates a chest state that is not placed in any world, like the one a loot generation event
   * holds when its container is an item or a plugin's own inventory.
   *
   * @param lootTable the table the chest should have, or null
   * @return an unplaced chest state
   */
  public static ChestStateMock unplacedChest(LootTable lootTable) {
    return new LootableChestState(Material.CHEST, lootTable);
  }

  /**
   * Creates a storage minecart that supports {@code getLootTable()}.
   *
   * @param server the mock server
   * @param lootTable the table the minecart should have, or null for an ordinary minecart
   * @return the minecart, which is not spawned in a world
   */
  public static StorageMinecartMock storageMinecart(ServerMock server, LootTable lootTable) {
    return new LootableStorageMinecart(server, lootTable);
  }

  private static final class LootableChestState extends ChestStateMock {
    private LootTable lootTable;

    LootableChestState(Block block, LootTable lootTable) {
      super(block);
      this.lootTable = lootTable;
    }

    LootableChestState(Material material, LootTable lootTable) {
      super(material);
      this.lootTable = lootTable;
    }

    private LootableChestState(LootableChestState other) {
      super(other);
      this.lootTable = other.lootTable;
    }

    @Override
    public LootTable getLootTable() {
      return lootTable;
    }

    @Override
    public void setLootTable(LootTable lootTable) {
      this.lootTable = lootTable;
    }

    @Override
    public void setLootTable(LootTable lootTable, long seed) {
      this.lootTable = lootTable;
      setSeed(seed);
    }

    @Override
    public ChestStateMock getSnapshot() {
      return new LootableChestState(this);
    }

    @Override
    public ChestStateMock copy() {
      return new LootableChestState(this);
    }
  }

  private static final class LootableStorageMinecart extends StorageMinecartMock {
    private LootTable lootTable;

    LootableStorageMinecart(ServerMock server, LootTable lootTable) {
      super(server, UUID.randomUUID());
      this.lootTable = lootTable;
    }

    @Override
    public LootTable getLootTable() {
      return lootTable;
    }

    @Override
    public void setLootTable(LootTable lootTable) {
      this.lootTable = lootTable;
    }

    @Override
    public void setLootTable(LootTable lootTable, long seed) {
      this.lootTable = lootTable;
    }
  }
}
