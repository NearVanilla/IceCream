package com.nearvanilla.iceCream.modules.flight.events;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.flight.FlightUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class FlightListener implements Listener {
  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    restoreNextTick(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerPostRespawnEvent event) {
    FlightUtils.restoreFlight(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
    // Minecraft applies the new game mode's abilities after this event returns.
    restoreNextTick(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    restoreNextTick(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    // Do not save survival flight abilities that could outlive the module being disabled.
    FlightUtils.suspendFlight(event.getPlayer());
  }

  private void restoreNextTick(Player player) {
    IceCream.instance
        .getServer()
        .getScheduler()
        .runTask(
            IceCream.instance,
            () -> {
              if (player.isOnline() && !player.isDead()) {
                FlightUtils.restoreFlight(player);
              }
            });
  }
}
