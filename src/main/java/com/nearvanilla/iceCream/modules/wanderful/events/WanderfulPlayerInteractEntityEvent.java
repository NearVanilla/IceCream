package com.nearvanilla.iceCream.modules.wanderful.events;

import com.nearvanilla.iceCream.modules.wanderful.WandType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * WanderfulPlayerInteractEntityEvent is an event listener that listens for
 * "PlayerInteractEntityEvent" and toggles the visibility of item frames when a player interacts
 * with them using the item frame wand.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public class WanderfulPlayerInteractEntityEvent implements Listener {
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof ItemFrame itemFrame)) return; // Not an item frame
    if (itemFrame.getItem().getType() == Material.AIR) return; // Item frame is empty
    if (event.getHand() != EquipmentSlot.HAND) return; // We only care about main hand

    ItemStack item = event.getPlayer().getEquipment().getItemInMainHand();
    if (item.getType() == Material.AIR) return; // Player is not holding anything

    // Make sure the item is the item frame wand
    NamespacedKey wandKey = WandType.ITEM_FRAME.getMainPersistentKey();
    if (!item.getItemMeta().getPersistentDataContainer().has(wandKey)) {
      return;
    }

    // Toggle visibility
    itemFrame.setVisible(!itemFrame.isVisible());
    event.setCancelled(true);
  }
}
