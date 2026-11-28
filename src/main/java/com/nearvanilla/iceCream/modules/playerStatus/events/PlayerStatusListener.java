package com.nearvanilla.iceCream.modules.playerStatus.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.playerStatus.PlayerStatusManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/**
 * Sends statuses when the proxy starts listening, and drops them when players leave the server or
 * go into spectator mode.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-23
 */
public class PlayerStatusListener implements Listener {

  private final PlayerStatusManager manager;

  public PlayerStatusListener(PlayerStatusManager manager) {
    this.manager = manager;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    manager.forget(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onRegisterChannel(PlayerRegisterChannelEvent event) {
    if (!PlayerStatusManager.STATUS_CHANNEL.equals(event.getChannel())) return;
    // On the main thread, once the player has joined.
    Player player = event.getPlayer();
    Bukkit.getScheduler()
        .runTask(
            IceCream.instance,
            () -> {
              if (player.isOnline()) manager.channelRegistered(player);
            });
  }

  /** Spectator mode marks the player before switching their gamemode. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
    if (event.getNewGameMode() == GameMode.SPECTATOR
        && PlayerStatusManager.isSpectating(event.getPlayer())) {
      manager.clear(event.getPlayer());
    }
  }
}
