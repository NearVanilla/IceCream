package com.nearvanilla.iceCream.modules.commandTracker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandLogFiltersTest {
  @Test
  void noFiltersGiveNoFlags() {
    CommandLogFilters filters = new CommandLogFilters(null, null, null, null, null);

    assertTrue(filters.isEmpty());
    assertEquals("", filters.toFlags());
  }

  @Test
  void flagsAreListedInAFixedOrder() {
    CommandLogFilters filters = new CommandLogFilters("Bob", "tp", "7d", "1h", "spawn");

    assertFalse(filters.isEmpty());
    assertEquals(
        "--player Bob --command tp --since 7d --until 1h --search spawn", filters.toFlags());
  }

  @Test
  void valuesWithSpacesOrQuotesAreQuoted() {
    assertEquals("\"two words\"", CommandLogFilters.quote("two words"));
    assertEquals("\"say \\\"hi\\\"\"", CommandLogFilters.quote("say \"hi\""));
    assertEquals("\"it's\"", CommandLogFilters.quote("it's"));
    assertEquals("\"\"", CommandLogFilters.quote(""));
    assertEquals("plain", CommandLogFilters.quote("plain"));
  }
}
