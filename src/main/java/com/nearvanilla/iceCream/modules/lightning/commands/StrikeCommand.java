package com.nearvanilla.iceCream.modules.lightning.commands;

import com.nearvanilla.iceCream.IceCream;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * StrikeCommand is a command that allows players to strike another player with lightning, unless
 * fate decides otherwise.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-11
 */
public class StrikeCommand {

  protected final Component noPlayerComponent =
      MiniMessage.miniMessage().deserialize("<red>No player found with that name.</red>");

  protected final Component victimStrikeComponent =
      MiniMessage.miniMessage().deserialize("<yellow>You have been struck by lightning!</yellow>");

  protected final Component senderStrikeComponent =
      MiniMessage.miniMessage().deserialize("<green>Success!</green>");

  protected final Component strikeFailComponent =
      MiniMessage.miniMessage()
          .deserialize("<red>Fate has decided that you cannot strike this player.</red>");

  /**
   * The logic for the strike command, striking a player with lightning, unless fate decides
   * otherwise.
   *
   * @param commandSourceStack The command source stack, containing a sender, executor and location,
   *     where applicable.
   * @param targetPlayer The player to strike with lightning.
   */
  @Command("strike <player>")
  @CommandDescription("Strike a player with lighting... unless fate decides otherwise.")
  @Permission("icecream.modules.lightning.strike")
  @SuppressWarnings("unused")
  public void strikeCommand(
      CommandSourceStack commandSourceStack, @Argument(value = "player") Player targetPlayer) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("You must be a player to use this command.");
      return;
    }
    if (targetPlayer == null) {
      commandSourceStack.getSender().sendMessage(noPlayerComponent);
      return;
    }
    if (targetPlayer.isOnline()) {
      if (targetPlayer.equals(player)) {
        commandSourceStack
            .getSender()
            .sendMessage(
                MiniMessage.miniMessage().deserialize("<red>You cannot strike yourself!</red>"));
        return;
      }
    } else {
      commandSourceStack
          .getSender()
          .sendMessage(
              MiniMessage.miniMessage().deserialize("<red>This player is not online.</red>"));
      return;
    }
    double chance = IceCream.config.getDouble("modules.lightning.chance", 0.5);
    if (Math.random() < chance) {
      targetPlayer.getWorld().strikeLightning(targetPlayer.getLocation());
      targetPlayer.sendMessage(victimStrikeComponent);
      commandSourceStack.getSender().sendMessage(senderStrikeComponent);
    } else {
      commandSourceStack.getSender().sendMessage(strikeFailComponent);
    }
  }
}
