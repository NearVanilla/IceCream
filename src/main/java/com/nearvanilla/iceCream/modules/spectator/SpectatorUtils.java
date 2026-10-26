package com.nearvanilla.iceCream.modules.spectator;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.integrations.SquaremapIntegration;
import com.nearvanilla.iceCream.utils.FakeMessageUtils;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for spectator mode functionality. Provides methods to enable/disable spectator
 * mode, broadcast fake join/quit messages, and communicate spectator state to Velocity.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-03-18
 */
public class SpectatorUtils {

  /** The plugin messaging channel identifier for spectator communication with Velocity. */
  public static final String SPECTATOR_CHANNEL = "icecream:spectator";

  /** Bossbar shown to all players in spectator mode. */
  private static BossBar spectatorBossbar;

  /** Message format for fake messages. */
  private static String joinMessageFormat;

  private static String leaveMessageFormat;

  /** Component shown to a player whose private message to or from a spectator was blocked. */
  private static Component privateMessageOfflineMessage;

  /** Lower-case private message command aliases that the vanilla listener should intercept. */
  private static Set<String> blockedPrivateMessageCommands = Collections.emptySet();

  private static DiscordSRVIntegration discordSRV;
  private static CarbonChatIntegration carbonChat;

  /**
   * Who is in spectator mode, and since when, for checks on players this thread does not own. The
   * player data stays the saved copy and fills this in when a player joins.
   */
  private static final Set<UUID> spectating = ConcurrentHashMap.newKeySet();

  private static final Map<UUID, Long> hiddenSince = new ConcurrentHashMap<>();

  public static void init(
      DiscordSRVIntegration discordSRVIntegration, CarbonChatIntegration carbonChatIntegration) {
    discordSRV = discordSRVIntegration;
    carbonChat = carbonChatIntegration;

    // Load message formats
    joinMessageFormat =
        IceCream.config.getString(
            "modules.spectator.messages.join", "<yellow><player> joined the game</yellow>");
    leaveMessageFormat =
        IceCream.config.getString(
            "modules.spectator.messages.leave", "<yellow><player> left the game</yellow>");

    // Load private message interception config
    String offlineMessageFormat =
        IceCream.config.getString(
            "modules.spectator.private-message.offline-message", "<red>Player not found</red>");
    privateMessageOfflineMessage = MiniMessage.miniMessage().deserialize(offlineMessageFormat);

    List<String> configuredCommands =
        IceCream.config.getStringList("modules.spectator.private-message.blocked-commands");
    Set<String> commands = new HashSet<>();
    for (String command : configuredCommands) {
      String trimmed = command.trim().toLowerCase();
      if (!trimmed.isEmpty()) {
        commands.add(trimmed);
      }
    }
    blockedPrivateMessageCommands = commands;

    // Load config and create bossbar
    String text =
        IceCream.config.getString(
            "modules.spectator.bossbar.text", "You are currently in spectator mode");
    String colorStr = IceCream.config.getString("modules.spectator.bossbar.color", "BLUE");

    Component bossbarText = MiniMessage.miniMessage().deserialize(text);
    BossBar.Color color;
    try {
      color = BossBar.Color.valueOf(colorStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      color = BossBar.Color.BLUE;
      IceCream.logger.warning("Invalid bossbar color '" + colorStr + "', using BLUE");
    }

    spectatorBossbar = BossBar.bossBar(bossbarText, 1.0f, color, BossBar.Overlay.PROGRESS);

    registerChannel();
  }

  public static void cleanup() {
    if (spectatorBossbar != null) {
      // Hide bossbar from viewers
      for (var viewer : spectatorBossbar.viewers()) {
        if (viewer instanceof net.kyori.adventure.audience.Audience audience) {
          spectatorBossbar.removeViewer(audience);
        }
      }

      spectatorBossbar = null;
    }

    // Clean up player state
    for (Player player : Bukkit.getOnlinePlayers()) {
      discordSRV.setVanishedMetadata(player, false);

      // Restore spectating players
      if (!isInSpectatorMode(player)) continue;
      try {
        if (SchedulerUtils.isFolia()) {
          // Folia stops the region threads before plugins, so a teleport can no longer happen.
          // The player leaves spectator mode when they next join instead.
          player
              .getPersistentDataContainer()
              .set(SpectatorModule.RESTORE_ON_JOIN_KEY, PersistentDataType.BOOLEAN, true);
        } else {
          disableSpectatorNow(player);
        }
      } catch (RuntimeException e) {
        IceCream.logger.log(
            Level.WARNING, "Failed to take " + player.getName() + " out of spectator mode", e);
      }
    }
    spectating.clear();
    hiddenSince.clear();
    carbonChat.cleanup();
  }

  /** Registers the plugin messaging channel for Velocity communication. */
  public static void registerChannel() {
    Bukkit.getMessenger().registerOutgoingPluginChannel(IceCream.instance, SPECTATOR_CHANNEL);
  }

  /**
   * Checks if a player is currently in spectator mode.
   *
   * @param player the player to check
   * @return true if the player is in spectator mode, false otherwise
   */
  public static boolean isInSpectatorMode(Player player) {
    return Boolean.TRUE.equals(
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN));
  }

  /**
   * Checks if an online player is in spectator mode. Safe to call from any thread, unlike {@link
   * #isInSpectatorMode}, which reads the player's own data.
   *
   * @param playerId the player to check
   * @return true if the player is in spectator mode, false otherwise
   */
  public static boolean isSpectating(UUID playerId) {
    return spectating.contains(playerId);
  }

  /**
   * Returns when an online player was hidden by spectator mode, which is the moment other players
   * last saw them, or 0 if they are not hidden. Safe to call from any thread.
   *
   * @param playerId the player to check
   * @return the epoch milliseconds at which the player was hidden, or 0 if unknown
   */
  public static long getHiddenSince(UUID playerId) {
    return hiddenSince.getOrDefault(playerId, 0L);
  }

  /**
   * Records a player's saved spectator state so other threads can check it.
   *
   * @param player the player, on their own thread
   */
  public static void track(Player player) {
    if (!isInSpectatorMode(player)) return;
    spectating.add(player.getUniqueId());
    Long since =
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.HIDDEN_SINCE_KEY, PersistentDataType.LONG);
    if (since != null) hiddenSince.put(player.getUniqueId(), since);
  }

  /**
   * Forgets a player who left or stopped spectating.
   *
   * @param playerId the player
   */
  public static void untrack(UUID playerId) {
    spectating.remove(playerId);
    hiddenSince.remove(playerId);
  }

  /**
   * Returns whether the given player is allowed to send and receive private messages that would
   * otherwise be blocked by the spectator module's interception. Holders of the spectator toggle
   * permission are treated as staff and can communicate with spectating players.
   *
   * @param player the player to check
   * @return true if the player can bypass the private message block, false otherwise
   */
  public static boolean canBypassPrivateMessage(Player player) {
    return player != null && player.hasPermission("icecream.modules.spectator");
  }

  /**
   * Returns the component sent to a player whose private message to or from a spectator was
   * blocked. Defaults to a vanilla "Player not found" style message so that the block is
   * indistinguishable from the recipient being offline.
   *
   * @return the offline-style component
   */
  public static Component getPrivateMessageOfflineMessage() {
    return privateMessageOfflineMessage;
  }

  /**
   * Returns the set of lower-case private message command aliases (without leading slash) that the
   * vanilla command listener should intercept. Configured via {@code
   * modules.spectator.private-message.blocked-commands}.
   *
   * @return an unmodifiable view of the configured aliases
   */
  public static Set<String> getBlockedPrivateMessageCommands() {
    return Collections.unmodifiableSet(blockedPrivateMessageCommands);
  }

  /**
   * Enables spectator mode for the given player. Broadcasts fake quit messages.
   *
   * @param player the player to put in spectator mode
   */
  public static void enableSpectator(Player player) {
    enableSpectator(player, true);
  }

  /**
   * Enables spectator mode for the given player.
   *
   * @param player the player to put in spectator mode
   * @param broadcastMessages whether to broadcast fake quit messages (in-game and Discord)
   */
  public static void enableSpectator(Player player, boolean broadcastMessages) {
    // Save current gamemode and location before switching, but only if we're not already in
    // spectator mode. On rejoin, the player's gamemode is restored as SPECTATOR by Paper, so
    // re-saving here would overwrite the originals stored in the PDC.
    if (player.getGameMode() != GameMode.SPECTATOR) {
      player
          .getPersistentDataContainer()
          .set(
              SpectatorModule.PREVIOUS_GAMEMODE_KEY,
              PersistentDataType.STRING,
              player.getGameMode().name());

      org.bukkit.Location loc = player.getLocation();
      String serialized =
          loc.getWorld().getName()
              + ","
              + loc.getX()
              + ","
              + loc.getY()
              + ","
              + loc.getZ()
              + ","
              + loc.getYaw()
              + ","
              + loc.getPitch();
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.PREVIOUS_LOCATION_KEY, PersistentDataType.STRING, serialized);

      // Save pre-spectator squaremap visibility so it can be restored on exit.
      // Kept inside this guard: on rejoin the player is already hidden on squaremap, so
      // re-sampling outside the guard would corrupt the stored pre-spectator state.
      boolean wasHiddenOnMap = !SquaremapIntegration.isPlayerVisible(player);
      if (wasHiddenOnMap) {
        player
            .getPersistentDataContainer()
            .set(SpectatorModule.MAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN, true);
      } else {
        player.getPersistentDataContainer().remove(SpectatorModule.MAP_WAS_HIDDEN_KEY);
      }

      // Record the moment others last saw them
      player
          .getPersistentDataContainer()
          .set(
              SpectatorModule.HIDDEN_SINCE_KEY,
              PersistentDataType.LONG,
              System.currentTimeMillis());
    }

    // Set spectator state in PDC
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    track(player);

    // Mark as vanished for DiscordSRV (suppresses real join/leave messages)
    discordSRV.setVanishedMetadata(player, true);

    // Each viewer hides the player on their own thread, since hiding changes the viewer.
    SchedulerUtils.runForEach(
            othersThan(player), viewer -> viewer.hidePlayer(IceCream.instance, player))
        .thenRun(
            () -> SchedulerUtils.runFor(player, () -> finishEnabling(player, broadcastMessages)));
  }

  /** Switches a player who is now hidden from everyone to spectator mode. */
  private static void finishEnabling(Player player, boolean broadcastMessages) {
    // Spectator mode was turned off again while the viewers were hiding the player.
    if (!isInSpectatorMode(player)) return;

    // Switch to spectator gamemode after hiding: setGameMode triggers a tab list refresh packet
    // on the next tick, so the player must already be hidden before that fires.
    player.setGameMode(GameMode.SPECTATOR);

    // Hide from squaremap
    SquaremapIntegration.hidePlayer(player);

    // Broadcast fake quit messages
    if (broadcastMessages) {
      FakeMessageUtils.broadcastFakeMessage(player, leaveMessageFormat);
      discordSRV.sendFakeLeave(player);
    }

    FakeMessageUtils.sendStateToVelocity(player, SPECTATOR_CHANNEL, true);
    showSpectatorBossbar(player);
  }

  /**
   * Disables spectator mode for the given player. Broadcasts fake join messages.
   *
   * @param player the player to remove from spectator mode
   */
  public static void disableSpectator(Player player) {
    disableSpectator(player, true);
  }

  /**
   * Disables spectator mode for the given player: takes them back to where they started spectating,
   * then restores their gamemode and shows them again.
   *
   * @param player the player to remove from spectator mode
   * @param broadcastMessages whether to broadcast fake join messages (in-game and Discord)
   */
  public static void disableSpectator(Player player, boolean broadcastMessages) {
    Location previousLocation = previousLocation(player);
    if (previousLocation == null) {
      finishDisabling(player, broadcastMessages);
      return;
    }
    // Teleport before leaving spectator mode, so the player is never inside a wall in survival.
    player
        .teleportAsync(previousLocation)
        .whenComplete(
            (teleported, error) -> {
              if (error != null || !Boolean.TRUE.equals(teleported)) {
                IceCream.logger.warning(
                    "Could not return " + player.getName() + " to where they started spectating.");
              }
              SchedulerUtils.runFor(player, () -> finishDisabling(player, broadcastMessages));
            });
  }

  /**
   * Disables spectator mode straight away, for a Paper shutdown, where an asynchronous teleport
   * would not land before players are saved.
   */
  private static void disableSpectatorNow(Player player) {
    Location previousLocation = previousLocation(player);
    if (previousLocation != null) player.teleport(previousLocation);
    finishDisabling(player, true);
  }

  /**
   * Reads where a player started spectating.
   *
   * @return the location, or null if none was saved or it cannot be used
   */
  private static Location previousLocation(Player player) {
    String serializedLocation =
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.PREVIOUS_LOCATION_KEY, PersistentDataType.STRING);
    if (serializedLocation == null) return null;
    String[] parts = serializedLocation.split(",", 6);
    if (parts.length != 6) return null;
    try {
      World world = Bukkit.getWorld(parts[0]);
      if (world == null) return null;
      double x = Double.parseDouble(parts[1]);
      double y = Double.parseDouble(parts[2]);
      double z = Double.parseDouble(parts[3]);
      float yaw = Float.parseFloat(parts[4]);
      float pitch = Float.parseFloat(parts[5]);
      return new Location(world, x, y, z, yaw, pitch);
    } catch (NumberFormatException e) {
      IceCream.logger.warning(
          "Failed to parse stored spectator location for "
              + player.getName()
              + ": "
              + serializedLocation);
      return null;
    }
  }

  /** Takes a player out of spectator mode once they are back where they started. */
  private static void finishDisabling(Player player, boolean broadcastMessages) {
    // Spectator mode was already turned off while the player was teleporting.
    if (!isInSpectatorMode(player)) return;

    // Set spectator state in PDC
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, false);
    player.getPersistentDataContainer().remove(SpectatorModule.HIDDEN_SINCE_KEY);
    player.getPersistentDataContainer().remove(SpectatorModule.PREVIOUS_LOCATION_KEY);
    player.getPersistentDataContainer().remove(SpectatorModule.RESTORE_ON_JOIN_KEY);
    untrack(player.getUniqueId());

    // Restore previous gamemode
    String previousGamemodeName =
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.PREVIOUS_GAMEMODE_KEY, PersistentDataType.STRING);
    GameMode previousGamemode = GameMode.SURVIVAL;
    if (previousGamemodeName != null) {
      try {
        previousGamemode = GameMode.valueOf(previousGamemodeName);
      } catch (IllegalArgumentException e) {
        IceCream.logger.warning(
            "Invalid stored gamemode '" + previousGamemodeName + "', defaulting to SURVIVAL");
      }
    }
    player.setGameMode(previousGamemode);
    player.getPersistentDataContainer().remove(SpectatorModule.PREVIOUS_GAMEMODE_KEY);

    // Clear vanished metadata for DiscordSRV
    discordSRV.setVanishedMetadata(player, false);

    // Each viewer shows the player on their own thread, since showing changes the viewer.
    SchedulerUtils.runForEach(
        othersThan(player), viewer -> viewer.showPlayer(IceCream.instance, player));

    // Restore pre-spectator squaremap visibility; only show if they were visible before
    boolean wasHiddenOnMap =
        Boolean.TRUE.equals(
            player
                .getPersistentDataContainer()
                .get(SpectatorModule.MAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN));
    player.getPersistentDataContainer().remove(SpectatorModule.MAP_WAS_HIDDEN_KEY);
    if (!wasHiddenOnMap) {
      SquaremapIntegration.showPlayer(player);
    }

    // Broadcast fake join messages
    if (broadcastMessages) {
      FakeMessageUtils.broadcastFakeMessage(player, joinMessageFormat);
      discordSRV.sendFakeJoin(player);
    }

    FakeMessageUtils.sendStateToVelocity(player, SPECTATOR_CHANNEL, false);
    hideSpectatorBossbar(player);
  }

  /** The online players other than the given one. */
  private static List<Player> othersThan(Player player) {
    List<Player> others = new ArrayList<>(Bukkit.getOnlinePlayers());
    others.remove(player);
    return others;
  }

  /**
   * Hides all players currently in spectator mode from the given player.
   *
   * @param player the player to hide spectating players from
   */
  public static void hideSpectatingPlayersFrom(Player player) {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      if (isSpectating(onlinePlayer.getUniqueId())) {
        player.hidePlayer(IceCream.instance, onlinePlayer);
      }
    }
  }

  /**
   * Shows the spectator bossbar to the given player.
   *
   * @param player the player to show the bossbar to
   */
  public static void showSpectatorBossbar(Player player) {
    if (spectatorBossbar == null) return;

    player.showBossBar(spectatorBossbar);
  }

  /**
   * Hides the spectator bossbar from the given player.
   *
   * @param player the player to hide the bossbar from
   */
  public static void hideSpectatorBossbar(Player player) {
    if (spectatorBossbar == null) return;

    player.hideBossBar(spectatorBossbar);
  }
}
