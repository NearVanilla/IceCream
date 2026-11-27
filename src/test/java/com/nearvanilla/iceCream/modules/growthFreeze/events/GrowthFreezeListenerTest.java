package com.nearvanilla.iceCream.modules.growthFreeze.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.growthFreeze.FreezableBlocksTest;
import com.nearvanilla.iceCream.modules.growthFreeze.FrozenBlockStore;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class GrowthFreezeListenerTest {
  private final FrozenBlockStore store = new FrozenBlockStore("icecream");
  private final GrowthFreezeListener listener =
      new GrowthFreezeListener(store, Component.text("frozen"), Component.text("resumed"));
  private ServerMock server;
  private WorldMock world;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = new WorldMock(Material.DIRT, -64, 320, 4);
    server.addWorld(world);
    player = server.addPlayer();
    player.setGameMode(GameMode.SURVIVAL);
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

  private ItemStack holdDandelions(int amount) {
    ItemStack dandelions = ItemStack.of(Material.GOLDEN_DANDELION, amount);
    player.getInventory().setItemInMainHand(dandelions);
    return player.getInventory().getItemInMainHand();
  }

  private PlayerInteractEvent click(Block block, ItemStack item, EquipmentSlot hand) {
    PlayerInteractEvent event =
        new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP, hand);
    listener.onPlayerInteract(event);
    return event;
  }

  private PlayerInteractEvent click(Block block) {
    return click(block, player.getInventory().getItemInMainHand(), EquipmentSlot.HAND);
  }

  private BlockGrowEvent grow(Block block, Material into) {
    BlockState state = block.getState();
    state.setType(into);
    BlockGrowEvent event = new BlockGrowEvent(block, state);
    listener.onBlockGrow(event);
    return event;
  }

  @Test
  void clickingACropWithAGoldenDandelionFreezesItAndUsesOne() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    ItemStack dandelions = holdDandelions(3);

    PlayerInteractEvent event = click(wheat);

    assertTrue(store.isFrozen(wheat));
    assertEquals(2, dandelions.getAmount());
    assertEquals(Event.Result.DENY, event.useItemInHand());
    assertEquals(Event.Result.DENY, event.useInteractedBlock());
    assertEquals(Component.text("frozen"), player.nextActionBar());
  }

  @Test
  void clickingAFrozenCropAgainUnfreezesItAndUsesAnother() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    ItemStack dandelions = holdDandelions(3);

    click(wheat);
    click(wheat);

    assertFalse(store.isFrozen(wheat));
    assertEquals(1, dandelions.getAmount());
    player.nextActionBar();
    assertEquals(Component.text("resumed"), player.nextActionBar());
  }

  @Test
  void creativeModeKeepsTheDandelion() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    ItemStack dandelions = holdDandelions(3);
    player.setGameMode(GameMode.CREATIVE);

    click(wheat);

    assertTrue(store.isFrozen(wheat));
    assertEquals(3, dandelions.getAmount());
  }

  @Test
  void offHandIsIgnoredWhenTheMainHandAlsoHoldsADandelion() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    holdDandelions(1);
    ItemStack offHand = ItemStack.of(Material.GOLDEN_DANDELION);
    player.getInventory().setItemInOffHand(offHand);

    click(wheat, player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND);

    assertFalse(store.isFrozen(wheat));
  }

  @Test
  void otherItemsAndOtherBlocksAreIgnored() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    Block stone = place(1, 10, 0, Material.STONE);
    player.getInventory().setItemInMainHand(ItemStack.of(Material.DANDELION));
    click(wheat);
    assertFalse(store.isFrozen(wheat));

    holdDandelions(1);
    PlayerInteractEvent event = click(stone);
    assertFalse(store.isFrozen(stone));
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
  }

  @Test
  void frozenCropDoesNotGrowButOthersDo() {
    Block frozen = place(0, 10, 0, Material.WHEAT);
    Block free = place(1, 10, 0, Material.WHEAT);
    store.setFrozen(frozen, true);

    assertTrue(grow(frozen, Material.WHEAT).isCancelled());
    assertFalse(grow(free, Material.WHEAT).isCancelled());
  }

  @Test
  void clickingThePitcherCropTopHalfFreezesTheWholePlant() {
    Block bottom = place(0, 10, 0, Material.PITCHER_CROP);
    Block top = FreezableBlocksTest.pitcherTop(bottom);
    holdDandelions(1);

    click(top);

    assertTrue(store.isFrozen(bottom));
    assertTrue(grow(bottom, Material.PITCHER_CROP).isCancelled());
    BlockGrowEvent topGrowth = new BlockGrowEvent(top, bottom.getState());
    listener.onBlockGrow(topGrowth);
    assertTrue(topGrowth.isCancelled());
  }

  @Test
  void frozenStemDoesNotGrowFruit() {
    Block stem = place(0, 10, 0, Material.MELON_STEM);
    Block fruitSpot = place(1, 10, 0, Material.AIR);
    store.setFrozen(stem, true);

    assertTrue(grow(fruitSpot, Material.MELON).isCancelled());
  }

  @Test
  void fruitStillGrowsBesideAFrozenStemOfTheOtherKind() {
    Block stem = place(0, 10, 0, Material.PUMPKIN_STEM);
    Block fruitSpot = place(1, 10, 0, Material.AIR);
    store.setFrozen(stem, true);

    assertFalse(grow(fruitSpot, Material.MELON).isCancelled());
  }

  @Test
  void clickingAnyPartOfABambooStalkStopsItGrowingTaller() {
    Block base = place(0, 10, 0, Material.BAMBOO);
    Block middle = place(0, 11, 0, Material.BAMBOO);
    Block top = place(0, 12, 0, Material.BAMBOO);
    Block above = place(0, 13, 0, Material.AIR);
    holdDandelions(1);

    click(middle);

    assertTrue(store.isFrozen(base));
    BlockState newState = above.getState();
    newState.setType(Material.BAMBOO);
    BlockSpreadEvent event = new BlockSpreadEvent(above, top, newState);
    listener.onBlockSpread(event);
    assertTrue(event.isCancelled());
  }

  @Test
  void frozenSaplingDoesNotBecomeATree() {
    Block sapling = place(0, 10, 0, Material.OAK_SAPLING);
    store.setFrozen(sapling, true);
    BlockState log = sapling.getState();
    log.setType(Material.OAK_LOG);

    StructureGrowEvent event =
        new StructureGrowEvent(sapling.getLocation(), TreeType.TREE, false, null, List.of(log));
    listener.onStructureGrow(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void anyFrozenSaplingInATwoByTwoStopsTheTree() {
    Block corner = place(0, 10, 0, Material.SPRUCE_SAPLING);
    Block frozen = place(1, 10, 1, Material.SPRUCE_SAPLING);
    store.setFrozen(frozen, true);
    BlockState first = corner.getState();
    first.setType(Material.SPRUCE_LOG);
    BlockState second = frozen.getState();
    second.setType(Material.SPRUCE_LOG);

    StructureGrowEvent event =
        new StructureGrowEvent(
            corner.getLocation(), TreeType.MEGA_REDWOOD, false, null, List.of(first, second));
    listener.onStructureGrow(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void boneMealDoesNothingOnAFrozenBlock() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    store.setFrozen(wheat, true);

    BlockFertilizeEvent event = new BlockFertilizeEvent(wheat, player, List.of());
    listener.onBlockFertilize(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void frozenEggsDoNotHatch() {
    Block frogspawn = place(0, 10, 0, Material.FROGSPAWN);
    Block snifferEgg = place(1, 10, 0, Material.SNIFFER_EGG);
    store.setFrozen(frogspawn, true);
    store.setFrozen(snifferEgg, true);

    BlockFadeEvent frogspawnHatch =
        new BlockFadeEvent(frogspawn, place(0, 20, 0, Material.AIR).getState());
    BlockFadeEvent snifferHatch =
        new BlockFadeEvent(snifferEgg, place(1, 20, 0, Material.AIR).getState());
    listener.onBlockFade(frogspawnHatch);
    listener.onBlockFade(snifferHatch);

    assertTrue(frogspawnHatch.isCancelled());
    assertTrue(snifferHatch.isCancelled());
    assertTrue(grow(snifferEgg, Material.SNIFFER_EGG).isCancelled());
  }

  @Test
  void unfreezingFrogspawnLeavesItInPlace() {
    Block frogspawn = place(0, 10, 0, Material.FROGSPAWN);
    holdDandelions(2);

    click(frogspawn);
    click(frogspawn);

    assertFalse(store.isFrozen(frogspawn));
    assertEquals(Material.FROGSPAWN, frogspawn.getType());
  }

  @Test
  void hatchingClearsTheMarker() {
    Block frogspawn = place(0, 10, 0, Material.FROGSPAWN);
    store.setFrozen(frogspawn, true);

    listener.onBlockFaded(new BlockFadeEvent(frogspawn, place(0, 20, 0, Material.AIR).getState()));

    assertFalse(store.isFrozen(frogspawn));
  }

  @Test
  void breakingAFrozenBlockClearsItsMarker() {
    Block wheat = place(0, 10, 0, Material.WHEAT);
    store.setFrozen(wheat, true);

    listener.onBlockBreak(new BlockBreakEvent(wheat, player));

    assertFalse(store.isFrozen(wheat));
  }

  @Test
  void breakingTheTopOfAFrozenBambooStalkKeepsTheRestFrozen() {
    Block base = place(0, 10, 0, Material.BAMBOO);
    Block top = place(0, 11, 0, Material.BAMBOO);
    store.setFrozen(base, true);

    listener.onBlockBreak(new BlockBreakEvent(top, player));

    assertTrue(store.isFrozen(base));
  }

  @Test
  void plantingWhereAFrozenCropUsedToBeStartsUnfrozen() {
    Block spot = place(0, 10, 0, Material.WHEAT);
    store.setFrozen(spot, true);
    spot.setType(Material.CARROTS);
    BlockState replaced = place(0, 20, 0, Material.AIR).getState();
    Block against = world.getBlockAt(0, 9, 0);

    listener.onBlockPlace(
        new BlockPlaceEvent(
            spot,
            replaced,
            against,
            ItemStack.of(Material.CARROT),
            player,
            true,
            EquipmentSlot.HAND));

    assertFalse(store.isFrozen(spot));
  }

  @Test
  void stackingBambooOnAFrozenStalkKeepsItFrozen() {
    Block base = place(0, 10, 0, Material.BAMBOO);
    Block placed = place(0, 11, 0, Material.BAMBOO);
    store.setFrozen(base, true);

    listener.onBlockPlace(
        new BlockPlaceEvent(
            placed,
            place(0, 20, 0, Material.AIR).getState(),
            base,
            ItemStack.of(Material.BAMBOO),
            player,
            true,
            EquipmentSlot.HAND));

    assertTrue(store.isFrozen(base));
  }

  @Test
  void aMobEatingBerriesKeepsTheBushFrozen() {
    Block bush = place(0, 10, 0, Material.SWEET_BERRY_BUSH);
    store.setFrozen(bush, true);

    listener.onEntityChangeBlock(
        new EntityChangeBlockEvent(player, bush, Material.SWEET_BERRY_BUSH.createBlockData()));

    assertTrue(store.isFrozen(bush));
  }

  @Test
  void aMobPlantingWhereAFrozenCropUsedToBeStartsUnfrozen() {
    Block spot = place(0, 10, 0, Material.AIR);
    store.setFrozen(spot, true);

    listener.onEntityChangeBlock(
        new EntityChangeBlockEvent(player, spot, Material.WHEAT.createBlockData()));

    assertFalse(store.isFrozen(spot));
  }
}
