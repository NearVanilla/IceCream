package com.nearvanilla.iceCream.modules.mapTools.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/**
 * Tells the NearVanilla Tools mod about map tools results over {@link MapToolsChannel#CHANNEL}, as
 * compact JSON, so it does not have to read them from chat. Map tools itself knows nothing about
 * the mod: this listens to {@link MapAreaChangeEvent} and {@link MapToolsErrorEvent}. Chat messages
 * are sent as usual, and only players whose client listens on the channel get anything.
 *
 * <p>Every message has {@code v}, the protocol version, and {@code type}:
 *
 * <ul>
 *   <li>{@code hello}: sent when the mod starts listening, so it knows replies come this way.
 *   <li>{@code changed}: a claim or marker was {@code created}, {@code updated}, {@code deleted} or
 *       {@code renamed} ({@code previousId} is its old id; other details may have changed too),
 *       sent to everyone who may use map tools.
 *   <li>{@code error}: a command the player sent over the channel failed, with the message as plain
 *       text. Errors from typed commands and forms are not sent.
 * </ul>
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-22
 */
public final class MapToolsReplies implements Listener {

  /** Raised when a change could break the mod. */
  public static final int VERSION = 1;

  record Hello(int v, String type) {}

  record Changed(int v, String type, String change, String kind, String id, String previousId) {}

  record Error(int v, String type, String message) {}

  /**
   * Greets every player whose mod already listens, for when the module starts after they joined.
   */
  public static void helloAll() {
    for (Player player : Bukkit.getOnlinePlayers()) hello(player);
  }

  /** Tells the mod that this server sends its answers over the channel, not only in chat. */
  private static void hello(Player player) {
    send(player, new Hello(VERSION, "hello"));
  }

  /** The mod starts listening on its channel after the player joins. */
  @EventHandler
  public void onRegisterChannel(PlayerRegisterChannelEvent event) {
    if (MapToolsChannel.CHANNEL.equals(event.getChannel())) hello(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChange(MapAreaChangeEvent event) {
    MapArea area = event.getArea();
    Changed message =
        new Changed(
            VERSION,
            "changed",
            event.getChange().name().toLowerCase(Locale.ROOT),
            area.isMarker() ? "marker" : "claim",
            area.id(),
            event.getPreviousId());
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.hasPermission(MapToolsUtils.PERMISSION)) send(player, message);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onError(MapToolsErrorEvent event) {
    if (event.getSender() instanceof Player player
        && MapToolsChannel.isRunning(player.getUniqueId())) {
      send(
          player,
          new Error(
              VERSION,
              "error",
              PlainTextComponentSerializer.plainText().serialize(event.getMessage())));
    }
  }

  /** Never throws: a reply that cannot be sent must not undo or interrupt the change itself. */
  private static void send(Player player, Object message) {
    if (!player.getListeningPluginChannels().contains(MapToolsChannel.CHANNEL)) return;
    try {
      player.sendPluginMessage(
          IceCream.instance, MapToolsChannel.CHANNEL, JsonLoader.toBytes(message));
    } catch (RuntimeException e) {
      IceCream.logger.log(
          Level.WARNING, "Failed to send a map tools reply to " + player.getName(), e);
    }
  }
}
