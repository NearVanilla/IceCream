package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Generates the codes players type to prove they are not AFK. The alphabet leaves out characters
 * that are easy to confuse (0/O, 1/I/L) and symbols that are awkward on some keyboards.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class CaptchaGenerator {
  public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

  private final RandomGenerator random;

  public CaptchaGenerator(RandomGenerator random) {
    this.random = random;
  }

  /**
   * Generates a new code.
   *
   * @param length the number of characters in the code
   * @return an upper case code made from {@link #ALPHABET}
   */
  public String generate(int length) {
    StringBuilder code = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  /**
   * Checks an answer against a code, ignoring case and any spaces.
   *
   * @param expected the code that was shown
   * @param input what the player typed
   * @return true when the answer matches
   */
  public static boolean matches(String expected, String input) {
    String normalized = input.replace(" ", "").toUpperCase(Locale.ROOT);
    return !normalized.isEmpty() && normalized.equals(expected.toUpperCase(Locale.ROOT));
  }
}
