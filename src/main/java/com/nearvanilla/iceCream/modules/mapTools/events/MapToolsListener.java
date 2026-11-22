package com.nearvanilla.iceCream.modules.mapTools.events;

import com.nearvanilla.iceCream.modules.mapTools.IconMenu;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Adds the areas layer to worlds loaded after startup, handles clicks in the marker icon menu, and
 * forgets how many commands a player sent over the mod channel when they leave.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class MapToolsListener implements Listener {

  /** Runs at MONITOR so squaremap has registered its own map world for the new world first. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldLoad(WorldLoadEvent event) {
    MapToolsUtils.registerWorld(event.getWorld());
  }

  /** Clicks in the icon menu pick an icon and never move items. */
  @EventHandler(priority = EventPriority.LOW)
  public void onIconMenuClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder(false) instanceof IconMenu menu)) return;
    event.setCancelled(true);
    if (event.getWhoClicked() instanceof Player player
        && event.getClickedInventory() == event.getInventory()) {
      menu.click(player, event.getSlot());
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onIconMenuDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder(false) instanceof IconMenu) event.setCancelled(true);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    MapToolsChannel.forget(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onIconMenuClose(InventoryCloseEvent event) {
    if (event.getInventory().getHolder(false) instanceof IconMenu menu
        && event.getPlayer() instanceof Player player) {
      menu.closed(player);
    }
  }
}
