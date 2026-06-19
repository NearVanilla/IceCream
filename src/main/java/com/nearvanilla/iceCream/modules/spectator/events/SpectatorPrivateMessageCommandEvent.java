package com.nearvanilla.iceCream.modules.spectator.events;

import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Cancels private message commands (e.g. /msg, /tell, /w, /whisper) involving players in spectator
 * mode so that the spectating player continues to appear as offline to standard players and the
 * spectating player does not leak their online state by sending private messages to others.
 *
 * <p>Two directions are enforced:
 *
 * <p>- Receiver-side: a standard player cannot /msg a spectating player; the sender sees an
 * offline-style message. - Sender-side: a spectating player cannot /msg a recipient that does not
 * have the spectator permission, including offline recipients.
 *
 * <p>Either side may bypass the block by holding the {@code icecream.modules.spectator} permission.
 *
 * <p>The set of intercepted command aliases and the offline-style component are read from {@link
 * SpectatorUtils} on every event so that the listener always reflects the current configuration,
 * even if the spectator module is re-initialised at runtime.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-06-14
 */
public class SpectatorPrivateMessageCommandEvent implements Listener {

  /**
   * Handles a player command. If the command is a configured private message alias and either the
   * target is in spectator mode or the sender is in spectator mode (and the other party lacks the
   * bypass permission), the command is cancelled and the sender receives the offline-style message.
   *
   * @param event the command preprocess event
   */
  @EventHandler
  public void onCommand(PlayerCommandPreprocessEvent event) {
    String message = event.getMessage();
    if (message == null || message.length() < 2 || message.charAt(0) != '/') {
      return;
    }

    String[] parts = message.substring(1).split(" ", 3);
    if (parts.length < 2 || parts[1].isEmpty()) {
      return;
    }

    String command = parts[0].toLowerCase();
    if (!SpectatorUtils.getBlockedPrivateMessageCommands().contains(command)) {
      return;
    }

    Player sender = event.getPlayer();
    Player target = findPlayer(parts[1]);

    boolean senderIsSpectator = SpectatorUtils.isInSpectatorMode(sender);
    boolean senderBypasses = SpectatorUtils.canBypassPrivateMessage(sender);
    boolean targetIsSpectator = target != null && SpectatorUtils.isInSpectatorMode(target);
    boolean targetBypasses = target != null && SpectatorUtils.canBypassPrivateMessage(target);

    if (targetIsSpectator && !senderBypasses) {
      event.setCancelled(true);
      sender.sendMessage(SpectatorUtils.getPrivateMessageOfflineMessage());
      return;
    }

    if (senderIsSpectator && !targetBypasses) {
      event.setCancelled(true);
      sender.sendMessage(SpectatorUtils.getPrivateMessageOfflineMessage());
    }
  }

  /**
   * Resolves a player name to an online {@link Player}, mirroring vanilla {@code /msg}'s behavior
   * of preferring an exact, case-insensitive match and falling back to a prefix match.
   *
   * @param name the name to resolve
   * @return the matching player, or {@code null} if no online player matches
   */
  private Player findPlayer(String name) {
    Player exact = Bukkit.getPlayerExact(name);
    if (exact != null) {
      return exact;
    }

    String lower = name.toLowerCase();
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (online.getName().toLowerCase().startsWith(lower)) {
        return online;
      }
    }
    return null;
  }
}
