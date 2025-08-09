package com.nearvanilla.iceCream.modules.example.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * ExampleCommand is a simple command that demonstrates how to create a command through Cloud Command Framework.
 * It simply sends a message to the player who executes it.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-09
 */
public class ExampleCommand {
  protected final Component exampleComponent =
      MiniMessage.miniMessage().deserialize("<green>This is an example command!</green>");

  @Command("example")
  @CommandDescription("This is an example command.")
  @Permission("icecream.modules.example.example")
  public void exampleCommand(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
    }
    Player player = (Player) commandSourceStack.getSender();
    player.sendMessage(exampleComponent);
  }
}
