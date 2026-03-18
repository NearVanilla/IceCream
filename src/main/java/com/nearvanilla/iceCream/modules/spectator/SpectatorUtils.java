package com.nearvanilla.iceCream.modules.spectator;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.spectator.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.spectator.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.spectator.integrations.DynmapIntegration;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for spectator mode functionality. Provides methods to enable/disable spectator
 * mode, broadcast fake join/quit messages, and communicate spectator state to Velocity.
 *
 * @author Dynant
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

  public static void init() {
    // Load message formats
    joinMessageFormat =
        IceCream.config.getString(
            "modules.spectator.messages.join", "<yellow><player> joined the game</yellow>");
    leaveMessageFormat =
        IceCream.config.getString(
            "modules.spectator.messages.leave", "<yellow><player> left the game</yellow>");

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
      DiscordSRVIntegration.setVanishedMetadata(player, false);

      // Restore spectating players
      if (isInSpectatorMode(player)) disableSpectator(player);
    }
    CarbonChatIntegration.cleanup();
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
    // Save current gamemode before switching
    player
        .getPersistentDataContainer()
        .set(
            SpectatorModule.PREVIOUS_GAMEMODE_KEY,
            PersistentDataType.STRING,
            player.getGameMode().name());

    // Switch to spectator gamemode
    player.setGameMode(GameMode.SPECTATOR);

    // Set spectator state in PDC
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    // Mark as vanished for DiscordSRV (suppresses real join/leave messages)
    DiscordSRVIntegration.setVanishedMetadata(player, true);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      onlinePlayer.hidePlayer(IceCream.instance, player);
    }

    // Save pre-spectator Dynmap visibility so it can be restored on exit
    boolean wasHiddenOnDynmap = !DynmapIntegration.isPlayerVisible(player);
    if (wasHiddenOnDynmap) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.DYNMAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN, true);
    } else {
      player.getPersistentDataContainer().remove(SpectatorModule.DYNMAP_WAS_HIDDEN_KEY);
    }

    // Hide from Dynmap
    DynmapIntegration.hidePlayer(player);

    // Broadcast fake quit messages
    if (broadcastMessages) {
      broadcastFakeQuit(player);
      DiscordSRVIntegration.sendFakeLeave(player);
    }

    sendSpectatorStateToVelocity(player, true);
    showSpectatorBossbar(player);
  }

  /**
   * Disables spectator mode for the given player. Broadcasts fake join messages.
   *
   * @param player the player to remove from spectator mode
   */
  public static void disableSpectator(Player player) {
    // Set spectator state in PDC
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, false);

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
    DiscordSRVIntegration.setVanishedMetadata(player, false);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      onlinePlayer.showPlayer(IceCream.instance, player);
    }

    // Restore pre-spectator Dynmap visibility; only show if they were visible before
    boolean wasHiddenOnDynmap =
        Boolean.TRUE.equals(
            player
                .getPersistentDataContainer()
                .get(SpectatorModule.DYNMAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN));
    player.getPersistentDataContainer().remove(SpectatorModule.DYNMAP_WAS_HIDDEN_KEY);
    if (!wasHiddenOnDynmap) {
      DynmapIntegration.showPlayer(player);
    }

    // Broadcast fake join messages
    broadcastFakeJoin(player);
    DiscordSRVIntegration.sendFakeJoin(player);

    sendSpectatorStateToVelocity(player, false);
    hideSpectatorBossbar(player);
  }

  /**
   * Hides all players currently in spectator mode from the given player.
   *
   * @param player the player to hide spectating players from
   */
  public static void hideSpectatingPlayersFrom(Player player) {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      if (isInSpectatorMode(onlinePlayer)) {
        player.hidePlayer(IceCream.instance, onlinePlayer);
      }
    }
  }

  /**
   * Broadcasts a fake quit message for the given player to simulate them leaving.
   *
   * @param player the player to broadcast the fake quit for
   */
  public static void broadcastFakeQuit(Player player) {
    Component quitMessage =
        MiniMessage.miniMessage()
            .deserialize(leaveMessageFormat, Placeholder.component("player", player.displayName()));
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.equals(player)) {
        onlinePlayer.sendMessage(quitMessage);
      }
    }
  }

  /**
   * Broadcasts a fake join message for the given player to simulate them joining.
   *
   * @param player the player to broadcast the fake join for
   */
  public static void broadcastFakeJoin(Player player) {
    Component joinMessage =
        MiniMessage.miniMessage()
            .deserialize(joinMessageFormat, Placeholder.component("player", player.displayName()));
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.equals(player)) {
        onlinePlayer.sendMessage(joinMessage);
      }
    }
  }

  /**
   * Sends the spectator state to Velocity via plugin messaging. The message format is: - Player
   * UUID (String) - Spectator state (Boolean)
   *
   * @param player the player whose spectator state changed
   * @param spectating true if the player is now in spectator mode, false otherwise
   */
  public static void sendSpectatorStateToVelocity(Player player, boolean spectating) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF(player.getUniqueId().toString());
    out.writeBoolean(spectating);

    player.sendPluginMessage(IceCream.instance, SPECTATOR_CHANNEL, out.toByteArray());
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
