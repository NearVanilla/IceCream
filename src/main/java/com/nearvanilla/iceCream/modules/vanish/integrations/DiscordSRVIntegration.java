package com.nearvanilla.iceCream.modules.vanish.integrations;

import com.nearvanilla.iceCream.IceCream;
import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Integration with DiscordSRV for vanished players. Sends fake join/leave messages to Discord when
 * players vanish or unvanish.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class DiscordSRVIntegration {
  private static String joinMessage;
  private static String leaveMessage;
  private static DiscordSRV discordSrv;

  /** Initializes the DiscordSRV integration if DiscordSRV is present. */
  public static void init() {
    if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) {
      IceCream.logger.info("DiscordSRV not found, skipping integration.");
      return;
    }

    discordSrv = DiscordSRV.getPlugin();

    joinMessage =
        IceCream.config.getString(
            "modules.vanish.messages.discord-join", "%player% joined the server");
    leaveMessage =
        IceCream.config.getString(
            "modules.vanish.messages.discord-leave", "%player% left the server");

    IceCream.logger.info("DiscordSRV integration enabled.");
  }

  /**
   * Sets the "vanished" metadata on a player. DiscordSRV checks this to suppress real join/leave
   * messages.
   *
   * @param player the player
   * @param vanished true to mark as vanished, false to clear
   */
  @SuppressWarnings("deprecation") // Standard metadata API for DiscordSRV compatibility
  public static void setVanishedMetadata(Player player, boolean vanished) {
    if (vanished) {
      player.setMetadata("vanished", new FixedMetadataValue(IceCream.instance, true));
    } else {
      player.removeMetadata("vanished", IceCream.instance);
    }
  }

  /**
   * Sends a fake leave message to Discord when a player vanishes.
   *
   * @param player the player who vanished
   */
  public static void sendFakeLeave(Player player) {
    if (discordSrv == null) return;

    discordSrv.sendLeaveMessage(player, leaveMessage);
  }

  /**
   * Sends a fake join message to Discord when a player unvanishes.
   *
   * @param player the player who unvanished
   */
  public static void sendFakeJoin(Player player) {
    if (discordSrv == null) return;

    discordSrv.sendJoinMessage(player, joinMessage);
  }
}
