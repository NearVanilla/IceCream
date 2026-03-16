package com.nearvanilla.iceCream.modules.vanish.commands;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vanish.VanishModule;
import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * VanishCommand toggles vanish mode for staff members. When vanished, the player is hidden from
 * other players, removed from the tab list, and a fake leave message is broadcast. When
 * unvanishing, the player becomes visible again and a fake join message is broadcast.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class VanishCommand {
  protected final Component vanishEnabled =
      MiniMessage.miniMessage().deserialize("<green>You are now vanished.</green>");

  protected final Component vanishDisabled =
      MiniMessage.miniMessage().deserialize("<red>You are no longer vanished.</red>");

  /**
   * Toggles vanish mode for the player who executed the command.
   *
   * @param commandSourceStack The command source stack containing the sender.
   */
  @Command("vanish|v")
  @CommandDescription("Toggles vanish mode.")
  @Permission("icecream.modules.vanish")
  @SuppressWarnings("unused")
  public void vanishCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }

    boolean isVanished = VanishUtils.isVanished(player);

    if (isVanished) {
      disableVanish(player);
    } else {
      enableVanish(player);
    }
  }

  /**
   * Enables vanish for the given player.
   *
   * @param player the player to vanish
   */
  private void enableVanish(Player player) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    Location loc = player.getLocation();
    pdc.set(
        VanishModule.VANISH_LOCATION_WORLD, PersistentDataType.STRING, loc.getWorld().getName());
    pdc.set(VanishModule.VANISH_LOCATION_X, PersistentDataType.DOUBLE, loc.getX());
    pdc.set(VanishModule.VANISH_LOCATION_Y, PersistentDataType.DOUBLE, loc.getY());
    pdc.set(VanishModule.VANISH_LOCATION_Z, PersistentDataType.DOUBLE, loc.getZ());
    pdc.set(VanishModule.VANISH_LOCATION_YAW, PersistentDataType.FLOAT, loc.getYaw());
    pdc.set(VanishModule.VANISH_LOCATION_PITCH, PersistentDataType.FLOAT, loc.getPitch());

    VanishUtils.hidePlayer(player);

    player.sendMessage(vanishEnabled);
  }

  /**
   * Disables vanish for the given player.
   *
   * @param player the player to unvanish
   */
  private void disableVanish(Player player) {
    VanishUtils.showPlayer(player);

    PersistentDataContainer pdc = player.getPersistentDataContainer();
    String world = pdc.get(VanishModule.VANISH_LOCATION_WORLD, PersistentDataType.STRING);
    Double x = pdc.get(VanishModule.VANISH_LOCATION_X, PersistentDataType.DOUBLE);
    Double y = pdc.get(VanishModule.VANISH_LOCATION_Y, PersistentDataType.DOUBLE);
    Double z = pdc.get(VanishModule.VANISH_LOCATION_Z, PersistentDataType.DOUBLE);
    Float yaw = pdc.get(VanishModule.VANISH_LOCATION_YAW, PersistentDataType.FLOAT);
    Float pitch = pdc.get(VanishModule.VANISH_LOCATION_PITCH, PersistentDataType.FLOAT);

    if (world != null && x != null && y != null && z != null && yaw != null && pitch != null) {
      Location savedLocation =
          new Location(IceCream.instance.getServer().getWorld(world), x, y, z, yaw, pitch);
      player.teleport(savedLocation);
    }

    player.sendMessage(vanishDisabled);
  }
}
