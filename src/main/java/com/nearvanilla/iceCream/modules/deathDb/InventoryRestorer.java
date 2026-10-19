package com.nearvanilla.iceCream.modules.deathDb;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Applies a deserialized snapshot to an online player. */
public final class InventoryRestorer {
  private InventoryRestorer() {}

  /**
   * Replaces the target's storage, armor and offhand with the snapshot. What they were carrying is
   * dropped at their feet. Must run on the server thread with a fully deserialized snapshot.
   */
  public static void restore(Player target, ItemStack[] snapshot) {
    PlayerInventory inventory = target.getInventory();
    ItemStack[] previous = InventorySnapshot.capture(inventory);
    InventorySnapshot.apply(inventory, snapshot);
    for (ItemStack item : previous) {
      if (!InventorySnapshot.isEmpty(item)) {
        target.getWorld().dropItem(target.getLocation(), item);
      }
    }
    target.updateInventory();
  }
}
