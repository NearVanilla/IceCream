package com.nearvanilla.iceCream.modules.playerStatus;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Keeps each player's status and sends it to the proxy, where bat shows it in the tab list. A
 * player has at most one status. A status is kept until the player changes or clears it, or leaves
 * the server or goes into spectator mode. A status is sent when it changes, and when the proxy
 * starts listening for the player.
 *
 * <p>Methods must be called on the main thread.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-23
 */
public final class PlayerStatusManager {

  /** The plugin messaging channel bat listens on. */
  public static final String STATUS_CHANNEL = "icecream:status";

  private final Map<UUID, PlayerStatus> statuses = new HashMap<>();

  /** Registers the channel. */
  public void start() {
    Bukkit.getMessenger().registerOutgoingPluginChannel(IceCream.instance, STATUS_CHANNEL);
  }

  /**
   * Clears everyone's status on the proxy unless the plugin is shutting down, and forgets all
   * players.
   */
  public void stop() {
    if (IceCream.instance.isEnabled()) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (statuses.remove(player.getUniqueId()) != null) send(player);
      }
    }
    statuses.clear();
    Bukkit.getMessenger().unregisterOutgoingPluginChannel(IceCream.instance, STATUS_CHANNEL);
  }

  /**
   * Sets the player's status, replacing any other.
   *
   * @return false when it already was their status
   */
  public boolean set(Player player, PlayerStatus status) {
    if (statuses.put(player.getUniqueId(), status) == status) return false;
    send(player);
    return true;
  }

  /** Clears the player's status. */
  public void clear(Player player) {
    if (statuses.remove(player.getUniqueId()) != null) send(player);
  }

  /** Returns the player's status, or null when they have none. */
  public PlayerStatus status(Player player) {
    return isSpectating(player) ? null : statuses.get(player.getUniqueId());
  }

  /** Drops the player's status. */
  public void forget(UUID playerId) {
    statuses.remove(playerId);
  }

  /** Sends the player's status, for when the proxy starts listening for them. */
  public void channelRegistered(Player player) {
    send(player);
  }

  /** Whether the player is in spectator mode, where they have no status. */
  public static boolean isSpectating(Player player) {
    return SpectatorModule.SPECTATOR_TOGGLE_KEY != null && SpectatorUtils.isInSpectatorMode(player);
  }

  /** Sends the player's status, unless the proxy is not listening for them yet. */
  private void send(Player player) {
    if (!player.getListeningPluginChannels().contains(STATUS_CHANNEL)) return;
    player.sendPluginMessage(
        IceCream.instance, STATUS_CHANNEL, encode(player.getUniqueId(), status(player)));
  }

  /**
   * Encodes a status message for bat: the player's UUID, the number of statuses (0 or 1), then the
   * status id.
   *
   * @param status the status, or null for none
   */
  static byte[] encode(UUID playerId, PlayerStatus status) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF(playerId.toString());
    out.writeInt(status == null ? 0 : 1);
    if (status != null) out.writeUTF(status.id());
    return out.toByteArray();
  }
}
