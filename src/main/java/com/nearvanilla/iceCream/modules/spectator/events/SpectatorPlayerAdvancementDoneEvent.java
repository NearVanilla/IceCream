package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Suppresses advancement broadcast messages for players in spectator mode.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-03-18
 */
public class SpectatorPlayerAdvancementDoneEvent implements Listener {

  /**
   * Cancels the advancement announcement if the player is in spectator mode.
   *
   * @param event the player advancement done event
   */
  @EventHandler
  public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
    Player player = event.getPlayer();

    if (SpectatorUtils.isInSpectatorMode(player)) {
      event.message(null);
    }
  }
}
