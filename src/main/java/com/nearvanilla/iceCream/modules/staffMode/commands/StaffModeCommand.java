package com.nearvanilla.iceCream.modules.staffMode.commands;

import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_CONFIRM_KEY;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_INVENTORY_KEY;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_PITCH;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_WORLD;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_X;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_Y;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_YAW;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_LOCATION_Z;
import static com.nearvanilla.iceCream.modules.staffMode.StaffModeModule.STAFF_MODE_TOGGLE_KEY;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
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
 * again, it restores the player's inventory and location, and takes them out of god mode and fly
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
      enableStaffMode(player);
    } else {
      disableStaffMode(player);
    }
  }

  /**
   * Enables staff mode for the given player by saving their current inventory and location,
   * clearing their inventory, and setting them to invulnerable and allowing flight.
   *
   * @param player the player to enable staff mode for
   */
  private void enableStaffMode(Player player) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    pdc.set( // Set serialized inventory.
        STAFF_MODE_INVENTORY_KEY,
        PersistentDataType.BYTE_ARRAY,
        StaffModeUtils.serializeInventory(player.getInventory().getContents()));

    Location playerLocation = player.getLocation();
    pdc.set(
        STAFF_MODE_LOCATION_WORLD, PersistentDataType.STRING, playerLocation.getWorld().getName());
    pdc.set(STAFF_MODE_LOCATION_X, PersistentDataType.DOUBLE, playerLocation.getX());
    pdc.set(STAFF_MODE_LOCATION_Y, PersistentDataType.DOUBLE, playerLocation.getY());
    pdc.set(STAFF_MODE_LOCATION_Z, PersistentDataType.DOUBLE, playerLocation.getZ());
    pdc.set(STAFF_MODE_LOCATION_YAW, PersistentDataType.FLOAT, playerLocation.getYaw());
    pdc.set(STAFF_MODE_LOCATION_PITCH, PersistentDataType.FLOAT, playerLocation.getPitch());

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
   */
  private void disableStaffMode(Player player) {
    boolean hasItems = false;
    PersistentDataContainer pdc = player.getPersistentDataContainer();

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

    String world = pdc.get(STAFF_MODE_LOCATION_WORLD, PersistentDataType.STRING);
    Double x = pdc.get(STAFF_MODE_LOCATION_X, PersistentDataType.DOUBLE);
    Double y = pdc.get(STAFF_MODE_LOCATION_Y, PersistentDataType.DOUBLE);
    Double z = pdc.get(STAFF_MODE_LOCATION_Z, PersistentDataType.DOUBLE);
    Float yaw = pdc.get(STAFF_MODE_LOCATION_YAW, PersistentDataType.FLOAT);
    Float pitch = pdc.get(STAFF_MODE_LOCATION_PITCH, PersistentDataType.FLOAT);

    if (world != null && x != null && y != null && z != null && yaw != null && pitch != null) {
      Location savedLocation =
          new Location(IceCream.instance.getServer().getWorld(world), x, y, z, yaw, pitch);
      player.teleport(savedLocation);
    }

    pdc.remove(STAFF_MODE_INVENTORY_KEY);
    pdc.set(STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, false);

    player.sendMessage(staffModeDisabled);
  }
}
