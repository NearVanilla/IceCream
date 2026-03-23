package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import com.nearvanilla.iceCream.utils.FakeMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events for spectator mode. Hides all spectating players from the joining
 * player, and if the joining player is in spectator mode, hides them from others and suppresses the
 * join message.
 *
 * @author 105hua
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
      // Re-send vanished=true after 1 tick to ensure the backend→proxy plugin messaging
      // channel is fully negotiated. The call inside enableSpectator fires immediately and
      // may be dropped if the channel isn't open yet during PlayerJoinEvent.
      Bukkit.getScheduler()
          .runTask(
              IceCream.instance,
              () -> {
                if (player.isOnline() && SpectatorUtils.isInSpectatorMode(player)) {
                  FakeMessageUtils.sendStateToVelocity(
                      player, SpectatorUtils.SPECTATOR_CHANNEL, true);
                }
              });
      player.sendMessage(spectatorReminderComponent);
    } else {
      // Explicitly clear any stale spectator state in Velocity. This handles the edge case
      // where the player was in spectator mode in a previous session but their PDC flag was
      // cleared while offline, leaving their UUID stuck in bat's vanishedPlayers set.
      FakeMessageUtils.sendStateToVelocity(player, SpectatorUtils.SPECTATOR_CHANNEL, false);
    }
  }
}
