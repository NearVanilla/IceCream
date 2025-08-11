package com.nearvanilla.iceCream.modules.muteDeaths.events;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * MuteDeathsPlayerDeathEvent is a simple event listener that listens for player deaths and sets the
 * death message to null if the player has muted their death messages.
 *
 * @author 105hua
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-11
 */
public class MuteDeathsPlayerDeathEvent implements Listener {

  /**
   * Handles the PlayerDeathEvent. When a player dies, this method is called, muting the death
   * message if the player has muted their death messages.
   *
   * @param event The PlayerDeathEvent that is triggered when a player dies.
   * @see PlayerDeathEvent
   */
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getPlayer();
    PersistentDataContainer playerContainer = player.getPersistentDataContainer();

    if (Boolean.TRUE.equals(
        playerContainer.get(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN))) {
      event.deathMessage(null);
    }
  }
}
