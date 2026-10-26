package com.nearvanilla.iceCream.modules.staffMode.events;

import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Finishes turning staff mode off for a player who quits before their teleport back lands, so they
 * are not saved with staff mode's invulnerability and flight.
 *
 * @version 1.0
 * @since 2026-10-26
 */
public class StaffModePlayerQuitEvent implements Listener {

  /**
   * Takes away the staff abilities of a player still on their way back from staff mode.
   *
   * @param event the player quit event
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (StaffModeUtils.finishReturning(player)) StaffModeUtils.dropStaffAbilities(player);
  }
}
