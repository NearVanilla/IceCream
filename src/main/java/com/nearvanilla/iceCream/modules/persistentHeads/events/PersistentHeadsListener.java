package com.nearvanilla.iceCream.modules.persistentHeads.events;

import com.nearvanilla.iceCream.modules.persistentHeads.HeadStore;
import com.nearvanilla.iceCream.modules.persistentHeads.StoredHead;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.other.persistentheads.BlockListener
 * Adaptations: only lore is stored, since vanilla keeps custom names; liquid flow and pistons
 *   edit Paper's BlockBreakBlockEvent drops instead of replacing the block; explosions only break
 *   heads that have stored data, skip wind charges, always drop the head as its loot table does,
 *   and respect the blockDrops game rule.
 */
/**
 * Keeps the lore of player heads when they are placed and broken again. Vanilla only keeps the skin
 * and custom name, so lore such as the "Killed by" line from Player Head Drops would be lost.
 *
 * <p>The lore is stored on the placed head and put back on the dropped item for every way a head
 * can break: by a player, by liquid or a piston, by an explosion, or by emptying a bucket onto it.
 * The last two have no event with editable drops, so the head is broken and dropped here instead.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 */
public class PersistentHeadsListener implements Listener {
  private final HeadStore store;
  private final Consumer<Runnable> nextTick;

  /**
   * Creates the listener.
   *
   * @param store where head lore is stored
   * @param nextTick runs a task on the next server tick
   */
  public PersistentHeadsListener(HeadStore store, Consumer<Runnable> nextTick) {
    this.store = store;
    this.nextTick = nextTick;
  }

  /** Stores the lore of a placed player head. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    ItemStack item = event.getItemInHand();
    if (item.getType() != Material.PLAYER_HEAD) return;
    List<Component> lore = item.lore();
    if (lore == null || lore.isEmpty()) return;
    if (!(event.getBlockPlaced().getState() instanceof Skull skull)) return;
    store.save(skull.getPersistentDataContainer(), lore);
    skull.update();
  }

  /** Restores lore when a player breaks a head. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDropItems(BlockDropItemEvent event) {
    StoredHead head = stored(event.getBlockState());
    if (head.isEmpty()) return;
    for (Item item : event.getItems()) {
      ItemStack stack = item.getItemStack();
      if (stack.getType() != Material.PLAYER_HEAD) continue;
      head.applyTo(stack);
      item.setItemStack(stack);
    }
  }

  /** Restores lore when liquid or a piston breaks a head. */
  @EventHandler(priority = EventPriority.HIGH)
  public void onBlockBreakBlock(BlockBreakBlockEvent event) {
    StoredHead head = stored(event.getBlock().getState());
    if (head.isEmpty()) return;
    event.getDrops().forEach(head::applyTo);
  }

  /** Breaks a head before a bucket's liquid destroys it and its lore. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    Block block = event.getBlock();
    StoredHead head = stored(block.getState());
    if (!head.isEmpty()) breakAndDrop(block, head);
  }

  /** Breaks heads with stored lore before an exploding block destroys them. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockExplode(BlockExplodeEvent event) {
    handleExplosion(event.blockList(), event.getExplosionResult());
  }

  /** Breaks heads with stored lore before an exploding entity destroys them. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityExplode(EntityExplodeEvent event) {
    handleExplosion(event.blockList(), event.getExplosionResult());
  }

  private void handleExplosion(List<Block> blocks, ExplosionResult result) {
    // KEEP leaves blocks alone and TRIGGER_BLOCK (wind charges) only presses buttons and the like.
    if (result != ExplosionResult.DESTROY && result != ExplosionResult.DESTROY_WITH_DECAY) return;
    Iterator<Block> iterator = blocks.iterator();
    while (iterator.hasNext()) {
      Block block = iterator.next();
      StoredHead head = stored(block.getState());
      if (head.isEmpty()) continue;
      // The head's loot table has no explosion condition, so it always drops, as in vanilla.
      breakAndDrop(block, head);
      iterator.remove();
    }
  }

  private void breakAndDrop(Block block, StoredHead head) {
    World world = block.getWorld();
    Location location = block.getLocation();
    List<ItemStack> drops = new ArrayList<>();
    if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRules.BLOCK_DROPS))) {
      for (ItemStack drop : block.getDrops()) {
        head.applyTo(drop);
        drops.add(drop);
      }
    }
    block.setType(Material.AIR);
    if (drops.isEmpty()) return;
    // Dropped a tick later, as PaperTweaks did, so the explosion does not destroy the item.
    nextTick.accept(() -> drops.forEach(drop -> world.dropItemNaturally(location, drop)));
  }

  private StoredHead stored(BlockState state) {
    if (!(state instanceof Skull skull) || !isPlayerHead(state.getType())) {
      return StoredHead.EMPTY;
    }
    return store.read(skull.getPersistentDataContainer());
  }

  private static boolean isPlayerHead(Material type) {
    return type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD;
  }
}
