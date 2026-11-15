package com.nearvanilla.iceCream.modules.spectator;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Auto spectator lets staff members opt in to joining in spectator mode after being offline for a
 * configured time, so they can log in without anyone seeing them arrive. Each staff member's choice
 * and their last quit time are kept in their persistent data container.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-15
 */
public final class AutoSpectator {

  /** Permission required to use and benefit from auto spectator. */
  public static final String PERMISSION = "icecream.modules.spectator";

  /** Offline time used when the configured value is missing or invalid. */
  public static final long DEFAULT_OFFLINE_MINUTES = 30;

  private static boolean featureEnabled = true;
  private static long offlineMillis = DEFAULT_OFFLINE_MINUTES * 60_000L;

  private AutoSpectator() {}

  /** Loads the auto spectator settings from the plugin config. */
  public static void init() {
    configure(
        IceCream.config.getBoolean("modules.spectator.auto-spectator.enabled", true),
        IceCream.config.getLong(
            "modules.spectator.auto-spectator.offline-minutes", DEFAULT_OFFLINE_MINUTES));
  }

  /**
   * Sets the auto spectator settings. A negative offline time falls back to {@link
   * #DEFAULT_OFFLINE_MINUTES}.
   *
   * @param enabled whether auto spectator is turned on for the server
   * @param offlineMinutes how long a player must be offline before they rejoin in spectator mode
   */
  public static void configure(boolean enabled, long offlineMinutes) {
    if (offlineMinutes < 0) {
      if (IceCream.logger != null) {
        IceCream.logger.warning(
            "Invalid auto spectator offline-minutes '"
                + offlineMinutes
                + "', using "
                + DEFAULT_OFFLINE_MINUTES);
      }
      offlineMinutes = DEFAULT_OFFLINE_MINUTES;
    }
    featureEnabled = enabled;
    offlineMillis = offlineMinutes * 60_000L;
  }

  /**
   * Returns whether auto spectator is turned on for the server.
   *
   * @return true if the feature is enabled in config
   */
  public static boolean isFeatureEnabled() {
    return featureEnabled;
  }

  /**
   * Returns how long a player must be offline before they rejoin in spectator mode.
   *
   * @return the offline threshold in milliseconds
   */
  public static long getOfflineMillis() {
    return offlineMillis;
  }

  /**
   * Returns whether the given player has opted in to auto spectator.
   *
   * @param player the player to check
   * @return true if the player has turned auto spectator on
   */
  public static boolean isAutoEnabled(Player player) {
    return Boolean.TRUE.equals(
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.AUTO_SPECTATOR_KEY, PersistentDataType.BOOLEAN));
  }

  /**
   * Flips the given player's auto spectator choice.
   *
   * @param player the player whose choice to flip
   * @return the new state, true if auto spectator is now on
   */
  public static boolean toggleAuto(Player player) {
    boolean enabled = !isAutoEnabled(player);
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.AUTO_SPECTATOR_KEY, PersistentDataType.BOOLEAN, enabled);
    return enabled;
  }

  /**
   * Records when a staff member quit, so their time away can be measured when they rejoin. Players
   * without {@link #PERMISSION} are ignored.
   *
   * @param player the player who quit
   * @param nowMillis the current time in epoch milliseconds
   */
  public static void recordQuit(Player player, long nowMillis) {
    if (!player.hasPermission(PERMISSION)) return;

    player
        .getPersistentDataContainer()
        .set(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG, nowMillis);
  }

  /**
   * Records a quit time for every online staff member. Called when the module shuts down, because
   * Paper disables plugins before it removes players, so no quit event reaches the listener.
   *
   * @param nowMillis the current time in epoch milliseconds
   */
  public static void recordQuitForOnlinePlayers(long nowMillis) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      recordQuit(player, nowMillis);
    }
  }

  /**
   * Returns whether the given player should join in spectator mode. This is true only when the
   * feature is enabled, the player has {@link #PERMISSION}, has opted in, is not already in
   * spectator or staff mode, and has been offline for at least the configured time.
   *
   * @param player the joining player
   * @param nowMillis the current time in epoch milliseconds
   * @return true if the player should be put into spectator mode
   */
  public static boolean shouldAutoSpectate(Player player, long nowMillis) {
    if (!featureEnabled) return false;
    if (!player.hasPermission(PERMISSION)) return false;
    if (!isAutoEnabled(player)) return false;
    if (SpectatorUtils.isInSpectatorMode(player)) return false;
    if (StaffModeUtils.isInStaffMode(player)) return false;

    PersistentDataContainer data = player.getPersistentDataContainer();
    Long lastQuit = data.get(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG);
    if (lastQuit == null || lastQuit > nowMillis) return false;

    return nowMillis - lastQuit >= offlineMillis;
  }
}
