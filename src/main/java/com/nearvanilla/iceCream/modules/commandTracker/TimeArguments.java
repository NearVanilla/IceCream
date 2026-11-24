package com.nearvanilla.iceCream.modules.commandTracker;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the times typed in {@code /commandlog --since} and {@code --until}: either a duration
 * before now, such as {@code 30m}, {@code 2h}, {@code 7d} or {@code 1d12h}, or a date or date and
 * time in the server's time zone, such as {@code 2026-11-20} or {@code 2026-11-20T14:30}.
 */
public final class TimeArguments {
  /** Describes the accepted formats, for error messages. */
  public static final String FORMATS =
      "a duration such as 30m, 2h, 7d or 1d12h, or a date such as 2026-11-20 or 2026-11-20T14:30";

  private static final Pattern DURATION = Pattern.compile("(?:\\d+[smhdw])+");
  private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([smhdw])");

  private TimeArguments() {}

  /**
   * Reads a time.
   *
   * @param input what was typed
   * @param now the current time, which durations count back from
   * @param zone the zone dates are read in
   * @return the time
   * @throws IllegalArgumentException if the input is not in a supported format
   */
  public static Instant parse(String input, Instant now, ZoneId zone) {
    String text = input.strip().toLowerCase(Locale.ROOT);
    if (DURATION.matcher(text).matches()) {
      try {
        return now.minus(parseDuration(text));
      } catch (ArithmeticException | DateTimeException e) {
        throw new IllegalArgumentException("Duration is too long: " + input, e);
      }
    }
    try {
      if (text.contains("t")) {
        return LocalDateTime.parse(text.toUpperCase(Locale.ROOT)).atZone(zone).toInstant();
      }
      return LocalDate.parse(text).atStartOfDay(zone).toInstant();
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Not a duration or date: " + input, e);
    }
  }

  private static Duration parseDuration(String text) {
    Duration total = Duration.ZERO;
    Matcher matcher = DURATION_PART.matcher(text);
    while (matcher.find()) {
      long amount;
      try {
        amount = Long.parseLong(matcher.group(1));
      } catch (NumberFormatException e) {
        throw new ArithmeticException("Number too large");
      }
      Duration part =
          switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> Duration.ofDays(Math.multiplyExact(amount, 7));
          };
      total = total.plus(part);
    }
    return total;
  }
}
