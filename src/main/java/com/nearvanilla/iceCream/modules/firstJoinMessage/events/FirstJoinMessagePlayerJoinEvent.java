package com.nearvanilla.iceCream.modules.firstJoinMessage.events;

import com.nearvanilla.iceCream.IceCream;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Welcomes players joining the server for the first time. Everyone receives the announcement, while
 * the new player also receives the welcome message. Either message is skipped when it is left empty
 * in the configuration.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-27
 */
public class FirstJoinMessagePlayerJoinEvent implements Listener {

  private static final String CONFIG_PATH = "modules.firstjoinmessage.";

  /**
   * Handles player join events.
   *
   * @param event the player join event
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    if (player.hasPlayedBefore()) {
      return;
    }

    // Inserted as a component rather than as raw text, so a display name containing
    // MiniMessage syntax cannot alter the surrounding message.
    TagResolver playerTag = Placeholder.component("player", player.displayName());

    String announcement = IceCream.config.getString(CONFIG_PATH + "announcement", "");
    if (announcement == null || announcement.isBlank()) {
      IceCream.logger.info("Skipping the first join announcement, as none is configured.");
    } else {
      Bukkit.broadcast(MiniMessage.miniMessage().deserialize(announcement, playerTag));
    }

    List<String> welcomeMessage = IceCream.config.getStringList(CONFIG_PATH + "welcome-message");
    if (welcomeMessage.isEmpty()) {
      IceCream.logger.info("Skipping the first join message, as none is configured.");
    } else {
      for (String line : welcomeMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(line, playerTag));
      }
    }
  }
}
