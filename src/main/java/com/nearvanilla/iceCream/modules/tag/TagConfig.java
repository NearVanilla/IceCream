package com.nearvanilla.iceCream.modules.tag;

import com.nearvanilla.iceCream.IceCream;

/**
 * Settings for the tag module, read from {@code modules.tag}.
 *
 * @param showMessages whether everyone is told when someone becomes "it" or stops playing while
 *     "it"
 * @param cooldownMs milliseconds a new "it" has to wait before they can tag someone
 * @param preventTagDestruction whether the tag item is kept from being dropped or used on mobs
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public record TagConfig(boolean showMessages, long cooldownMs, boolean preventTagDestruction) {
  private static final String CONFIG = "modules.tag.";
  private static final long MAX_COOLDOWN_SECONDS = 86_400;

  /**
   * Reads the settings from the plugin config. A cooldown outside 0 to 86400 seconds is clamped,
   * with a warning.
   *
   * @return the settings
   */
  public static TagConfig load() {
    return new TagConfig(
        IceCream.config.getBoolean(CONFIG + "show-messages", true),
        readCooldownSeconds() * 1_000,
        IceCream.config.getBoolean(CONFIG + "prevent-tag-destruction", true));
  }

  private static long readCooldownSeconds() {
    long seconds = IceCream.config.getLong(CONFIG + "cooldown-seconds", 0);
    if (seconds < 0 || seconds > MAX_COOLDOWN_SECONDS) {
      long clamped = Math.clamp(seconds, 0, MAX_COOLDOWN_SECONDS);
      IceCream.logger.warning(
          "modules.tag.cooldown-seconds must be between 0 and "
              + MAX_COOLDOWN_SECONDS
              + "; using "
              + clamped
              + ".");
      return clamped;
    }
    return seconds;
  }
}
