package com.nearvanilla.iceCream.modules.playerSeen.commands;

import com.nearvanilla.iceCream.modules.playerSeen.PlayerSeenUtils;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * SeenCommand reports whether a player is currently online and, if not, how long ago they were last
 * seen on the server.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-25
 */
@SuppressWarnings("unused")
public class SeenCommand {

  private static final String NOT_FOUND_DEFAULT =
      "<gray>Player <red>could not be found</red>.</gray>";
  private static final String ONLINE_DEFAULT =
      "<gray>Player <white><player></white> <green>is currently online</green></gray>";
  private static final String NEVER_PLAYED_DEFAULT =
      "<gray>Player <white><player></white> <red>has never played before</red></gray>";
  private static final String LAST_SEEN_DEFAULT =
      "<gray>Player <white><player></white> <gray>was last seen <time></gray>";

  /**
   * Sends the sender a message describing when the given player was last seen. Offline players are
   * reported with a colour-coded duration, with the exact date and time shown on hover. Players
   * hidden by the spectator module are reported as offline.
   */
  @Command("seen <player>")
  @CommandDescription("Check when a player was last seen on the server.")
  @Permission("icecream.modules.playerseen.use")
  public void seenCommand(
      CommandSourceStack commandSourceStack,
      @Argument(value = "player", description = "The player to look up") OfflinePlayer player) {
    String playerName = player.getName();
    Player onlinePlayer = player.getPlayer();
    boolean hidden = onlinePlayer != null && SpectatorUtils.isInSpectatorMode(onlinePlayer);
    long lastSeen = player.getLastSeen();
    if (hidden) {
      // Spectator mode hides a player as though offline, so report when they were hidden.
      long hiddenSince = SpectatorUtils.getHiddenSince(onlinePlayer);
      lastSeen = hiddenSince > 0 ? hiddenSince : player.getLastLogin();
    }
    Component message;

    if (playerName == null) {
      message =
          PlayerSeenUtils.buildMessage(PlayerSeenUtils.getMessage("not-found", NOT_FOUND_DEFAULT));
    } else if (player.isOnline() && !hidden) {
      message =
          PlayerSeenUtils.buildMessage(
              PlayerSeenUtils.getMessage("online", ONLINE_DEFAULT).replace("<player>", playerName));
    } else if (lastSeen == 0) {
      message =
          PlayerSeenUtils.buildMessage(
              PlayerSeenUtils.getMessage("never-played", NEVER_PLAYED_DEFAULT)
                  .replace("<player>", playerName));
    } else {
      message =
          PlayerSeenUtils.buildMessage(
                  PlayerSeenUtils.getMessage("last-seen", LAST_SEEN_DEFAULT)
                      .replace("<player>", playerName)
                      .replace("<time>", PlayerSeenUtils.formatLastSeenTime(lastSeen)))
              .hoverEvent(
                  HoverEvent.showText(Component.text(PlayerSeenUtils.formatDate(lastSeen))));
    }

    commandSourceStack.getSender().sendMessage(message);
  }
}
