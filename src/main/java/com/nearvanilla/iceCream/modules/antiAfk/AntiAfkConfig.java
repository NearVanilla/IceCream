package com.nearvanilla.iceCream.modules.antiAfk;

import com.nearvanilla.iceCream.IceCream;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

/**
 * The anti-AFK settings from {@code modules.antiafk} in the config, checked once when the module is
 * enabled.
 *
 * @param timeoutMs how long without activity before a captcha appears
 * @param answerMs how long the player has to type the code
 * @param codeLength the number of characters in a code
 * @param minRotationDegrees how far the camera must turn to count as activity
 * @param announceKick whether everyone is told when a player is kicked
 * @param sound the sound played with a captcha, or null for none
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public record AntiAfkConfig(
    long timeoutMs,
    long answerMs,
    int codeLength,
    double minRotationDegrees,
    boolean announceKick,
    Sound sound) {
  private static final String CONFIG = "modules.antiafk.";

  /**
   * Reads the settings from the plugin config.
   *
   * @return the settings, or null when a value is invalid (the reason is logged)
   */
  public static AntiAfkConfig load() {
    Double timeoutMinutes = readNumber("timeout-minutes", 300, 0.25, Double.MAX_VALUE);
    Double answerSeconds = readNumber("answer-seconds", 60, 15, 600);
    Double codeLength = readNumber("code-length", 6, 4, 12);
    Double rotation = readNumber("min-rotation-degrees", 20, 1, 180);
    if (timeoutMinutes == null || answerSeconds == null || codeLength == null || rotation == null) {
      return null;
    }
    return new AntiAfkConfig(
        (long) (timeoutMinutes * 60_000),
        (long) (answerSeconds * 1_000),
        codeLength.intValue(),
        rotation,
        IceCream.config.getBoolean(CONFIG + "announce-kick", true),
        readSound());
  }

  /** Reads a number from the module config, logging and returning null when it is invalid. */
  private static Double readNumber(String key, double fallback, double min, double max) {
    Object value = IceCream.config.get(CONFIG + key, fallback);
    if (!(value instanceof Number number)
        || !Double.isFinite(number.doubleValue())
        || number.doubleValue() < min
        || number.doubleValue() > max) {
      IceCream.logger.severe(
          "Invalid modules.antiafk."
              + key
              + ": expected a number from "
              + min
              + (max == Double.MAX_VALUE ? " upwards" : " to " + max)
              + "; Anti-AFK module is disabled.");
      return null;
    }
    return number.doubleValue();
  }

  private static Sound readSound() {
    String name = IceCream.config.getString(CONFIG + "sound", "");
    if (name == null || name.isBlank()) return null;
    try {
      return Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f);
    } catch (InvalidKeyException exception) {
      IceCream.logger.warning("Invalid modules.antiafk.sound '" + name + "'; no sound will play.");
      return null;
    }
  }
}
