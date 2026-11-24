package com.nearvanilla.iceCream.modules.tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original files: me.machinemaker.papertweaks.modules.hermitcraft.tag.TagManager, PlayerListener
 *   and Commands
 * Adaptations: players can opt out, "it" is shown again when the player logs back in, and a reset
 *   also reaches players who are offline.
 */
/**
 * The rules of the tag game. Whoever is "it" hits another player with the tag item to make them
 * "it" instead. Players are in the game by default and can opt out, after which they cannot be
 * tagged or tag anyone.
 *
 * <p>Players who are "it" are on the red {@value #TEAM_NAME} team, have a red name in chat and in
 * the player list, and carry the tag item. Each time a player becomes "it" their score on the
 * {@value #OBJECTIVE_NAME} objective goes up by one. Both names are the ones PaperTweaks used, so
 * the counter and the team carry over.
 *
 * <p>"It" stays with a player who logs off. The team doubles as the record of who is "it" while
 * offline: a player who is "it" but no longer on the team when they log in was reset while away.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class TagGame {
  /** The scoreboard team players who are "it" are on. */
  public static final String TEAM_NAME = "tag/redcolor";

  /** The scoreboard objective counting how often each player has been "it". */
  public static final String OBJECTIVE_NAME = "tg_timesTagged";

  private final TagConfig config;
  private final TagStore store;
  private final TagItem item;
  private final Audience everyone;
  private final LongSupplier clock;
  private final Team team;
  private final Objective counter;

  /**
   * Creates the game, registering its team and objective on the scoreboard if they are missing.
   *
   * @param config the settings
   * @param store the per-player state
   * @param item the tag item
   * @param scoreboard the scoreboard for the team and counter, normally the main one
   * @param everyone who announcements go to, normally the server
   * @param clock the current time in epoch milliseconds
   */
  public TagGame(
      TagConfig config,
      TagStore store,
      TagItem item,
      Scoreboard scoreboard,
      Audience everyone,
      LongSupplier clock) {
    this.config = config;
    this.store = store;
    this.item = item;
    this.everyone = everyone;
    this.clock = clock;
    Team existingTeam = scoreboard.getTeam(TEAM_NAME);
    this.team = existingTeam != null ? existingTeam : scoreboard.registerNewTeam(TEAM_NAME);
    this.team.color(NamedTextColor.RED);
    Objective existingCounter = scoreboard.getObjective(OBJECTIVE_NAME);
    this.counter =
        existingCounter != null
            ? existingCounter
            : scoreboard.registerNewObjective(
                OBJECTIVE_NAME, Criteria.DUMMY, Component.text("Times Tagged"));
  }

  /**
   * Gets the settings.
   *
   * @return the settings
   */
  public TagConfig config() {
    return config;
  }

  /**
   * Checks whether an item is the tag item.
   *
   * @param stack the item, may be null
   * @return true if it is the tag item
   */
  public boolean isTagItem(ItemStack stack) {
    return item.isTagItem(stack);
  }

  /**
   * Makes a player "it" on someone's command, without taking it from whoever already is.
   *
   * @param from who gave the tag, told when it cannot be given
   * @param player the player to make "it"
   * @return true if the player is now "it"
   */
  public boolean give(CommandSender from, Player player) {
    if (!canBecomeIt(from, player)) return false;
    makeIt(player);
    announce(TagMessages.tagged(from.getName(), player.getName()));
    return true;
  }

  /**
   * Handles one player hitting another with the tag item. If the tagger is "it", off cooldown, and
   * the target can be tagged, "it" moves to the target.
   *
   * @param tagger the player holding the tag item
   * @param target the player who was hit
   * @return true if the target is now "it"
   */
  public boolean tag(Player tagger, Player target) {
    if (!store.isIt(tagger) || store.isOptedOut(tagger)) return false;
    long until = store.cooldownUntil(tagger);
    long now = clock.getAsLong();
    if (until > now) {
      tagger.sendMessage(TagMessages.cooldown(formatTimeLeft(until - now)));
      return false;
    }
    if (!canBecomeIt(tagger, target)) return false;
    makeIt(target);
    removeIt(tagger);
    announce(TagMessages.tagged(tagger.getName(), target.getName()));
    return true;
  }

  /**
   * Opts a player out of the game. If they are "it", nobody takes over: "it" is cleared and, when
   * messages are on, everyone is told.
   *
   * @param player the player
   * @return true if the player was playing before
   */
  public boolean optOut(Player player) {
    if (store.isOptedOut(player)) {
      player.sendMessage(TagMessages.ALREADY_OPTED_OUT);
      return false;
    }
    store.setOptedOut(player, true);
    if (store.isIt(player)) {
      removeIt(player);
      announce(TagMessages.stoppedWhileIt(player.getName()));
    }
    player.sendMessage(TagMessages.OPTED_OUT);
    return true;
  }

  /**
   * Opts a player back into the game.
   *
   * @param player the player
   * @return true if the player had opted out before
   */
  public boolean optIn(Player player) {
    if (!store.isOptedOut(player)) {
      player.sendMessage(TagMessages.ALREADY_OPTED_IN);
      return false;
    }
    store.setOptedOut(player, false);
    player.sendMessage(TagMessages.OPTED_IN);
    return true;
  }

  /**
   * Checks whether a player has opted out of the game.
   *
   * @param player the player
   * @return true if the player is not playing
   */
  public boolean isOptedOut(Player player) {
    return store.isOptedOut(player);
  }

  /**
   * Checks whether a player is "it".
   *
   * @param player the player
   * @return true if the player is "it"
   */
  public boolean isIt(Player player) {
    return store.isIt(player);
  }

  /**
   * Removes "it" from everyone, online or not. Offline players lose it fully when they next log in.
   *
   * @param sender who is told which players were removed
   * @return how many players were removed
   */
  public int reset(CommandSender sender) {
    List<String> removed = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!store.isIt(player)) continue;
      removeIt(player);
      removed.add(player.getName());
    }
    // Whoever is left on the team is "it" while offline.
    for (String entry : new ArrayList<>(team.getEntries())) {
      team.removeEntry(entry);
      removed.add(entry);
    }
    if (removed.isEmpty()) {
      sender.sendMessage(TagMessages.RESET_NONE);
    }
    for (String name : removed) {
      sender.sendMessage(TagMessages.resetRemoved(name));
    }
    return removed.size();
  }

  /**
   * Shows the "Times Tagged" counter in a display slot, or hides it.
   *
   * @param slot where to show it
   * @return false if it was already shown there
   */
  public boolean showCounter(TagCounterSlot slot) {
    DisplaySlot current = counter.getDisplaySlot();
    if (current == slot.slot()) return false;
    if (slot.slot() == null) {
      counter.getScoreboard().clearSlot(current);
    } else {
      counter.setDisplaySlot(slot.slot());
    }
    return true;
  }

  /**
   * Brings a player's state up to date as they log in: takes over PaperTweaks' "it" status, clears
   * "it" if they were reset or opted out while away, and otherwise shows them as "it" again.
   *
   * @param player the player who logged in
   */
  public void restore(Player player) {
    if (store.takeLegacyIt(player) && !store.isIt(player)) {
      store.setIt(player, Long.MIN_VALUE);
    }
    if (!store.isIt(player)) return;
    if (store.isOptedOut(player) || !team.hasEntry(player.getName())) {
      removeIt(player);
      return;
    }
    showAsIt(player);
  }

  private boolean canBecomeIt(CommandSender from, Player player) {
    if (store.isOptedOut(player)) {
      from.sendMessage(TagMessages.notPlaying(player.getName()));
      return false;
    }
    if (store.isAfk(player)) {
      from.sendMessage(TagMessages.AFK);
      return false;
    }
    if (store.isIt(player)) {
      from.sendMessage(TagMessages.alreadyIt(player.getName()));
      return false;
    }
    return true;
  }

  private void makeIt(Player player) {
    team.addEntry(player.getName());
    Score score = counter.getScore(player.getName());
    score.setScore(score.getScore() + 1);
    store.setIt(player, clock.getAsLong() + config.cooldownMs());
    showAsIt(player);
    item.giveTo(player);
  }

  private void removeIt(Player player) {
    team.removeEntry(player.getName());
    player.displayName(null);
    player.playerListName(null);
    store.clearIt(player);
    item.removeFrom(player);
  }

  private void showAsIt(Player player) {
    Component current = player.displayName();
    if (current == null) current = Component.text(player.getName());
    Component name = current.color(NamedTextColor.RED);
    player.displayName(name);
    player.playerListName(name);
  }

  private void announce(Component message) {
    if (config.showMessages()) everyone.sendMessage(message);
  }

  /**
   * Formats time left as {@code H:MM:SS}, as PaperTweaks did, rounding up to the next second.
   *
   * @param ms the time left in milliseconds
   * @return the formatted time
   */
  static String formatTimeLeft(long ms) {
    long seconds = Math.ceilDiv(Math.max(0, ms), 1_000);
    return String.format("%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60);
  }
}
