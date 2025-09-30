package com.nearvanilla.iceCream.modules.wanderful.commands;

import com.nearvanilla.iceCream.modules.wanderful.WandType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

/**
 * WanderfulCommand is a command that allows players to get a wand item.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public class WanderfulCommand {
  @Suggestions("wand_names")
  public List<String> suggestWands(CommandContext<CommandSourceStack> ctx, String input) {
    return Arrays.stream(WandType.values()).map(WandType::getId).toList();
  }

  @Command("wanderful give <wand>")
  @CommandDescription("Give wand to the player")
  @Permission("icecream.modules.wanderful.give")
  @SuppressWarnings("unused")
  public void giveWandCommand(
      CommandSourceStack commandSourceStack,
      @Argument(value = "wand", suggestions = "wand_names") WandType wand) {

    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("You must be a player to use this command.");
      return;
    }

    player.getInventory().addItem(wand.createItem());
    player.sendMessage(
        MiniMessage.miniMessage()
            .deserialize("You have been given the <gold>" + wand.getName() + "</gold> wand!"));
  }
}
