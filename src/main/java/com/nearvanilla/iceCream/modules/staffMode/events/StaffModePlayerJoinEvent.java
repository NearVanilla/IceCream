package com.nearvanilla.iceCream.modules.staffMode.events;

import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Restores flight and reminds players whose persisted staff mode is enabled when they join.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-11-25
 */
public class StaffModePlayerJoinEvent implements Listener {

  /** The reminder message component that is sent to players upon joining. */
  protected final Component staffModeMutedReminderComponent =
      MiniMessage.miniMessage()
          .deserialize(
              "<gold>\uD83D\uDD15 <yellow>Staff mode is enabled. </yellow>"
                  + "<aqua>[<click:run_command:'/staffmode'>"
                  + "<hover:show_text:'Click to toggle staff mode.'>"
                  + "Toggle"
                  + "</hover></click>]</aqua>");

  /**
   * Restores staff flight without replacing the inventory or location saved before staff mode.
   *
   * @param event The PlayerJoinEvent that is triggered when a player joins the server.
   * @see PlayerJoinEvent
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    PersistentDataContainer playerContainer = player.getPersistentDataContainer();
    if (Boolean.TRUE.equals(
        playerContainer.get(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN))) {
      player.setAllowFlight(true);
      player.setFlying(true);
      player.sendMessage(staffModeMutedReminderComponent);
    }
  }
}
