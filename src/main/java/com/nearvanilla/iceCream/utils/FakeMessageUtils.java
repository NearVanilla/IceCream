package com.nearvanilla.iceCream.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nearvanilla.iceCream.IceCream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shared utilities for broadcasting fake join/quit messages and sending player state to Velocity.
 *
 * <p>Originally implemented by Dynant as part of {@link
 * com.nearvanilla.iceCream.modules.vanish.VanishUtils}, extracted here for reuse across modules.
 *
 * @author Dynant
 * @author 105hua
 * @version 1.0
 * @since 2025-01-27
 */
public class FakeMessageUtils {

  /**
   * Broadcasts a MiniMessage-formatted message to all online players except the excluded player.
   * The {@code <player>} tag in the format string is replaced with the excluded player's display
   * name.
   *
   * @param excluded the player who is excluded from receiving the message
   * @param format the MiniMessage format string
   */
  public static void broadcastFakeMessage(Player excluded, String format) {
    Component message =
        MiniMessage.miniMessage()
            .deserialize(format, Placeholder.component("player", excluded.displayName()));
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.equals(excluded)) {
        onlinePlayer.sendMessage(message);
      }
    }
  }

  /**
   * Sends a player's hidden state to Velocity via plugin messaging.
   *
   * <p>Message format: player UUID (String), state (Boolean).
   *
   * @param player the player whose state changed
   * @param channel the plugin messaging channel to use
   * @param state the new hidden state
   */
  public static void sendStateToVelocity(Player player, String channel, boolean state) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF(player.getUniqueId().toString());
    out.writeBoolean(state);

    player.sendPluginMessage(IceCream.instance, channel, out.toByteArray());
  }
}
