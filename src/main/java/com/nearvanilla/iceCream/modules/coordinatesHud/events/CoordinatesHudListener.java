package com.nearvanilla.iceCream.modules.coordinatesHud.events;

import com.nearvanilla.iceCream.modules.coordinatesHud.CoordinatesHud;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Starts showing the Coordinates HUD to players who want it when they join, and stops tracking them
 * when they leave.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see CoordinatesHud
 */
public class CoordinatesHudListener implements Listener {
  private final CoordinatesHud hud;

  /**
   * Creates the listener.
   *
   * @param hud the HUD to update
   */
  public CoordinatesHudListener(CoordinatesHud hud) {
    this.hud = hud;
  }

  /** Shows the HUD to a joining player if they want it. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    hud.load(event.getPlayer());
  }

  /** Stops tracking a player who left. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    hud.forget(event.getPlayer());
  }
}
