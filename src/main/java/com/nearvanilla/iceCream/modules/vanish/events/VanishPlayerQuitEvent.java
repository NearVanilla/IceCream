package com.nearvanilla.iceCream.modules.vanish.events;

import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player quit events for vanish. If the player is vanished, suppresses the quit message.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class VanishPlayerQuitEvent implements Listener {

  /**
   * Handles player quit events.
   *
   * @param event the player quit event
   */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    // Suppress quit message if player is vanished
    if (VanishUtils.isVanished(player)) {
      event.quitMessage(null);
    }
  }
}
