package com.nearvanilla.iceCream.modules.tag.commands;

import com.nearvanilla.iceCream.modules.tag.TagCounterSlot;
import com.nearvanilla.iceCream.modules.tag.TagGame;
import com.nearvanilla.iceCream.modules.tag.TagMessages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.hermitcraft.tag.Commands
 * Adaptations: added /tag optout and /tag optin.
 */
/**
 * The {@code /tag} commands: opting in and out, giving the tag, resetting the game and showing the
 * counter.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
@SuppressWarnings("unused")
public class TagCommand {
  private final TagGame game;

  /**
   * Creates the commands.
   *
   * @param game the tag game
   */
  public TagCommand(TagGame game) {
    this.game = game;
  }

  /**
   * Opts the player out of tag. If they are "it", "it" is cleared.
   *
   * @param source the command source
   */
  @Command("tag optout")
  @CommandDescription("Stop playing tag, so nobody can tag you.")
  @Permission("icecream.modules.tag.toggle")
  public void optOut(CommandSourceStack source) {
    Player player = requirePlayer(source);
    if (player != null) game.optOut(player);
  }

  /**
   * Opts the player back into tag.
   *
   * @param source the command source
   */
  @Command("tag optin")
  @CommandDescription("Play tag again after opting out.")
  @Permission("icecream.modules.tag.toggle")
  public void optIn(CommandSourceStack source) {
    Player player = requirePlayer(source);
    if (player != null) game.optIn(player);
  }

  /**
   * Makes a player "it", or the sender when no player is given.
   *
   * @param source the command source
   * @param target the player to make "it", or null for the sender
   */
  @Command("tag givetag [player]")
  @CommandDescription("Give the tag to yourself or another player.")
  @Permission("icecream.modules.tag.givetag")
  public void giveTag(CommandSourceStack source, @Argument("player") Player target) {
    CommandSender sender = source.getSender();
    Player player = target;
    if (player == null) {
      player = requirePlayer(source);
      if (player == null) return;
    }
    game.give(sender, player);
  }

  /**
   * Removes "it" from everyone.
   *
   * @param source the command source
   */
  @Command("tag reset")
  @CommandDescription("Remove \"it\" from whoever is it.")
  @Permission("icecream.modules.tag.reset")
  public void reset(CommandSourceStack source) {
    game.reset(source.getSender());
  }

  /**
   * Shows the "Times Tagged" counter in a scoreboard display slot, or hides it with {@code none}.
   *
   * @param source the command source
   * @param slot where to show the counter
   */
  @Command("tag counter <slot>")
  @CommandDescription("Show the tag counter in a scoreboard slot, or hide it.")
  @Permission("icecream.modules.tag.counter")
  public void counter(CommandSourceStack source, @Argument("slot") TagCounterSlot slot) {
    CommandSender sender = source.getSender();
    if (!game.showCounter(slot)) {
      sender.sendMessage(TagMessages.COUNTER_ALREADY);
    } else if (slot == TagCounterSlot.NONE) {
      sender.sendMessage(TagMessages.COUNTER_HIDDEN);
    } else {
      sender.sendMessage(TagMessages.counterShown(slot.name().toLowerCase(Locale.ROOT)));
    }
  }

  private static Player requirePlayer(CommandSourceStack source) {
    if (source.getSender() instanceof Player player) return player;
    source.getSender().sendMessage(TagMessages.PLAYERS_ONLY);
    return null;
  }
}
