package com.nearvanilla.iceCream.modules.integrations;

import com.nearvanilla.iceCream.IceCream;
import github.scarsz.discordsrv.DiscordSRV;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Shared integration with DiscordSRV. Sends fake join/leave messages to Discord when players enter
 * or exit a hidden state (spectator or vanish). Each module holds its own instance so that
 * join/leave message formats are loaded from the correct config prefix.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class DiscordSRVIntegration {
  private static final Component DISCORD_ERROR_MESSAGE =
      MiniMessage.miniMessage()
          .deserialize(
              "<yellow>Heads up! The Discord leave/join message couldn't be sent.</yellow>");

  private String joinMessage;
  private String leaveMessage;
  private DiscordSRV discordSrv;

  /**
   * Initializes the DiscordSRV integration if DiscordSRV is present.
   *
   * @param configPrefix the config path prefix for message keys, e.g. {@code
   *     "modules.spectator.messages"}
   */
  public void init(String configPrefix) {
    if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) {
      IceCream.logger.info("DiscordSRV not found, skipping integration.");
      return;
    }

    discordSrv = DiscordSRV.getPlugin();

    joinMessage =
        IceCream.config.getString(configPrefix + ".discord-join", "%player% joined the server");
    leaveMessage =
        IceCream.config.getString(configPrefix + ".discord-leave", "%player% left the server");

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
  public void setVanishedMetadata(Player player, boolean vanished) {
    if (vanished) {
      player.setMetadata("vanished", new FixedMetadataValue(IceCream.instance, true));
    } else {
      player.removeMetadata("vanished", IceCream.instance);
    }
  }

  /**
   * Sends a fake leave message to Discord when a player enters a hidden state.
   *
   * @param player the player who entered the hidden state
   */
  public void sendFakeLeave(Player player) {
    if (discordSrv == null) return;

    try {
      discordSrv.sendLeaveMessage(player, leaveMessage);
    } catch (Exception e) {
      IceCream.logger.warning("Failed to send fake leave message to DiscordSRV: " + e.getMessage());
      player.sendMessage(DISCORD_ERROR_MESSAGE);
    }
  }

  /**
   * Sends a fake join message to Discord when a player exits a hidden state.
   *
   * @param player the player who exited the hidden state
   */
  public void sendFakeJoin(Player player) {
    if (discordSrv == null) return;

    try {
      discordSrv.sendJoinMessage(player, joinMessage);
    } catch (Exception e) {
      IceCream.logger.warning("Failed to send fake join message to DiscordSRV: " + e.getMessage());
      player.sendMessage(DISCORD_ERROR_MESSAGE);
    }
  }
}
