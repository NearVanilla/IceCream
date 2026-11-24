package com.nearvanilla.iceCream.modules.coordinatesHud;

import com.nearvanilla.iceCream.utils.ActionBarUtils;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.survival.coordinateshud.HUDRunnable
 * Adaptations: refreshed on the main thread rather than asynchronously, and paused while another
 *   module uses the action bar.
 */
/**
 * Tracks which online players see the Coordinates HUD and refreshes it in their action bar.
 *
 * <p>When another module shows a player an action bar message through {@link ActionBarUtils}, the
 * HUD skips that player until the message is the given pause old, so the message can be read. A
 * message that keeps being refreshed, such as a countdown, keeps the HUD paused until it stops.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see HudLine
 * @see HudPreference
 */
public final class CoordinatesHud {
  private final Set<UUID> shown = new HashSet<>();
  private final HudPreference preference;
  private final HudLine line;
  private final boolean enabledByDefault;
  private final long pauseMillis;

  /**
   * Creates the HUD.
   *
   * @param preference where players' choices are stored
   * @param line the line shown in the action bar
   * @param enabledByDefault whether players with no choice stored see the HUD
   * @param pauseMillis how long the HUD steps aside after another module's action bar message, in
   *     milliseconds; 0 never steps aside
   */
  public CoordinatesHud(
      HudPreference preference, HudLine line, boolean enabledByDefault, long pauseMillis) {
    this.preference = preference;
    this.line = line;
    this.enabledByDefault = enabledByDefault;
    this.pauseMillis = pauseMillis;
  }

  /**
   * Starts showing the HUD to a player who joined, if they want it.
   *
   * @param player the player
   */
  public void load(Player player) {
    if (preference.resolve(player.getPersistentDataContainer(), enabledByDefault)) {
      shown.add(player.getUniqueId());
    }
  }

  /**
   * Stops tracking a player who left. Their stored choice is kept.
   *
   * @param player the player
   */
  public void forget(Player player) {
    shown.remove(player.getUniqueId());
  }

  /**
   * Tells whether a player currently sees the HUD.
   *
   * @param player the player
   * @return whether the HUD is shown to them
   */
  public boolean isShown(Player player) {
    return shown.contains(player.getUniqueId());
  }

  /**
   * Turns the HUD on or off for a player and stores the choice. Turning it off clears the action
   * bar straight away.
   *
   * @param player the player
   * @return whether the HUD is now shown to them
   */
  public boolean toggle(Player player) {
    boolean show = !isShown(player);
    preference.set(player.getPersistentDataContainer(), show);
    if (show) {
      shown.add(player.getUniqueId());
    } else {
      shown.remove(player.getUniqueId());
      player.sendActionBar(Component.empty());
    }
    return show;
  }

  /**
   * Refreshes the HUD of every player who sees it, skipping players whose action bar another module
   * is using and dropping players who are no longer online.
   */
  public void tick() {
    Iterator<UUID> iterator = shown.iterator();
    while (iterator.hasNext()) {
      Player player = Bukkit.getPlayer(iterator.next());
      if (player == null) {
        iterator.remove();
        continue;
      }
      if (ActionBarUtils.sentWithin(player, pauseMillis)) {
        continue;
      }
      Location location = player.getLocation();
      player.sendActionBar(
          line.render(
              location.getBlockX(),
              location.getBlockY(),
              location.getBlockZ(),
              location.getYaw(),
              player.getWorld().getTime()));
    }
  }

  /** Stops tracking everyone, leaving stored choices as they are. */
  public void clear() {
    shown.clear();
  }
}
