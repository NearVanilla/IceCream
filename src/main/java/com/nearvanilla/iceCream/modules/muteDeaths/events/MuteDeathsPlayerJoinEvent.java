package com.nearvanilla.iceCream.modules.muteDeaths.events;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * MuteDeathsPlayerJoinEvent is a simple event listener that reminds the player their death messages
 * are muted. It listens for the PlayerJoinEvent and sends a welcome message to the player when they
 * join the server.
 *
 * @author 105hua
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-11
 */
public class MuteDeathsPlayerJoinEvent implements Listener {

  /** The welcome message component that is sent to players upon joining. */
  protected final Component muteDeathsMutedReminderComponent =
      MiniMessage.miniMessage()
          .deserialize(
              "<gold>\uD83D\uDD15 <yellow>Your death messages are muted. </yellow>"
                  + "<aqua>[<click:run_command:'/mutedeaths'>"
                  + "<hover:show_text:'Click to toggle your death message visibility.'>"
                  + "Toggle"
                  + "</hover></click>]</aqua>");

  /**
   * Handles the PlayerJoinEvent. When a player joins the server, this method is called, sending a
   * welcome message to the player.
   *
   * @param event The PlayerJoinEvent that is triggered when a player joins the server.
   * @see PlayerJoinEvent
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    PersistentDataContainer playerContainer = player.getPersistentDataContainer();
    if (Boolean.TRUE.equals(
        playerContainer.get(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN))) {
      player.sendMessage(muteDeathsMutedReminderComponent);
    }
  }
}
