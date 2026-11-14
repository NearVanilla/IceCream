package com.nearvanilla.iceCream.modules.trialVaultReset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationFormatTest {

  @Test
  void formatsSecondsRoundingUp() {
    assertEquals("1s", DurationFormat.format(1));
    assertEquals("45s", DurationFormat.format(45_000));
  }

  @Test
  void formatsMinutesWithSeconds() {
    assertEquals("1m 1s", DurationFormat.format(61_000));
  }

  @Test
  void formatsHoursWithMinutes() {
    assertEquals("3h 12m", DurationFormat.format(11_520_000));
    assertEquals("3h 12m", DurationFormat.format(11_550_000));
  }

  @Test
  void omitsZeroSecondUnit() {
    assertEquals("3h", DurationFormat.format(10_800_000));
  }

  @Test
  void formatsDaysWithHours() {
    assertEquals("1d 1h", DurationFormat.format(90_000_000));
  }

  @Test
  void roundingUpCarriesIntoTheNextUnit() {
    assertEquals("1h", DurationFormat.format(3_599_500));
  }

  @Test
  void formatsZero() {
    assertEquals("0s", DurationFormat.format(0));
  }
}
