package com.nearvanilla.iceCream.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nearvanilla.iceCream.IceCream;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shared utilities for broadcasting fake join/quit messages and sending player state to Velocity.
 *
 * @author Dynant
 * @author 105hua
 * @version 1.0
 * @since 2025-01-27
 */
public class FakeMessageUtils {

  /**
   * Cached reflection handles for routing broadcasts through the vanilla NMS {@code
   * PlayerList.broadcastMessage} path. Populated on first use. We use reflection because the
   * CraftBukkit / NMS classes are only present on the runtime classpath, not on the {@code
   * paper-api} compile classpath this plugin depends on.
   */
  private static Object playerListInstance;

  private static Method broadcastMessageMethod;
  private static Method componentLiteralMethod;
  private static Method componentTranslatableMethod;

  /**
   * Broadcasts a server-system join/leave announcement for the given player, routed through the
   * vanilla NMS broadcast path so that mods such as "No Join Leave Messages" (which mixin into
   * {@code PlayerList.broadcastMessage}) can intercept and cancel it.
   *
   * <p>The announcement uses the vanilla translation keys {@code multiplayer.player.joined} (for
   * joins) and {@code multiplayer.player.left} (for leaves). When the mod is absent, the
   * announcement is rendered client-side from each player's language file.
   *
   * @param excluded the player whose join/leave is being faked
   * @param joining {@code true} for a fake join (player is leaving spectator), {@code false} for a
   *     fake leave (player is entering spectator)
   */
  public static void broadcastFakeMessage(Player excluded, boolean joining) {
    String key = joining ? "multiplayer.player.joined" : "multiplayer.player.left";

    try {
      ensureReflectionInitialized();

      Object playerNameComponent = componentLiteralMethod.invoke(null, excluded.getName());
      Object msg =
          componentTranslatableMethod.invoke(null, key, new Object[] {playerNameComponent});
      broadcastMessageMethod.invoke(playerListInstance, msg, false);
    } catch (Exception e) {
      IceCream.logger.severe(
          "Failed to broadcast fake "
              + (joining ? "join" : "leave")
              + " message for "
              + excluded.getName()
              + ": "
              + e.getMessage());
    }
  }

  /**
   * Lazily resolves and caches the reflection handles used to route the broadcast through NMS.
   * Idempotent and thread-safe; a failure logs once and leaves the handles null, causing subsequent
   * calls to no-op with a logged error.
   */
  private static synchronized void ensureReflectionInitialized() throws Exception {
    if (broadcastMessageMethod != null) return;

    org.bukkit.Server server = Bukkit.getServer();
    Object craftServer = server.getClass().getMethod("getServer").invoke(server);
    playerListInstance = craftServer.getClass().getMethod("getPlayerList").invoke(craftServer);

    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
    Class<?> playerListClass = Class.forName("net.minecraft.server.players.PlayerList");

    broadcastMessageMethod =
        playerListClass.getMethod("broadcastMessage", componentClass, boolean.class);
    componentLiteralMethod = componentClass.getMethod("literal", String.class);
    componentTranslatableMethod =
        componentClass.getMethod("translatable", String.class, Object[].class);
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
