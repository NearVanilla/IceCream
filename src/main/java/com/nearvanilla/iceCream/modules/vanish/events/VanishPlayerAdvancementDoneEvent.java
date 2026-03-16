package com.nearvanilla.iceCream.modules.vanish.events;

import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Suppresses advancement broadcast messages for vanished players.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-03-16
 */
public class VanishPlayerAdvancementDoneEvent implements Listener {

  /**
   * Cancels the advancement announcement if the player is vanished.
   *
   * @param event the player advancement done event
   */
  @EventHandler
  public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
    Player player = event.getPlayer();

    if (VanishUtils.isVanished(player)) {
      event.message(null);
    }
  }
}
