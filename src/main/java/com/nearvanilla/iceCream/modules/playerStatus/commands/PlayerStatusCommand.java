package com.nearvanilla.iceCream.modules.playerStatus.commands;

import com.nearvanilla.iceCream.modules.playerStatus.PlayerStatus;
import com.nearvanilla.iceCream.modules.playerStatus.PlayerStatusManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * Lets players set the status shown next to their name in the tab list.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-23
 */
@SuppressWarnings("unused")
public class PlayerStatusCommand {

  private static final String PERMISSION = "icecream.modules.playerstatus.use";
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final Component PLAYERS_ONLY =
      MINI_MESSAGE.deserialize("<red>This command can only be used by players.</red>");
  private static final Component SPECTATING =
      MINI_MESSAGE.deserialize("<red>You can't have a status while in spectator mode.</red>");
  private static final Component CLEARED =
      MINI_MESSAGE.deserialize("<gray>Your status has been cleared.</gray>");
  private static final Component NO_STATUS =
      MINI_MESSAGE.deserialize(
          "<gray>You have no status. Use <white>/status afk</white>, <white>/status dnd</white>"
              + " or <white>/status streaming</white> to set one.</gray>");

  private final PlayerStatusManager manager;

  public PlayerStatusCommand(PlayerStatusManager manager) {
    this.manager = manager;
  }

  /** Shows the player's current status. */
  @Command("status")
  @CommandDescription("Show your current status.")
  @Permission(PERMISSION)
  public void show(CommandSourceStack source) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    PlayerStatus status = manager.status(player);
    if (status == null) {
      player.sendMessage(NO_STATUS);
      return;
    }
    player.sendMessage(
        MINI_MESSAGE.deserialize(
            "<gray>Your status: <white><status></white></gray>",
            Placeholder.unparsed("status", status.displayName())));
  }

  /** Sets AFK. */
  @Command("status afk")
  @CommandDescription("Show yourself as AFK in the tab list.")
  @Permission(PERMISSION)
  public void afk(CommandSourceStack source) {
    set(source, PlayerStatus.AFK);
  }

  /** Sets do not disturb. */
  @Command("status dnd")
  @CommandDescription("Show yourself as do not disturb in the tab list.")
  @Permission(PERMISSION)
  public void doNotDisturb(CommandSourceStack source) {
    set(source, PlayerStatus.DO_NOT_DISTURB);
  }

  /** Sets streaming. */
  @Command("status streaming")
  @CommandDescription("Show yourself as streaming in the tab list.")
  @Permission(PERMISSION)
  public void streaming(CommandSourceStack source) {
    set(source, PlayerStatus.STREAMING);
  }

  /** Clears the player's status. */
  @Command("status clear")
  @CommandDescription("Clear your status in the tab list.")
  @Permission(PERMISSION)
  public void clear(CommandSourceStack source) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    manager.clear(player);
    player.sendMessage(CLEARED);
  }

  private void set(CommandSourceStack source, PlayerStatus status) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    if (PlayerStatusManager.isSpectating(player)) {
      player.sendMessage(SPECTATING);
      return;
    }
    boolean changed = manager.set(player, status);
    player.sendMessage(
        MINI_MESSAGE.deserialize(
            changed
                ? "<gray>Your status is now <white><status></white>. Use <white>/status clear</white>"
                    + " to remove it.</gray>"
                : "<gray>You are already shown as <white><status></white>.</gray>",
            Placeholder.unparsed("status", status.displayName())));
  }
}
