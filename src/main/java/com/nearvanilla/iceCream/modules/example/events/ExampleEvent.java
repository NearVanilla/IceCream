package com.nearvanilla.iceCream.modules.example.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * ExampleEvent is a simple event listener that demonstrates how to create an event. It listens for
 * the PlayerJoinEvent and sends a welcome message to the player when they join the server.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-09
 */
public class ExampleEvent implements Listener {

  /** The welcome message component that is sent to players upon joining. */
  protected final Component joinedComponent =
      MiniMessage.miniMessage()
          .deserialize("<rainbow>Welcome to the server! This is an example event.</rainbow>");

  /**
   * Handles the PlayerJoinEvent. When a player joins the server, this method is called, sending a
   * welcome message to the player.
   *
   * @param event The PlayerJoinEvent that is triggered when a player joins the server.
   * @see PlayerJoinEvent
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    event.getPlayer().sendMessage(joinedComponent);
  }
}
