package com.nearvanilla.iceCream.modules.mapTools.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Runs {@code /maptools} commands sent by the NearVanilla Tools mod over a plugin channel instead
 * of chat. The command runs as the player, exactly as if typed, so the same permission checks,
 * forms and chat replies apply. While it runs, {@link MapToolsReplies} sends the player's errors
 * back to the mod as well.
 *
 * <p>The message is the command without its slash, as UTF-8 text, such as {@code maptools create
 * claim polygon "0 0;10 0;10 10" spawn gold Spawn}. Anything else is ignored.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-20
 */
public class MapToolsChannel implements PluginMessageListener {

  /** The channel the mod sends on. Paper tells clients about it when they join. */
  public static final String CHANNEL = "nvtools:maptools";

  /** Far more than any real command; longer messages are ignored. */
  private static final int MAX_BYTES = 16_384;

  /**
   * How many commands a player may send at once. Saving an edited claim in the mod sends one
   * command per changed detail, so a short burst has to fit.
   */
  private static final double BURST = 16;

  /** How many commands per second a player may keep sending after a burst. */
  private static final double PER_SECOND = 4;

  /** Each player's allowance: how many commands they may still send, and when it was counted. */
  private record Allowance(double left, long atNanos) {}

  private static final Map<UUID, Allowance> allowances = new ConcurrentHashMap<>();

  /** The players whose command from the mod is running right now. */
  private static final Set<UUID> running = ConcurrentHashMap.newKeySet();

  /** Whether this player's command from the mod is running right now, so errors answer it. */
  public static boolean isRunning(UUID player) {
    return running.contains(player);
  }

  /** Forgets a player's allowance when they leave. */
  public static void forget(UUID player) {
    allowances.remove(player);
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!CHANNEL.equals(channel)) return;
    // Nothing is read from players who may not use map tools.
    if (!player.hasPermission(MapToolsUtils.PERMISSION)) return;
    String command = commandFrom(message);
    if (command == null) return;

    Runnable run =
        () -> {
          if (!player.isOnline()) return;
          running.add(player.getUniqueId());
          try {
            if (!allow(player.getUniqueId())) {
              MapToolsUtils.error(
                  player,
                  MiniMessage.miniMessage()
                      .deserialize(
                          "<red>Too many map tools commands at once. Wait a moment and try"
                              + " again.</red>"));
              return;
            }
            // Commands run this way skip PlayerCommandPreprocessEvent, so command logs never see
            // them.
            IceCream.logger.info("[maptools via mod] " + player.getName() + ": /" + command);
            player.performCommand(command);
          } finally {
            running.remove(player.getUniqueId());
          }
        };
    if (Bukkit.isPrimaryThread()) {
      run.run();
    } else {
      Bukkit.getScheduler().runTask(IceCream.instance, run);
    }
  }

  /**
   * Reads the command from a message.
   *
   * @return the command, or null if the message is not one: too long, not a map tools command, or
   *     with characters typed chat does not allow (control characters, formatting codes)
   */
  static String commandFrom(byte[] message) {
    if (message.length > MAX_BYTES) return null;
    String command = new String(message, StandardCharsets.UTF_8);
    if (!command.startsWith("maptools ")
        || command.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00A7')) {
      return null;
    }
    return command;
  }

  /**
   * Takes one command from the player's allowance, which refills by {@link #PER_SECOND} up to
   * {@link #BURST}. Each command that changes a claim saves the claims file and the web export, so
   * this keeps a client from flooding the server with them.
   *
   * @return false if the player has sent too many commands too quickly
   */
  static boolean allow(UUID player) {
    long now = System.nanoTime();
    boolean[] allowed = {false};
    allowances.compute(
        player,
        (id, previous) -> {
          double left =
              previous == null
                  ? BURST
                  : Math.min(
                      BURST, previous.left() + (now - previous.atNanos()) / 1e9 * PER_SECOND);
          allowed[0] = left >= 1;
          return new Allowance(allowed[0] ? left - 1 : left, now);
        });
    return allowed[0];
  }
}
