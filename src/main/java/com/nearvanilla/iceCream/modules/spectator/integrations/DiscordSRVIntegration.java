package com.nearvanilla.iceCream.modules.spectator.integrations;

import com.nearvanilla.iceCream.IceCream;
import github.scarsz.discordsrv.DiscordSRV;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Integration with DiscordSRV for players in spectator mode. Sends fake join/leave messages to
 * Discord when players enter or exit spectator mode.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-03-18
 */
public class DiscordSRVIntegration {
  private static final Component DISCORD_ERROR_MESSAGE =
      MiniMessage.miniMessage()
          .deserialize(
              "<yellow>Heads up! The Discord leave/join message couldn't be sent.</yellow>");

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
            "modules.spectator.messages.discord-join", "%player% joined the server");
    leaveMessage =
        IceCream.config.getString(
            "modules.spectator.messages.discord-leave", "%player% left the server");

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
   * Sends a fake leave message to Discord when a player enters spectator mode.
   *
   * @param player the player who entered spectator mode
   */
  public static void sendFakeLeave(Player player) {
    if (discordSrv == null) return;

    try {
      discordSrv.sendLeaveMessage(player, leaveMessage);
    } catch (Exception e) {
      IceCream.logger.warning("Failed to send fake leave message to DiscordSRV: " + e.getMessage());
      player.sendMessage(DISCORD_ERROR_MESSAGE);
    }
  }

  /**
   * Sends a fake join message to Discord when a player exits spectator mode.
   *
   * @param player the player who exited spectator mode
   */
  public static void sendFakeJoin(Player player) {
    if (discordSrv == null) return;

    try {
      discordSrv.sendJoinMessage(player, joinMessage);
    } catch (Exception e) {
      IceCream.logger.warning("Failed to send fake join message to DiscordSRV: " + e.getMessage());
      player.sendMessage(DISCORD_ERROR_MESSAGE);
    }
  }
}
