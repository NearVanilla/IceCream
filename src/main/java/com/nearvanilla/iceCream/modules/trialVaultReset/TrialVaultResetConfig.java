package com.nearvanilla.iceCream.modules.trialVaultReset;

import com.nearvanilla.iceCream.IceCream;

/**
 * Settings for the trial vault reset module, read from {@code modules.trialvaultreset}.
 *
 * @param delayMs milliseconds after looting before a player can reset a vault
 * @param resetMessage MiniMessage sent when a vault is reset for a player
 * @param waitingMessage MiniMessage sent while the delay has not passed; {@code <time>} is the time
 *     left
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 */
public record TrialVaultResetConfig(long delayMs, String resetMessage, String waitingMessage) {
  private static final String CONFIG = "modules.trialvaultreset.";
  private static final double DEFAULT_DELAY_MINUTES = 1440;
  private static final String DEFAULT_RESET_MESSAGE =
      "<green>This vault has been reset for you. Use your key again.</green>";
  private static final String DEFAULT_WAITING_MESSAGE =
      "<gray>You can reuse this vault in <white><time></white>.</gray>";

  /**
   * Reads the settings from the plugin config. An invalid delay falls back to the default and a
   * negative delay counts as 0, each with a warning.
   *
   * @return the settings
   */
  public static TrialVaultResetConfig load() {
    return new TrialVaultResetConfig(
        (long) (readDelayMinutes() * 60_000),
        IceCream.config.getString(CONFIG + "messages.reset", DEFAULT_RESET_MESSAGE),
        IceCream.config.getString(CONFIG + "messages.waiting", DEFAULT_WAITING_MESSAGE));
  }

  private static double readDelayMinutes() {
    Object value = IceCream.config.get(CONFIG + "reset-delay-minutes", DEFAULT_DELAY_MINUTES);
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      IceCream.logger.warning(
          "Invalid modules.trialvaultreset.reset-delay-minutes; using "
              + (long) DEFAULT_DELAY_MINUTES
              + ".");
      return DEFAULT_DELAY_MINUTES;
    }
    if (number.doubleValue() < 0) {
      IceCream.logger.warning(
          "modules.trialvaultreset.reset-delay-minutes is negative; vaults reset with no delay.");
      return 0;
    }
    return number.doubleValue();
  }
}
