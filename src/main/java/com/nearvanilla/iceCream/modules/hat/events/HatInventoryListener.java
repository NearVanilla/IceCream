package com.nearvanilla.iceCream.modules.hat.events;

import com.nearvanilla.iceCream.modules.hat.commands.HatCommand;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class HatInventoryListener implements Listener {
  private static final int HELMET_SLOT = 39;
  private static final Component SET_MESSAGE =
      MiniMessage.miniMessage().deserialize("<green>[Hat] Item set as hat.</green>");
  private static final Component NO_PERMISSION_MESSAGE =
      MiniMessage.miniMessage()
          .deserialize("<red>[Hat] You do not have permission to use hats.</red>");

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)
        || event.getView().getType() != InventoryType.CRAFTING
        || event.getSlotType() != InventoryType.SlotType.ARMOR
        || event.getSlot() != HELMET_SLOT) {
      return;
    }

    PlayerInventory inventory = player.getInventory();
    boolean numberKey = event.getClick() == ClickType.NUMBER_KEY;
    ItemStack item =
        numberKey ? inventory.getItem(event.getHotbarButton()) : event.getCursor();
    if (item == null || item.isEmpty()) {
      return;
    }

    Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
    if (equippable != null && equippable.slot() == EquipmentSlot.HEAD) {
      return;
    }

    event.setCancelled(true);
    if (!player.hasPermission(HatCommand.PERMISSION)) {
      player.sendMessage(NO_PERMISSION_MESSAGE);
      return;
    }

    ItemStack helmet = inventory.getItem(EquipmentSlot.HEAD);
    if (numberKey) {
      inventory.setItem(event.getHotbarButton(), helmet.clone());
    } else {
      event.getView().setCursor(helmet.clone());
    }
    inventory.setItem(EquipmentSlot.HEAD, item.clone());
    player.sendMessage(SET_MESSAGE);
  }
}
