package com.nearvanilla.iceCream.modules.worldTour.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * WorldTourListener implements the disconnect failsafe for World Tour participants. When a
 * non-host participant disconnects and does not return within the configured timeout, they are
 * automatically opted out. Host disconnects do not automatically end the tour.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-06-26
 */
public class WorldTourListener implements Listener {

  private final WorldTourModule module;

  public WorldTourListener(WorldTourModule module) {
    this.module = module;
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    // Clear glow tracking if this player was the glowing one.
    if (player.equals(module.getCurrentGlowing())) {
      module.clearGlowing();
    }

    // Host disconnects do not automatically end the tour. Also skip the normal participant
    // auto-opt-out path, because the host is marked as joined while a tour is active.
    if (player.equals(module.getCurrentHost())) {
      return;
    }

    long timeoutTicks = module.getFailsafeTimeoutMinutes() * 60L * 20L;

    // If the player is a participant, schedule auto-opt-out.
    if (Boolean.TRUE.equals(
        player
            .getPersistentDataContainer()
            .get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN))) {
      var task =
          IceCream.instance
              .getServer()
              .getScheduler()
              .runTaskLater(
                  IceCream.instance,
                  () -> {
                    module.cancelDisconnectTask(player.getUniqueId());
                    if (!player.isOnline()) {
                      player
                          .getPersistentDataContainer()
                          .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, false);
                      player.saveData();
                    }
                  },
                  timeoutTicks);
      module.scheduleDisconnectTask(player.getUniqueId(), task);
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    module.cancelDisconnectTask(player.getUniqueId());

    // Clean up stale join flag if no tour is active.
    if (module.getCurrentHost() == null
        && Boolean.TRUE.equals(
            player
                .getPersistentDataContainer()
                .get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN))) {
      player
          .getPersistentDataContainer()
          .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, false);
    }
  }
}
