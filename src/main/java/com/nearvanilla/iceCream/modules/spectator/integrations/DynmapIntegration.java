package com.nearvanilla.iceCream.modules.spectator.integrations;

import com.nearvanilla.iceCream.IceCream;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.dynmap.DynmapCommonAPI;

/**
 * Integration with Dynmap to hide players in spectator mode from the map.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-03-18
 */
public class DynmapIntegration {
  private static DynmapCommonAPI dynmapApi = null;

  /** Initializes the Dynmap integration if Dynmap is present. */
  public static void init() {
    var plugin = Bukkit.getPluginManager().getPlugin("dynmap");
    if (plugin == null) {
      IceCream.logger.info("Dynmap not found, skipping integration.");
      return;
    }

    if (!(plugin instanceof DynmapCommonAPI dynmap)) {
      IceCream.logger.warning("Dynmap found but does not expose DynmapCommonAPI.");
      return;
    }

    dynmapApi = dynmap;
    IceCream.logger.info("Dynmap integration enabled.");
  }

  /**
   * Hides a player from the Dynmap.
   *
   * @param player the player to hide
   */
  public static void hidePlayer(Player player) {
    if (dynmapApi == null) return;

    dynmapApi.setPlayerVisiblity(player.getName(), false);
  }

  /**
   * Shows a player on the Dynmap.
   *
   * @param player the player to show
   */
  public static void showPlayer(Player player) {
    if (dynmapApi == null) return;

    dynmapApi.setPlayerVisiblity(player.getName(), true);
  }

  /**
   * Returns whether a player is currently visible on the Dynmap.
   *
   * @param player the player to check
   * @return true if the player is visible, false if hidden
   */
  public static boolean isPlayerVisible(Player player) {
    if (dynmapApi == null) return true;

    return dynmapApi.getPlayerVisbility(player.getName());
  }
}
