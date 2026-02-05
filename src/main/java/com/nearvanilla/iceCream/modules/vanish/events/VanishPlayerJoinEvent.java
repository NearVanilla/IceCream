package com.nearvanilla.iceCream.modules.vanish.events;

import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events for vanish. Hides all vanished players from the joining player, and if
 * the joining player is vanished, hides them from others and suppresses the join message.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class VanishPlayerJoinEvent implements Listener {

  protected final Component vanishReminderComponent =
      MiniMessage.miniMessage()
          .deserialize(
              "<gold>\uD83D\uDC41 <yellow>You are currently vanished. </yellow>"
                  + "<aqua>[<click:run_command:'/vanish'>"
                  + "<hover:show_text:'Click to toggle vanish.'>"
                  + "Toggle"
                  + "</hover></click>]</aqua>");

  /**
   * Handles player join events.
   *
   * @param event the player join event
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    // Hide all vanished players from this player
    VanishUtils.hideVanishedPlayersFrom(player);

    // If the joining player is vanished, hide them from others and suppress join message
    if (VanishUtils.isVanished(player)) {
      event.joinMessage(null);
      VanishUtils.hidePlayer(player, false);
      player.sendMessage(vanishReminderComponent);
    }
  }
}
