package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.modules.spectator.AutoSpectator;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles auto spectator. Records when staff members quit, and when an opted-in staff member
 * rejoins after the configured time away, marks them as spectating so they join silently.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-15
 * @see AutoSpectator
 */
public class AutoSpectatorListener implements Listener {

  protected final Component autoSpectatorNotice =
      MiniMessage.miniMessage()
          .deserialize(
              "<aqua>👁 <yellow>You were away a while, so you joined in spectator mode."
                  + " </yellow><aqua>[<click:run_command:'/spec auto'>"
                  + "<hover:show_text:'Click to turn off auto spectator.'>"
                  + "Turn off"
                  + "</hover></click>]</aqua>");

  /**
   * Marks an opted-in staff member as spectating when they rejoin after enough time away. Runs at
   * LOW priority so that {@link SpectatorPlayerJoinEvent} sees the flag next and does the silent
   * join itself: it suppresses the join message, hides the player and calls {@code
   * SpectatorUtils.enableSpectator}, which saves their gamemode and location for when they leave.
   *
   * @param event the player join event
   */
  @EventHandler(priority = EventPriority.LOW)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (!AutoSpectator.shouldAutoSpectate(player, System.currentTimeMillis())) return;

    PersistentDataContainer data = player.getPersistentDataContainer();
    data.set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    // Others last saw this player when they quit, so /seen should report that time rather than
    // the moment they rejoined. enableSpectator keeps a hidden-since time that is already stored.
    Long lastQuit = data.get(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG);
    if (lastQuit != null) {
      SpectatorUtils.recordHiddenSince(player, lastQuit);
    }
    player.sendMessage(autoSpectatorNotice);
  }

  /**
   * Records when a staff member quits, so their time away can be measured when they rejoin.
   *
   * @param event the player quit event
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    AutoSpectator.recordQuit(event.getPlayer(), System.currentTimeMillis());
  }
}
