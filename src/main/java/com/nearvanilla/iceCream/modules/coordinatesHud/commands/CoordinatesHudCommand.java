package com.nearvanilla.iceCream.modules.coordinatesHud.commands;

import com.nearvanilla.iceCream.modules.coordinatesHud.CoordinatesHud;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.bukkit.annotation.specifier.AllowEmptySelection;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.survival.coordinateshud.Commands
 * Adaptations: messages are configurable MiniMessage templates.
 */
/**
 * Lets players turn the Coordinates HUD on and off for themselves, and lets staff turn it on and
 * off for other players.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see CoordinatesHud
 */
@SuppressWarnings("unused")
public class CoordinatesHudCommand {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final Component PLAYERS_ONLY =
      MM.deserialize("<red>This command can only be used by players.</red>");

  private final CoordinatesHud hud;
  private final Messages messages;

  /**
   * The messages the command sends, as MiniMessage templates.
   *
   * @param on sent to a player who turned their HUD on
   * @param off sent to a player who turned their HUD off
   * @param toggledFor sent to whoever toggled the HUD for other players, with {@code <count>}
   */
  public record Messages(String on, String off, String toggledFor) {}

  /**
   * Creates the command.
   *
   * @param hud the HUD to toggle
   * @param messages the messages to send
   */
  public CoordinatesHudCommand(CoordinatesHud hud, Messages messages) {
    this.hud = hud;
    this.messages = messages;
  }

  /** Turns the HUD on or off for the player running the command. */
  @Command("togglehud|thud")
  @CommandDescription("Toggle the coordinates HUD in your action bar.")
  @Permission("icecream.modules.coordinateshud.toggle")
  public void toggle(CommandSourceStack source) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    player.sendMessage(MM.deserialize(hud.toggle(player) ? messages.on() : messages.off()));
  }

  /** Turns the HUD on or off for each of the selected players, refusing a selection of none. */
  @Command("togglehud|thud player <players>")
  @CommandDescription("Toggle the coordinates HUD for other players.")
  @Permission("icecream.modules.coordinateshud.toggle.others")
  public void toggleOthers(
      CommandSourceStack source,
      @Argument("players") @AllowEmptySelection(false) MultiplePlayerSelector players) {
    Collection<Player> selected = players.values();
    for (Player player : selected) {
      hud.toggle(player);
    }
    source
        .getSender()
        .sendMessage(
            MM.deserialize(
                messages.toggledFor(),
                Placeholder.unparsed("count", Integer.toString(selected.size()))));
  }
}
