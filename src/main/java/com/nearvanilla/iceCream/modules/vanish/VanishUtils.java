package com.nearvanilla.iceCream.modules.vanish;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.integrations.DynmapIntegration;
import com.nearvanilla.iceCream.utils.FakeMessageUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for vanish functionality. Provides methods to hide/show players, broadcast fake
 * join/quit messages, and communicate vanish state to Velocity.
 *
 * @author Dynant
 * @author 105hua
 * @version 1.0
 * @since 2025-01-27
 */
public class VanishUtils {

  /** The plugin messaging channel identifier for vanish communication with Velocity. */
  public static final String VANISH_CHANNEL = "icecream:vanish";

  /** Bossbar shown to all vanished players. */
  private static BossBar vanishBossbar;

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
            "modules.vanish.messages.join", "<yellow><player> joined the game</yellow>");
    leaveMessageFormat =
        IceCream.config.getString(
            "modules.vanish.messages.leave", "<yellow><player> left the game</yellow>");

    // Load config and create bossbar
    String text =
        IceCream.config.getString("modules.vanish.bossbar.text", "You are currently vanished");
    String colorStr = IceCream.config.getString("modules.vanish.bossbar.color", "RED");

    Component bossbarText = MiniMessage.miniMessage().deserialize(text);
    BossBar.Color color;
    try {
      color = BossBar.Color.valueOf(colorStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      color = BossBar.Color.RED;
      IceCream.logger.warning("Invalid bossbar color '" + colorStr + "', using RED");
    }

    vanishBossbar = BossBar.bossBar(bossbarText, 1.0f, color, BossBar.Overlay.PROGRESS);

    registerChannel();
  }

  public static void cleanup() {
    if (vanishBossbar != null) {
      // Hide bossbar from viewers
      for (var viewer : vanishBossbar.viewers()) {
        if (viewer instanceof net.kyori.adventure.audience.Audience audience) {
          vanishBossbar.removeViewer(audience);
        }
      }

      vanishBossbar = null;
    }

    // Clean up player state
    for (Player player : Bukkit.getOnlinePlayers()) {
      discordSRV.setVanishedMetadata(player, false);

      // Show vanished players again
      if (isVanished(player)) showPlayer(player);
    }
    carbonChat.cleanup();
  }

  /** Registers the plugin messaging channel for Velocity communication. */
  public static void registerChannel() {
    Bukkit.getMessenger().registerOutgoingPluginChannel(IceCream.instance, VANISH_CHANNEL);
  }

  /**
   * Checks if a player is currently vanished.
   *
   * @param player the player to check
   * @return true if the player is vanished, false otherwise
   */
  public static boolean isVanished(Player player) {
    return Boolean.TRUE.equals(
        player
            .getPersistentDataContainer()
            .get(VanishModule.VANISH_TOGGLE_KEY, PersistentDataType.BOOLEAN));
  }

  /**
   * Hides the given player from all other online players. Broadcasts fake quit messages.
   *
   * @param player the player to hide
   */
  public static void hidePlayer(Player player) {
    hidePlayer(player, true);
  }

  /**
   * Hides the given player from all other online players.
   *
   * @param player the player to hide
   * @param broadcastMessages whether to broadcast fake quit messages (in-game and Discord)
   */
  public static void hidePlayer(Player player, boolean broadcastMessages) {
    // Set vanished state in PDC
    player
        .getPersistentDataContainer()
        .set(VanishModule.VANISH_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    // Mark as vanished for DiscordSRV (suppresses real join/leave messages)
    discordSRV.setVanishedMetadata(player, true);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      onlinePlayer.hidePlayer(IceCream.instance, player);
    }

    // Save pre-vanish Dynmap visibility so it can be restored on unvanish
    boolean wasHiddenOnDynmap = !DynmapIntegration.isPlayerVisible(player);
    if (wasHiddenOnDynmap) {
      player
          .getPersistentDataContainer()
          .set(VanishModule.DYNMAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN, true);
    } else {
      player.getPersistentDataContainer().remove(VanishModule.DYNMAP_WAS_HIDDEN_KEY);
    }

    // Hide from Dynmap
    DynmapIntegration.hidePlayer(player);

    // Broadcast fake quit messages
    if (broadcastMessages) {
      FakeMessageUtils.broadcastFakeMessage(player, leaveMessageFormat);
      discordSRV.sendFakeLeave(player);
    }

    player.setInvulnerable(true);
    FakeMessageUtils.sendStateToVelocity(player, VANISH_CHANNEL, true);
    showVanishBossbar(player);
  }

  /**
   * Shows the given player to all other online players. Broadcasts fake join messages.
   *
   * @param player the player to show
   */
  public static void showPlayer(Player player) {
    // Set vanished state in PDC
    player
        .getPersistentDataContainer()
        .set(VanishModule.VANISH_TOGGLE_KEY, PersistentDataType.BOOLEAN, false);

    // Clear vanished metadata for DiscordSRV
    discordSRV.setVanishedMetadata(player, false);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) {
        continue;
      }
      onlinePlayer.showPlayer(IceCream.instance, player);
    }
    // Restore pre-vanish Dynmap visibility; only show if they were visible before vanishing
    boolean wasHiddenOnDynmap =
        Boolean.TRUE.equals(
            player
                .getPersistentDataContainer()
                .get(VanishModule.DYNMAP_WAS_HIDDEN_KEY, PersistentDataType.BOOLEAN));
    player.getPersistentDataContainer().remove(VanishModule.DYNMAP_WAS_HIDDEN_KEY);
    if (!wasHiddenOnDynmap) {
      DynmapIntegration.showPlayer(player);
    }

    // Broadcast fake join messages
    FakeMessageUtils.broadcastFakeMessage(player, joinMessageFormat);
    discordSRV.sendFakeJoin(player);

    player.setInvulnerable(false);
    FakeMessageUtils.sendStateToVelocity(player, VANISH_CHANNEL, false);
    hideVanishBossbar(player);
  }

  /**
   * Hides all currently vanished players from the given player.
   *
   * @param player the player to hide vanished players from
   */
  public static void hideVanishedPlayersFrom(Player player) {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (onlinePlayer.equals(player)) continue;

      if (isVanished(onlinePlayer)) {
        player.hidePlayer(IceCream.instance, onlinePlayer);
      }
    }
  }

  /**
   * Shows the vanish bossbar to the given player.
   *
   * @param player the player to show the bossbar to
   */
  public static void showVanishBossbar(Player player) {
    if (vanishBossbar == null) return;

    player.showBossBar(vanishBossbar);
  }

  /**
   * Hides the vanish bossbar from the given player.
   *
   * @param player the player to hide the bossbar from
   */
  public static void hideVanishBossbar(Player player) {
    if (vanishBossbar == null) return;

    player.hideBossBar(vanishBossbar);
  }
}
