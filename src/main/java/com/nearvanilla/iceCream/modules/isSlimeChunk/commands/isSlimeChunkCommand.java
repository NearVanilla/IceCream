package com.nearvanilla.iceCream.modules.isSlimeChunk.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * isSlimeChunkCommand is a simple command that checks if the chunk the player is currently standing
 * in is a slime chunk or not and sends them a message
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-17
 */
public class isSlimeChunkCommand {
  protected final Component isSlimeChunkComponent =
      MiniMessage.miniMessage().deserialize("<green>This chunk is a slime chunk.</green>");
  protected final Component isNotSlimeChunkComponent =
      MiniMessage.miniMessage().deserialize("<red>This chunk is not a slime chunk.</red>");

  /**
   * The logic for the "isslimechunk" or "slime?" command, sending a message to the player who
   * executed it.
   *
   * @param commandSourceStack The command source stack, containing a sender, executor and location,
   *     where applicable.
   */
  @Command("isslimechunk|slime?")
  @CommandDescription("Checks if the current chunk is a slime chunk.")
  @Permission("icecream.modules.isslimechunk.self")
  @SuppressWarnings("unused")
  public void exampleCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }
    if (player.getLocation().getChunk().isSlimeChunk()) {
      player.sendMessage(isSlimeChunkComponent);
    } else {
      player.sendMessage(isNotSlimeChunkComponent);
    }
  }
}
