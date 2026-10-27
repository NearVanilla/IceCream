package com.nearvanilla.iceCream.modules.antiAfk.events;

import com.nearvanilla.iceCream.modules.antiAfk.AntiAfkModule;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Feeds deliberate player input to the anti-AFK module. Passive movement (water, vehicles, pistons)
 * is never counted. Cancelled events still count, because the player still pressed something.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 * @see AntiAfkModule
 */
public class AntiAfkListener implements Listener {
  private final AntiAfkModule module;

  public AntiAfkListener(AntiAfkModule module) {
    this.module = module;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    module.startTracking(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    module.stopTracking(event.getPlayer());
  }

  // LOWEST so the answer is cancelled before chat plugins format or send it.
  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncChatEvent event) {
    if (module.shouldHideChat(event.getPlayer(), event.originalMessage())) {
      event.setCancelled(true);
      event.viewers().clear();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInput(PlayerInputEvent event) {
    module.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCommand(PlayerCommandPreprocessEvent event) {
    module.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) module.markActive(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player) module.markActive(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onItemHeld(PlayerItemHeldEvent event) {
    module.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onSwapHands(PlayerSwapHandItemsEvent event) {
    module.markActive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDrop(PlayerDropItemEvent event) {
    module.markActive(event.getPlayer());
  }

  // Only the main hand counts, otherwise the two hands' events would alternate and always look new.
  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() == Action.PHYSICAL || event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    String target = block == null ? "-" : block.getX() + "," + block.getY() + "," + block.getZ();
    String item = event.getItem() == null ? "-" : event.getItem().getType().name();
    module.markInteraction(event.getPlayer(), event.getAction() + "|" + target + "|" + item);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    Player player = event.getPlayer();
    module.markInteraction(
        player,
        "ENTITY|"
            + event.getRightClicked().getType()
            + "|"
            + player.getInventory().getItemInMainHand().getType());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    module.markActive(event.getPlayer());
    module.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onTeleport(PlayerTeleportEvent event) {
    module.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    module.resetView(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMount(EntityMountEvent event) {
    if (event.getEntity() instanceof Player player) module.resetView(player);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDismount(EntityDismountEvent event) {
    if (event.getEntity() instanceof Player player) module.resetView(player);
  }
}
