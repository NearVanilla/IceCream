package com.nearvanilla.iceCream.modules.trialVaultReset;

/**
 * Formats a duration for players, such as {@code 3h 12m} or {@code 45s}. Seconds are rounded up,
 * then the largest non-zero unit is shown with the next unit, which is left out when it is zero.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 */
public final class DurationFormat {
  private static final long[] UNIT_SECONDS = {86_400, 3_600, 60, 1};
  private static final String[] UNIT_NAMES = {"d", "h", "m", "s"};

  private DurationFormat() {}

  /**
   * Formats a duration.
   *
   * @param ms the duration in milliseconds
   * @return the formatted duration, {@code 0s} for zero or less
   */
  public static String format(long ms) {
    long seconds = Math.ceilDiv(Math.max(0, ms), 1_000);
    for (int i = 0; i < UNIT_SECONDS.length; i++) {
      long value = seconds / UNIT_SECONDS[i];
      if (value == 0 && i < UNIT_SECONDS.length - 1) continue;
      String result = value + UNIT_NAMES[i];
      if (i + 1 < UNIT_SECONDS.length) {
        long next = seconds % UNIT_SECONDS[i] / UNIT_SECONDS[i + 1];
        if (next > 0) result += " " + next + UNIT_NAMES[i + 1];
      }
      return result;
    }
    return "0s";
  }
}
