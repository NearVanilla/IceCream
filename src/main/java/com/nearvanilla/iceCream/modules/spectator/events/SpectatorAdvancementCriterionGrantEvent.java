package com.nearvanilla.iceCream.modules.spectator.events;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Prevents players in spectator mode from earning visible advancements. Hidden technical
 * advancements used by datapacks are left alone.
 *
 * @author 105hua
 * @author Dynant
 * @version 1.1
 * @since 2026-03-18
 */
public class SpectatorAdvancementCriterionGrantEvent implements Listener {

  /**
   * Cancels criterion grants for displayed advancements while the player is in spectator mode.
   *
   * @param event the advancement criterion grant event
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAdvancementCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
    if (event.getAdvancement().getDisplay() != null
        && SpectatorUtils.isInSpectatorMode(event.getPlayer())) {
      event.setCancelled(true);
    }
  }
}
