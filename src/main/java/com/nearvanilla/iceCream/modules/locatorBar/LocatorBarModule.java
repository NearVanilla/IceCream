package com.nearvanilla.iceCream.modules.locatorBar;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.locatorBar.commands.LocatorBarCommand;
import org.bukkit.NamespacedKey;

/**
 * Registers the locator bar toggle and owns its persistent player preference.
 *
 * <p>Disabling the bar reduces the player's waypoint receive range to the configured positive
 * minimum. A positive value avoids the vanilla bug that also stops the player from transmitting
 * their own waypoint when the receive range is zero. The player's previous base receive range is
 * stored and restored when they enable the bar again.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-09-13
 * @see Module
 * @see LocatorBarCommand
 */
public class LocatorBarModule implements Module {

  /** Persistent-data key containing a player's receive range from before they disabled the bar. */
  public static NamespacedKey PREVIOUS_RECEIVE_RANGE_KEY;

  private static final double MINIMUM_RECEIVE_RANGE = 0.1;
  private static final double MAXIMUM_RECEIVE_RANGE = 60_000_000;

  private boolean enabled;

  /** Initializes the persistent-data keys used by this module. */
  private static void initKeys() {
    if (PREVIOUS_RECEIVE_RANGE_KEY == null) {
      PREVIOUS_RECEIVE_RANGE_KEY =
          new NamespacedKey(IceCream.instance, "locatorbar.previous_range");
    }
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.locatorbar.enabled", false);
  }

  /**
   * Returns the configured receive range used while the locator bar is disabled.
   *
   * <p>The value is constrained to {@code 0.1} through {@code 60000000}. Non-finite values fall
   * back to {@code 0.1}.
   *
   * @return the validated waypoint receive range
   */
  public static double getDisabledReceiveRange() {
    double configuredRange =
        IceCream.config.getDouble(
            "modules.locatorbar.disabled-receive-range", MINIMUM_RECEIVE_RANGE);
    if (!Double.isFinite(configuredRange)) {
      return MINIMUM_RECEIVE_RANGE;
    }
    return Math.clamp(configuredRange, MINIMUM_RECEIVE_RANGE, MAXIMUM_RECEIVE_RANGE);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new LocatorBarCommand());
  }

  @Override
  public void registerEvents() {}

  @Override
  public void register() {
    initKeys();
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        enabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register LocatorBar module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("LocatorBar module has been enabled.");
    } else {
      IceCream.logger.info("LocatorBar module is disabled.");
    }
  }
}
