package com.nearvanilla.iceCream.modules.hat.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

@SuppressWarnings("unused")
public final class HatCommand {
  public static final String PERMISSION = "icecream.modules.hat";

  private static final Component SET_MESSAGE =
      MiniMessage.miniMessage().deserialize("<green>[Hat] Item set as hat.</green>");
  private static final Component NO_PERMISSION_MESSAGE =
      MiniMessage.miniMessage()
          .deserialize("<red>[Hat] You do not have permission to use hats.</red>");
  private static final Component PLAYERS_ONLY =
      MiniMessage.miniMessage()
          .deserialize("<red>[Hat] This command can only be used by players.</red>");

  @Command("hat")
  @CommandDescription("Swap the item in your main hand with your helmet slot.")
  @Permission(PERMISSION)
  public void hat(CommandSourceStack source) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }

    PlayerInventory inventory = player.getInventory();
    ItemStack held = inventory.getItemInMainHand();
    if (!held.isEmpty() && !player.hasPermission(PERMISSION)) {
      player.sendMessage(NO_PERMISSION_MESSAGE);
      return;
    }

    ItemStack helmet = inventory.getItem(EquipmentSlot.HEAD);
    inventory.setItemInMainHand(helmet.clone());
    inventory.setItem(EquipmentSlot.HEAD, held.clone());
    player.sendMessage(SET_MESSAGE);
  }

}
