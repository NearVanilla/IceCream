package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events for spectator mode. Hides all spectating players from the joining
 * player, and if the joining player is in spectator mode, hides them from others and suppresses the
 * join message.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-03-18
 */
public class SpectatorPlayerJoinEvent implements Listener {

  protected final Component spectatorReminderComponent =
      MiniMessage.miniMessage()
          .deserialize(
              "<aqua>\uD83D\uDC41 <yellow>You are currently in spectator mode. </yellow>"
                  + "<aqua>[<click:run_command:'/spec'>"
                  + "<hover:show_text:'Click to toggle spectator mode.'>"
                  + "Toggle"
                  + "</hover></click>]</aqua>");

  /**
   * Handles player join events.
   *
   * @param event the player join event
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    // Hide all spectating players from this player
    SpectatorUtils.hideSpectatingPlayersFrom(player);

    // If the joining player is in spectator mode, hide them from others and suppress join message
    if (SpectatorUtils.isInSpectatorMode(player)) {
      event.joinMessage(null);
      SpectatorUtils.enableSpectator(player, false);
      player.sendMessage(spectatorReminderComponent);
    }
  }
}
