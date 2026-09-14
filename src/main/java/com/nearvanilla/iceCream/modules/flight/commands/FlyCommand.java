package com.nearvanilla.iceCream.modules.flight.commands;

import com.nearvanilla.iceCream.modules.flight.FlightUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

@SuppressWarnings("unused")
public class FlyCommand {
  private static final Component ENABLED =
      MiniMessage.miniMessage()
          .deserialize("<green>Flight enabled. Double-tap jump to fly.</green>");
  private static final Component DISABLED =
      MiniMessage.miniMessage().deserialize("<red>Flight disabled.</red>");
  private static final Component PLAYERS_ONLY =
      MiniMessage.miniMessage().deserialize("<red>This command can only be used by players.</red>");
  private static final Component OTHER_FLIGHT =
      MiniMessage.miniMessage()
          .deserialize(
              "<yellow>Your current game mode or staff mode already provides flight.</yellow>");

  @Command("fly")
  @CommandDescription("Toggle flight for yourself. Your preference is saved between sessions.")
  @Permission(FlightUtils.PERMISSION)
  public void fly(CommandSourceStack source) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    if (FlightUtils.hasOtherFlight(player)) {
      player.sendMessage(OTHER_FLIGHT);
      return;
    }
    boolean enabled = !FlightUtils.isEnabled(player);
    FlightUtils.setEnabled(player, enabled);
    player.sendMessage(enabled ? ENABLED : DISABLED);
  }
}
