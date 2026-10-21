package com.nearvanilla.iceCream.modules.integrations;

import com.nearvanilla.iceCream.IceCream;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;

/**
 * Shared integration with squaremap to hide players from the map.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class SquaremapIntegration {
  private static Squaremap squaremapApi = null;

  /** Initializes the squaremap integration if squaremap is present. */
  public static void init() {
    squaremapApi = lookup();
    if (squaremapApi != null) {
      IceCream.logger.info("squaremap integration enabled.");
    }
  }

  /**
   * Looks up the squaremap API.
   *
   * @return the squaremap API, or null if squaremap is absent or not loaded
   */
  public static Squaremap lookup() {
    if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
      IceCream.logger.info("squaremap not found, skipping integration.");
      return null;
    }

    try {
      return SquaremapProvider.get();
    } catch (IllegalStateException e) {
      IceCream.logger.warning("squaremap found but its API is not loaded.");
      return null;
    }
  }

  /**
   * Hides a player from the squaremap.
   *
   * @param player the player to hide
   */
  public static void hidePlayer(Player player) {
    if (squaremapApi == null) return;

    squaremapApi.playerManager().hide(player.getUniqueId(), true);
  }

  /**
   * Shows a player on the squaremap.
   *
   * @param player the player to show
   */
  public static void showPlayer(Player player) {
    if (squaremapApi == null) return;

    squaremapApi.playerManager().show(player.getUniqueId(), true);
  }

  /**
   * Returns whether a player is currently visible on the squaremap.
   *
   * @param player the player to check
   * @return true if the player is visible, false if hidden
   */
  public static boolean isPlayerVisible(Player player) {
    if (squaremapApi == null) return true;

    return !squaremapApi.playerManager().hidden(player.getUniqueId());
  }
}
