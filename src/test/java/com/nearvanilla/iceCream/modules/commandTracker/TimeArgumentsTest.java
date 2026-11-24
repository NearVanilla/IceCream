package com.nearvanilla.iceCream.modules.commandTracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimeArgumentsTest {
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Europe/London");

  private static Instant parse(String input) {
    return TimeArguments.parse(input, NOW, ZONE);
  }

  @Test
  void durationsCountBackFromNow() {
    assertEquals(NOW.minusSeconds(30), parse("30s"));
    assertEquals(NOW.minus(Duration.ofMinutes(15)), parse("15m"));
    assertEquals(NOW.minus(Duration.ofHours(2)), parse("2H"));
    assertEquals(NOW.minus(Duration.ofDays(7)), parse("7d"));
    assertEquals(NOW.minus(Duration.ofDays(14)), parse("2w"));
  }

  @Test
  void durationPartsAddUp() {
    assertEquals(NOW.minus(Duration.ofDays(1).plusHours(12).plusMinutes(5)), parse("1d12h5m"));
  }

  @Test
  void datesAreTheStartOfThatDayInTheServerZone() {
    assertEquals(
        LocalDateTime.parse("2026-07-01T00:00").atZone(ZONE).toInstant(), parse("2026-07-01"));
  }

  @Test
  void dateTimesAreReadInTheServerZone() {
    assertEquals(
        LocalDateTime.parse("2026-07-01T14:30").atZone(ZONE).toInstant(),
        parse("2026-07-01T14:30"));
    assertEquals(
        LocalDateTime.parse("2026-07-01T14:30:15").atZone(ZONE).toInstant(),
        parse("2026-07-01t14:30:15"));
  }

  @Test
  void rejectsAnythingElse() {
    for (String input : new String[] {"", "abc", "5", "5x", "d5", "2026-13-01", "1d 2h", "-5m"}) {
      assertThrows(IllegalArgumentException.class, () -> parse(input), input);
    }
  }

  @Test
  void rejectsDurationsTooLargeToRepresent() {
    assertThrows(IllegalArgumentException.class, () -> parse("99999999999999999999d"));
    assertThrows(IllegalArgumentException.class, () -> parse("999999999999999w"));
  }
}
