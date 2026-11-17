package com.nearvanilla.iceCream.modules.vineGrowth.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vineGrowth.VineGrowthUtils;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * What the listener does when a vine spreads, grows, is placed over, is broken or its chunk loads,
 * fired through the server.
 */
class VineGrowthListenerTest {
  private ServerMock server;
  private WorldMock world;
  private PlayerMock player;
  private Block vine;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    server.getPluginManager().registerEvents(new VineGrowthListener(), IceCream.instance);
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Gardener");
    vine = vine(0, 64, 0);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Block vine(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.VINE);
    return block;
  }

  private Block block(Material type, int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(type);
    return block;
  }

  private <E extends Event> E call(E event) {
    server.getPluginManager().callEvent(event);
    return event;
  }

  private BlockState vineState(Block block) {
    BlockState state = block.getState();
    state.setType(Material.VINE);
    return state;
  }

  private BlockState stateOf(Block block, Material type) {
    BlockState state = block.getState();
    state.setType(type);
    return state;
  }

  private BlockSpreadEvent spread(Block source, Block target, Material newType) {
    return new BlockSpreadEvent(target, source, stateOf(target, newType));
  }

  private BlockPlaceEvent place(Block placed, BlockState replaced) {
    return new BlockPlaceEvent(
        placed,
        replaced,
        placed.getRelative(BlockFace.DOWN),
        new ItemStack(placed.getType()),
        player,
        true,
        EquipmentSlot.HAND);
  }

  @Test
  void aSpreadFromAShearedVineIsCancelled() {
    Block source = vine(0, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);
    VineGrowthUtils.setSheared(source, true);

    BlockSpreadEvent event = call(spread(source, target, Material.VINE));

    assertTrue(event.isCancelled());
  }

  @Test
  void aSpreadFromAVineThatWasNotShearedIsAllowed() {
    Block source = vine(0, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);

    BlockSpreadEvent event = call(spread(source, target, Material.VINE));

    assertFalse(event.isCancelled());
  }

  @Test
  void aSpreadFromAnotherVineIsAllowedWhenOnlyTheNeighbourIsSheared() {
    Block source = vine(0, 65, 0);
    Block neighbour = vine(1, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);
    VineGrowthUtils.setSheared(neighbour, true);

    BlockSpreadEvent event = call(spread(source, target, Material.VINE));

    assertFalse(event.isCancelled());
  }

  @Test
  void aSpreadIntoAPositionClearsTheMarkerLeftThere() {
    Block source = vine(0, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);
    VineGrowthUtils.setSheared(target, true);

    call(spread(source, target, Material.VINE));

    target.setType(Material.VINE);
    assertFalse(VineGrowthUtils.isSheared(target));
  }

  @Test
  void aSpreadThatIsNotAVineIsLeftAlone() {
    Block source = vine(0, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);
    VineGrowthUtils.setSheared(source, true);
    VineGrowthUtils.setSheared(target, true);

    BlockSpreadEvent event = call(spread(source, target, Material.GRASS_BLOCK));

    assertFalse(event.isCancelled());
    target.setType(Material.VINE);
    assertTrue(VineGrowthUtils.isSheared(target));
  }

  @Test
  void aSpreadThatWasAlreadyCancelledIsLeftAlone() {
    Block source = vine(0, 65, 0);
    Block target = world.getBlockAt(0, 64, 1);
    VineGrowthUtils.setSheared(target, true);
    BlockSpreadEvent event = spread(source, target, Material.VINE);
    event.setCancelled(true);

    call(event);

    target.setType(Material.VINE);
    assertTrue(VineGrowthUtils.isSheared(target));
  }

  @Test
  void growthOfAShearedVineIsCancelled() {
    VineGrowthUtils.setSheared(vine, true);

    BlockGrowEvent event = call(new BlockGrowEvent(vine, vineState(vine)));

    assertTrue(event.isCancelled());
  }

  @Test
  void growthOfAVineThatWasNotShearedIsAllowed() {
    BlockGrowEvent event = call(new BlockGrowEvent(vine, vineState(vine)));

    assertFalse(event.isCancelled());
  }

  @Test
  void growthOfAVineIsAllowedWhenOnlyAnotherVineIsSheared() {
    VineGrowthUtils.setSheared(vine(1, 64, 0), true);

    BlockGrowEvent event = call(new BlockGrowEvent(vine, vineState(vine)));

    assertFalse(event.isCancelled());
  }

  @Test
  void growthThatIsNotAVineIsAllowed() {
    VineGrowthUtils.setSheared(vine, true);

    BlockGrowEvent event = call(new BlockGrowEvent(vine, stateOf(vine, Material.GRASS_BLOCK)));

    assertFalse(event.isCancelled());
  }

  @Test
  void loadingAChunkRemovesMarkersOfVinesThatAreGone() {
    Block gone = vine(1, 64, 1);
    VineGrowthUtils.setSheared(gone, true);
    gone.setType(Material.AIR);

    call(new ChunkLoadEvent(gone.getChunk(), false));

    gone.setType(Material.VINE);
    assertFalse(VineGrowthUtils.isSheared(gone));
  }

  @Test
  void loadingAChunkKeepsMarkersOfVinesThatAreStillThere() {
    VineGrowthUtils.setSheared(vine, true);

    call(new ChunkLoadEvent(vine.getChunk(), false));

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void breakingAShearedVineClearsItsMarker() {
    VineGrowthUtils.setSheared(vine, true);

    call(new BlockBreakEvent(vine, player));

    vine.setType(Material.AIR);
    vine.setType(Material.VINE);
    assertFalse(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void breakingAVineKeepsTheMarkersOfOtherVines() {
    Block other = vine(0, 65, 0);
    VineGrowthUtils.setSheared(vine, true);
    VineGrowthUtils.setSheared(other, true);

    call(new BlockBreakEvent(vine, player));

    assertTrue(VineGrowthUtils.isSheared(other));
  }

  @Test
  void breakingAnotherBlockKeepsTheMarkersOfVines() {
    Block stone = block(Material.STONE, 3, 64, 0);
    VineGrowthUtils.setSheared(vine, true);

    call(new BlockBreakEvent(stone, player));

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void aBreakThatWasCancelledKeepsTheMarker() {
    VineGrowthUtils.setSheared(vine, true);
    BlockBreakEvent event = new BlockBreakEvent(vine, player);
    event.setCancelled(true);

    call(event);

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void placingAVineClearsTheMarkerLeftAtThePosition() {
    Block position = world.getBlockAt(2, 64, 2);
    VineGrowthUtils.setSheared(position, true);
    BlockState replaced = position.getState();
    position.setType(Material.VINE);

    call(place(position, replaced));

    assertFalse(VineGrowthUtils.isSheared(position));
  }

  @Test
  void buildingOverAShearedVineClearsItsMarker() {
    Block position = vine(2, 64, 2);
    VineGrowthUtils.setSheared(position, true);
    BlockState replaced = position.getState();
    position.setType(Material.STONE);

    call(place(position, replaced));

    position.setType(Material.VINE);
    assertFalse(VineGrowthUtils.isSheared(position));
  }

  @Test
  void placingAnotherBlockKeepsTheMarkersOfVines() {
    VineGrowthUtils.setSheared(vine, true);
    Block position = world.getBlockAt(3, 64, 0);
    BlockState replaced = position.getState();
    position.setType(Material.STONE);

    call(place(position, replaced));

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void placingAVineKeepsTheMarkersOfOtherVines() {
    VineGrowthUtils.setSheared(vine, true);
    Block position = world.getBlockAt(3, 64, 0);
    BlockState replaced = position.getState();
    position.setType(Material.VINE);

    call(place(position, replaced));

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void aPlacementThatWasCancelledKeepsTheMarker() {
    Block position = world.getBlockAt(2, 64, 2);
    VineGrowthUtils.setSheared(position, true);
    BlockState replaced = position.getState();
    position.setType(Material.VINE);
    BlockPlaceEvent event = place(position, replaced);
    event.setCancelled(true);

    call(event);

    assertTrue(VineGrowthUtils.isSheared(position));
  }
}
