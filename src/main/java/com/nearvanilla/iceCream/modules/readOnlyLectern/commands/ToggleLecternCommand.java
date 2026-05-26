package com.nearvanilla.iceCream.modules.readOnlyLectern.commands;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

public class ToggleLecternCommand {

  private static final Component NOT_LOOKING_AT_LECTERN =
      LecternUtils.buildMessage("<red>You are not looking at a valid lectern.</red>");
  private static final Component NOT_ELIGIBLE =
      LecternUtils.buildMessage("<red>This lectern does not contain a player-authored book.</red>");
  private static final Component NOW_READ_ONLY =
      LecternUtils.buildMessage("<green>This lectern is now read-only.</green>");
  private static final Component NO_LONGER_READ_ONLY =
      LecternUtils.buildMessage("<green>This lectern is no longer read-only.</green>");

  @Command("togglelectern")
  @CommandDescription("Toggle the read-only state of a lectern you are looking at.")
  @Permission("icecream.modules.readonlylectern.toggle")
  @SuppressWarnings("unused")
  public void toggleLecternCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack
          .getSender()
          .sendMessage(LecternUtils.buildMessage("<red>Only players can use this command.</red>"));
      return;
    }

    int maxDistance = IceCream.config.getInt("modules.readonlylectern.max-distance", 5);
    Block lectern = LecternUtils.getTargetLectern(player, maxDistance);

    if (lectern == null) {
      player.sendMessage(NOT_LOOKING_AT_LECTERN);
      return;
    }

    if (!LecternUtils.hasPlayerAuthoredBook(lectern)) {
      player.sendMessage(NOT_ELIGIBLE);
      return;
    }

    boolean isCurrentlyReadOnly = LecternUtils.isReadOnly(lectern);
    LecternUtils.setReadOnly(lectern, !isCurrentlyReadOnly);

    if (!isCurrentlyReadOnly) {
      player.sendMessage(NOW_READ_ONLY);
    } else {
      player.sendMessage(NO_LONGER_READ_ONLY);
    }
  }
}
