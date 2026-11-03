package com.nearvanilla.iceCream.modules.staffMode.events;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Prevents players in staff mode from earning visible advancements. Hidden technical advancements
 * used by datapacks are left alone.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-03
 */
public class StaffModeAdvancementCriterionGrantEvent implements Listener {

  /**
   * Cancels criterion grants for displayed advancements while the player is in staff mode.
   *
   * @param event the advancement criterion grant event
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAdvancementCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
    if (event.getAdvancement().getDisplay() != null
        && StaffModeUtils.isInStaffMode(event.getPlayer())) {
      event.setCancelled(true);
    }
  }
}
