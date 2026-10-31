package com.nearvanilla.iceCream.modules.antiAfk.events;

import com.nearvanilla.iceCream.modules.antiAfk.AntiAfkManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Feeds deliberate player input to the anti-AFK manager. Passive movement (water, vehicles,
 * pistons) is never counted. Cancelled events still count, because the player still pressed
 * something.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 * @see AntiAfkManager
 */
public class AntiAfkListener implements Listener {
  // Clicks on air use 0, 0, 0, since the action already tells them apart. Item is null for an
  // empty hand.
  private record BlockInteraction(Action action, int x, int y, int z, Material item) {}

  private record EntityInteraction(EntityType entity, Material item) {}

  private final AntiAfkManager manager;

  public AntiAfkListener(AntiAfkManager manager) {
    this.manager = manager;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    manager.startTracking(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    manager.stopTracking(event.getPlayer());
  }

  // LOWEST so the answer is cancelled before chat plugins format or send it.
  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncChatEvent event) {
    if (manager.shouldHideChat(event.getPlayer(), event.originalMessage())) {
      event.setCancelled(true);
      event.viewers().clear();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInput(PlayerInputEvent event) {
    manager.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCommand(PlayerCommandPreprocessEvent event) {
    manager.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) manager.markActive(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player) manager.markActive(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onItemHeld(PlayerItemHeldEvent event) {
    manager.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onSwapHands(PlayerSwapHandItemsEvent event) {
    manager.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDrop(PlayerDropItemEvent event) {
    manager.markActive(event.getPlayer());
  }

  // Only the main hand counts, otherwise the two hands' events would alternate and always look new.
  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() == Action.PHYSICAL || event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    ItemStack item = event.getItem();
    manager.markInteraction(
        event.getPlayer(),
        new BlockInteraction(
            event.getAction(),
            block == null ? 0 : block.getX(),
            block == null ? 0 : block.getY(),
            block == null ? 0 : block.getZ(),
            item == null ? null : item.getType()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    Player player = event.getPlayer();
    manager.markInteraction(
        player,
        new EntityInteraction(
            event.getRightClicked().getType(),
            player.getInventory().getItemInMainHand().getType()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onKick(PlayerKickEvent event) {
    manager.kickConfirmed(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    manager.markActive(event.getPlayer());
    manager.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onTeleport(PlayerTeleportEvent event) {
    manager.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    manager.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMount(EntityMountEvent event) {
    if (event.getEntity() instanceof Player player) manager.resetView(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDismount(EntityDismountEvent event) {
    if (event.getEntity() instanceof Player player) manager.resetView(player);
  }
}
