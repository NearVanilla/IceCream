package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.playerSeen.PlayerSeenUtils;
import java.lang.reflect.Field;

/**
 * Helpers for testing the player seen module, whose date formatter is built from the configuration
 * on first use and then kept in a static field for the life of the server.
 */
public final class PlayerSeenTestUtils {

  private PlayerSeenTestUtils() {}

  /**
   * Forgets the date formatter {@link PlayerSeenUtils#formatDate(long)} built earlier, so that the
   * next call reads the date format from the current configuration again. A test that formats a
   * date calls this before and after, so one test's format cannot leak into another.
   */
  public static void resetDateFormatter() {
    try {
      Field formatter = PlayerSeenUtils.class.getDeclaredField("dateFormatter");
      formatter.setAccessible(true);
      formatter.set(null, null);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not reset the date formatter", exception);
    }
  }
}
