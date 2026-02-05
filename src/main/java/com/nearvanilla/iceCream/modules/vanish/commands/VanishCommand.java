package com.nearvanilla.iceCream.modules.vanish.commands;

import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
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

    player.sendMessage(vanishDisabled);
  }
}
