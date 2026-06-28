package com.nearvanilla.iceCream.modules.modulesInfo.commands;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * ModulesInfoCommand displays all modules registered in the plugin along with their enabled or
 * disabled status.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-06-28
 */
public class ModulesInfoCommand {

  /**
   * Lists all modules and their enabled/disabled status. Enabled modules are shown in green,
   * disabled modules in red.
   *
   * @param commandSourceStack The command source stack.
   */
  @Command("modules")
  @CommandDescription("Lists all modules and their enabled status.")
  @Permission("icecream.modules.modulesinfo.modules")
  @SuppressWarnings("unused")
  public void modulesCommand(CommandSourceStack commandSourceStack) {
    MiniMessage mm = MiniMessage.miniMessage();
    commandSourceStack
        .getSender()
        .sendMessage(mm.deserialize("<bold><aqua>Modules:</aqua></bold>"));

    for (Module module : IceCream.modules) {
      String name = module.getClass().getSimpleName();
      String colorTag = module.isEnabled() ? "green" : "red";
      String status = module.isEnabled() ? "ENABLED" : "DISABLED";
      commandSourceStack
          .getSender()
          .sendMessage(
              mm.deserialize(
                  "<"
                      + colorTag
                      + "> • "
                      + name
                      + " - "
                      + status
                      + "</"
                      + colorTag
                      + ">"));
    }
  }
}