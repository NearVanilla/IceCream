package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player quit events for spectator mode. If the player is in spectator mode, suppresses the
 * quit message.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-03-18
 */
public class SpectatorPlayerQuitEvent implements Listener {

  /**
   * Handles player quit events.
   *
   * @param event the player quit event
   */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    // Suppress quit message if player is in spectator mode
    if (SpectatorUtils.isInSpectatorMode(player)) {
      event.quitMessage(null);
    }
  }
}
