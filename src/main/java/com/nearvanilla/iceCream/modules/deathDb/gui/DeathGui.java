package com.nearvanilla.iceCream.modules.deathDb.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder for the death browsing GUIs; the listener cancels all interaction with them. */
public interface DeathGui extends InventoryHolder {
  /**
   * Called for a plain left click on a slot of this GUI's own inventory.
   *
   * @param viewer the clicking player
   * @param rawSlot the clicked slot index in the GUI inventory
   */
  void onClick(Player viewer, int rawSlot);
}
