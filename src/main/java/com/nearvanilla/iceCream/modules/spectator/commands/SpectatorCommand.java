package com.nearvanilla.iceCream.modules.spectator.commands;

import com.nearvanilla.iceCream.modules.spectator.AutoSpectator;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

  protected final Component spectatorBlockedByStaffMode =
      MiniMessage.miniMessage()
          .deserialize("<red>You cannot enter spectator mode while in staff mode.</red>");

  protected final Component autoSpectatorDisabled =
      MiniMessage.miniMessage().deserialize("<red>Auto spectator is off.</red>");

  protected final Component autoSpectatorFeatureOff =
      MiniMessage.miniMessage()
          .deserialize(
              "<gray>Auto spectator is turned off on this server, so this setting has no effect"
                  + " for now.</gray>");

  /**
   * Builds the message confirming that auto spectator is on, including the configured offline time.
   *
   * @return the confirmation component
   */
  protected Component autoSpectatorEnabledMessage() {
    return MiniMessage.miniMessage()
        .deserialize(
            "<aqua>Auto spectator is on. You will join in spectator mode after <minutes> minutes"
                + " away.</aqua>",
            Placeholder.unparsed(
                "minutes", String.valueOf(AutoSpectator.getOfflineMillis() / 60_000L)));
  }

  /**
   * Toggles auto spectator for the player who executed the command. When it is on, the player joins
   * in spectator mode after being offline for the configured time.
   *
   * @param commandSourceStack The command source stack containing the sender.
   */
  @Command("spectator|spec|s auto")
  @CommandDescription("Toggles joining in spectator mode after time away.")
  @Permission("icecream.modules.spectator")
  @SuppressWarnings("unused")
  public void autoCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }

    boolean enabled = AutoSpectator.toggleAuto(player);
    player.sendMessage(enabled ? autoSpectatorEnabledMessage() : autoSpectatorDisabled);

    if (!AutoSpectator.isFeatureEnabled()) {
      player.sendMessage(autoSpectatorFeatureOff);
    }
  }

  /**
   * Toggles spectator mode for the player who executed the command. Entering spectator mode is
   * refused while the player is in staff mode, because staff mode has cleared their inventory and
   * the two saved states are easy to mistake for item loss.
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

    if (SpectatorUtils.isInSpectatorMode(player)) {
      // Leaving is always allowed. /staffmode is blocked while spectating, so a player who is
      // already in both modes would otherwise have no way out.
      disableSpectator(player);
      return;
    }

    if (StaffModeUtils.isInStaffMode(player)) {
      player.sendMessage(spectatorBlockedByStaffMode);
      return;
    }

    enableSpectator(player);
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
