package com.nearvanilla.iceCream.modules.spectator;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.integrations.DynmapIntegration;
import com.nearvanilla.iceCream.utils.FakeMessageUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

  private static DiscordSRVIntegration discordSRV;
  private static CarbonChatIntegration carbonChat;

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
      if (isInSpectatorMode(player)) disableSpectator(player);
    }
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

    // Set spectator state in PDC
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    // Mark as vanished for DiscordSRV (suppresses real join/leave messages)
    discordSRV.setVanishedMetadata(player, true);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      onlinePlayer.hidePlayer(IceCream.instance, player);
    }

    // Switch to spectator gamemode after hiding — setGameMode triggers a tab list refresh packet
    // on the next tick, so the player must already be hidden before that fires.
    player.setGameMode(GameMode.SPECTATOR);

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
    discordSRV.setVanishedMetadata(player, false);

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
    FakeMessageUtils.broadcastFakeMessage(player, joinMessageFormat);
    discordSRV.sendFakeJoin(player);

    FakeMessageUtils.sendStateToVelocity(player, SPECTATOR_CHANNEL, false);
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
