package com.nearvanilla.iceCream.modules.staffMode.commands;

import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_CONFIRM_KEY;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_INVENTORY_KEY;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_KEY;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_TOGGLE_KEY;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * StaffMode is a command that adds a staff mode. It saves the location and inventory of the player,
 * clears their inventory and puts the player into god mode and fly mode. When the command is run
 * again, it restores the players inventory and location, and takes them out of god mode and fly
 * mode.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-23
 */
public class StaffModeCommand {
  protected final Component staffModeEnabled =
      MiniMessage.miniMessage().deserialize("<green>Staff mode has been enabled.</green>");

  protected final Component staffModeDisabled =
      MiniMessage.miniMessage().deserialize("<red>Staff mode has been disabled.</red>");

  /**
   * The logic for the staff mode command, toggling staff mode for the player who executed it and
   * sending them a message.
   *
   * @param commandSourceStack The command source stack, containing a sender, executor and location,
   *     where applicable.
   */
  @Command("staffmode|staff|sm")
  @CommandDescription("Toggles staff mode.")
  @Permission("icecream.modules.staffmode.staffmode")
  @SuppressWarnings("unused")
  public void staffModeCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }
    PersistentDataContainer pdc = player.getPersistentDataContainer();

    boolean isStaffModeEnabled =
        Boolean.TRUE.equals(pdc.get(STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN));

    if (!pdc.has(STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN) || !isStaffModeEnabled) {
      enableStaffMode(player, pdc);
    } else {
      disableStaffMode(player, pdc);
    }
  }

  /**
   * Enables staff mode for the given player by saving their current inventory and location,
   * clearing their inventory, and setting them to invulnerable and allowing flight.
   *
   * @param player the player to enable staff mode for
   * @param pdc the player's PersistentDataContainer
   */
  private void enableStaffMode(Player player, PersistentDataContainer pdc) {
    pdc.set(
        STAFF_MODE_INVENTORY_KEY,
        PersistentDataType.BYTE_ARRAY,
        StaffModeUtils.serializeInventory(player.getInventory().getContents()));

    pdc.set(
        STAFF_MODE_LOCATION_KEY,
        PersistentDataType.BYTE_ARRAY,
        StaffModeUtils.serializeLocation(player));

    player.getInventory().clear();
    player.setInvulnerable(true);
    player.setAllowFlight(true);
    player.setFlying(true);

    pdc.set(STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    player.sendMessage(staffModeEnabled);
  }

  /**
   * Disables staff mode for the player by restoring their saved inventory and location, clearing
   * their current inventory, and setting them to vulnerable and disabling flight.
   *
   * @param player the player to disable staff mode for
   * @param pdc the player's PersistentDataContainer
   */
  private void disableStaffMode(Player player, PersistentDataContainer pdc) {
    boolean hasItems = false;
    for (ItemStack item : player.getInventory().getContents()) {
      if (item != null && !item.getType().isAir()) {
        hasItems = true;
        break;
      }
    }

    if (hasItems
        && !Boolean.TRUE.equals(pdc.get(STAFF_MODE_CONFIRM_KEY, PersistentDataType.BOOLEAN))) {
      pdc.set(STAFF_MODE_CONFIRM_KEY, PersistentDataType.BOOLEAN, true);
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<yellow>Are you sure you want to disable staff mode? Run the command again to confirm.</yellow>"));
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red>This will clear your inventory before restoring your previous inventory, "
                      + "if you have picked up or taken any items, please return the items to avoid losing them</red>"));
      return;
    }
    // Remove confirmation flag
    pdc.remove(STAFF_MODE_CONFIRM_KEY);

    player.getInventory().clear();
    byte[] serializedInventory = pdc.get(STAFF_MODE_INVENTORY_KEY, PersistentDataType.BYTE_ARRAY);
    if (serializedInventory != null) {
      ItemStack[] deserializedInventory =
          StaffModeUtils.deserializeInventory(player, STAFF_MODE_INVENTORY_KEY);
      player.getInventory().setContents(deserializedInventory);
    }

    player.setInvulnerable(false);
    player.setFlying(false);
    player.setAllowFlight(false);

    byte[] serializedLocation = pdc.get(STAFF_MODE_LOCATION_KEY, PersistentDataType.BYTE_ARRAY);
    if (serializedLocation != null) {
      player.teleport(
          StaffModeUtils.deserializeLocation(serializedLocation, IceCream.instance.getServer()));
    }

    pdc.remove(STAFF_MODE_LOCATION_KEY);
    pdc.remove(STAFF_MODE_INVENTORY_KEY);
    pdc.set(STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, false);

    player.sendMessage(staffModeDisabled);
  }
}
