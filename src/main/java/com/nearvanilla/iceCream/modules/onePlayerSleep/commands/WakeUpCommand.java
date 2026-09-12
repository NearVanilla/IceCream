package com.nearvanilla.iceCream.modules.onePlayerSleep.commands;

import com.nearvanilla.iceCream.modules.onePlayerSleep.OnePlayerSleepModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

public class WakeUpCommand {
  private final OnePlayerSleepModule module;

  public WakeUpCommand(OnePlayerSleepModule module) {
    this.module = module;
  }

  @Command("oneplayersleep wake <token>")
  @CommandDescription("Wake sleeping players and keep the night.")
  public void wakeUp(CommandSourceStack stack, @Argument("token") String token) {
    if (!(stack.getSender() instanceof Player player)) {
      stack.getSender().sendMessage(Component.text("Only players can use this command."));
      return;
    }
    module.requestWakeUp(player, token);
  }
}
