package com.nearvanilla.iceCream.modules.persistentHeads.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.persistentHeads.HeadStore;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

class PersistentHeadsListenerTest {
  private static final List<Component> LORE = List.of(Component.text("Killed by Steve"));

  private final HeadStore store = new HeadStore("icecream");
  private final List<Runnable> nextTick = new ArrayList<>();
  private ServerMock server;
  private PlayerMock player;
  private World world;
  private Location location;
  private Block block;
  private Skull skull;
  private PersistentDataContainer pdc;
  private PersistentHeadsListener listener;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    // A second player's container stands in for the head's real container.
    pdc = server.addPlayer().getPersistentDataContainer();

    world = mock(World.class);
    when(world.getGameRuleValue(GameRules.BLOCK_DROPS)).thenReturn(true);
    location = new Location(world, 1, 64, 1);
    block = mock(Block.class);
    skull = mock(Skull.class);
    when(block.getType()).thenReturn(Material.PLAYER_HEAD);
    when(block.getState()).thenReturn(skull);
    when(block.getWorld()).thenReturn(world);
    when(block.getLocation()).thenReturn(location);
    when(skull.getType()).thenReturn(Material.PLAYER_HEAD);
    when(skull.getPersistentDataContainer()).thenReturn(pdc);

    listener = new PersistentHeadsListener(store, nextTick::add);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static ItemStack headWithLore() {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    head.lore(LORE);
    return head;
  }

  private void storeLore() {
    store.save(pdc, LORE);
  }

  private BlockPlaceEvent place(ItemStack item) {
    BlockPlaceEvent event =
        new BlockPlaceEvent(block, skull, block, item, player, true, EquipmentSlot.HAND);
    listener.onPlace(event);
    return event;
  }

  private List<ItemStack> droppedItems() {
    nextTick.forEach(Runnable::run);
    ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
    verify(world).dropItemNaturally(any(Location.class), dropped.capture());
    return dropped.getAllValues();
  }

  @Test
  void placingHeadWithLoreStoresIt() {
    place(headWithLore());

    assertEquals(LORE, store.read(pdc).lore());
    verify(skull).update();
  }

  @Test
  void placingHeadWithoutLoreStoresNothing() {
    place(new ItemStack(Material.PLAYER_HEAD));

    assertTrue(store.read(pdc).isEmpty());
    verify(skull, never()).update();
  }

  @Test
  void placingOtherBlocksStoresNothing() {
    ItemStack dirt = new ItemStack(Material.DIRT);
    dirt.lore(LORE);

    place(dirt);

    assertTrue(store.read(pdc).isEmpty());
  }

  @Test
  void breakingHeadRestoresLore() {
    storeLore();
    Item item = mock(Item.class);
    when(item.getItemStack()).thenReturn(new ItemStack(Material.PLAYER_HEAD));

    listener.onDropItems(new BlockDropItemEvent(block, skull, player, List.of(item)));

    ArgumentCaptor<ItemStack> restored = ArgumentCaptor.forClass(ItemStack.class);
    verify(item).setItemStack(restored.capture());
    assertEquals(LORE, restored.getValue().lore());
  }

  @Test
  void breakingHeadWithNothingStoredLeavesDropsAlone() {
    Item item = mock(Item.class);
    when(item.getItemStack()).thenReturn(new ItemStack(Material.PLAYER_HEAD));

    listener.onDropItems(new BlockDropItemEvent(block, skull, player, List.of(item)));

    verify(item, never()).setItemStack(any());
  }

  @Test
  void liquidOrPistonBreakingHeadRestoresLore() {
    storeLore();
    List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.PLAYER_HEAD)));

    listener.onBlockBreakBlock(new BlockBreakBlockEvent(block, mock(Block.class), drops));

    assertEquals(LORE, drops.getFirst().lore());
  }

  @Test
  void explosionDropsHeadWithLore() {
    storeLore();
    when(block.getDrops()).thenReturn(List.of(new ItemStack(Material.PLAYER_HEAD)));
    List<Block> blocks = new ArrayList<>(List.of(block));

    listener.onEntityExplode(
        new EntityExplodeEvent(player, location, blocks, 1.0f, ExplosionResult.DESTROY_WITH_DECAY));

    assertFalse(blocks.contains(block));
    verify(block).setType(Material.AIR);
    assertEquals(LORE, droppedItems().getFirst().lore());
  }

  @Test
  void blockExplosionDropsHeadWithLore() {
    storeLore();
    when(block.getDrops()).thenReturn(List.of(new ItemStack(Material.PLAYER_HEAD)));
    List<Block> blocks = new ArrayList<>(List.of(block));

    listener.onBlockExplode(
        new BlockExplodeEvent(block, skull, blocks, 1.0f, ExplosionResult.DESTROY));

    assertFalse(blocks.contains(block));
    assertEquals(LORE, droppedItems().getFirst().lore());
  }

  @Test
  void explosionLeavesHeadsWithNothingStoredToVanilla() {
    List<Block> blocks = new ArrayList<>(List.of(block));

    listener.onEntityExplode(
        new EntityExplodeEvent(player, location, blocks, 1.0f, ExplosionResult.DESTROY));

    assertTrue(blocks.contains(block));
    verify(block, never()).setType(any());
  }

  @Test
  void windChargeDoesNotBreakHeads() {
    storeLore();
    List<Block> blocks = new ArrayList<>(List.of(block));

    listener.onEntityExplode(
        new EntityExplodeEvent(player, location, blocks, 1.0f, ExplosionResult.TRIGGER_BLOCK));

    assertTrue(blocks.contains(block));
    verify(block, never()).setType(any());
  }

  @Test
  void explosionWithBlockDropsOffRemovesHeadWithoutDropping() {
    storeLore();
    when(world.getGameRuleValue(GameRules.BLOCK_DROPS)).thenReturn(false);
    List<Block> blocks = new ArrayList<>(List.of(block));

    listener.onEntityExplode(
        new EntityExplodeEvent(player, location, blocks, 1.0f, ExplosionResult.DESTROY));

    verify(block).setType(Material.AIR);
    nextTick.forEach(Runnable::run);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void bucketOntoHeadDropsHeadWithLore() {
    storeLore();
    when(block.getDrops()).thenReturn(List.of(new ItemStack(Material.PLAYER_HEAD)));

    PlayerBucketEmptyEvent event =
        new PlayerBucketEmptyEvent(
            player,
            block,
            mock(Block.class),
            BlockFace.UP,
            Material.WATER_BUCKET,
            new ItemStack(Material.WATER_BUCKET),
            EquipmentSlot.HAND);
    listener.onBucketEmpty(event);

    verify(block).setType(Material.AIR);
    assertEquals(LORE, droppedItems().getFirst().lore());
  }

  @Test
  void bucketOntoOtherBlocksDoesNothing() {
    Block air = mock(Block.class);
    when(air.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

    listener.onBucketEmpty(
        new PlayerBucketEmptyEvent(
            player,
            air,
            mock(Block.class),
            BlockFace.UP,
            Material.WATER_BUCKET,
            new ItemStack(Material.WATER_BUCKET),
            EquipmentSlot.HAND));

    verify(air, never()).setType(any());
    assertTrue(nextTick.isEmpty());
  }
}
