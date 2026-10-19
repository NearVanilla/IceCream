package com.nearvanilla.iceCream.modules.deathDb.events;

import com.nearvanilla.iceCream.modules.deathDb.DeathDbModule;
import com.nearvanilla.iceCream.modules.deathDb.gui.DeathGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Makes the death GUIs strictly read-only and dispatches button clicks. While a death GUI is the
 * top inventory every click and drag is cancelled, whichever inventory it targets (this covers
 * shift clicks, number keys and double click collection). Only a plain left click on the GUI's own
 * inventory runs an action.
 */
public class DeathGuiListener implements Listener {

  @EventHandler(priority = EventPriority.HIGH)
  public void onClick(InventoryClickEvent event) {
    Inventory top = event.getView().getTopInventory();
    if (!(top.getHolder(false) instanceof DeathGui gui)) {
      return;
    }
    event.setCancelled(true);
    if (event.getClickedInventory() != top || event.getClick() != ClickType.LEFT) {
      return;
    }
    if (!(event.getWhoClicked() instanceof Player viewer)) {
      return;
    }
    if (!viewer.hasPermission(DeathDbModule.RESTORE_PERMISSION)) {
      viewer.closeInventory();
      return;
    }
    gui.onClick(viewer, event.getRawSlot());
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onDrag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder(false) instanceof DeathGui) {
      event.setCancelled(true);
    }
  }
}
