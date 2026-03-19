package com.nearvanilla.iceCream.modules.spectator.commands;

import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * SpectatorCommand toggles spectator mode for staff members. When in spectator mode, the player is
 * set to SPECTATOR gamemode, hidden from other players, and a fake leave message is broadcast. When
 * exiting, the player's previous gamemode is restored and a fake join message is broadcast.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-03-18
 */
public class SpectatorCommand {
  protected final Component spectatorEnabled =
      MiniMessage.miniMessage().deserialize("<aqua>You are now in spectator mode.</aqua>");

  protected final Component spectatorDisabled =
      MiniMessage.miniMessage().deserialize("<red>You are no longer in spectator mode.</red>");

  /**
   * Toggles spectator mode for the player who executed the command.
   *
   * @param commandSourceStack The command source stack containing the sender.
   */
  @Command("spectator|spec|s")
  @CommandDescription("Toggles spectator mode.")
  @Permission("icecream.modules.spectator")
  @SuppressWarnings("unused")
  public void spectatorCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }

    boolean isInSpectatorMode = SpectatorUtils.isInSpectatorMode(player);

    if (isInSpectatorMode) {
      disableSpectator(player);
    } else {
      enableSpectator(player);
    }
  }

  /**
   * Enables spectator mode for the given player.
   *
   * @param player the player to put in spectator mode
   */
  private void enableSpectator(Player player) {
    SpectatorUtils.enableSpectator(player);

    player.sendMessage(spectatorEnabled);
  }

  /**
   * Disables spectator mode for the given player.
   *
   * @param player the player to remove from spectator mode
   */
  private void disableSpectator(Player player) {
    SpectatorUtils.disableSpectator(player);

    player.sendMessage(spectatorDisabled);
  }
}
