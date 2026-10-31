package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CaptchaGeneratorTest {

  @Test
  void generatesCodesOfTheRequestedLength() {
    CaptchaGenerator generator = new CaptchaGenerator(new Random(1));
    assertEquals(4, generator.generate(4).length());
    assertEquals(12, generator.generate(12).length());
  }

  @Test
  void neverUsesConfusableCharacters() {
    CaptchaGenerator generator = new CaptchaGenerator(new Random(2));
    for (int i = 0; i < 10_000; i++) {
      String code = generator.generate(6);
      for (char c : code.toCharArray()) {
        assertTrue(CaptchaGenerator.ALPHABET.indexOf(c) >= 0, "unexpected character " + c);
        assertTrue("0O1IL".indexOf(c) < 0, "confusable character " + c);
      }
    }
  }

  @Test
  void sameSeedGivesSameCode() {
    assertEquals(
        new CaptchaGenerator(new Random(42)).generate(6),
        new CaptchaGenerator(new Random(42)).generate(6));
  }

  @Test
  void matchingIgnoresCaseAndSpaces() {
    assertTrue(CaptchaGenerator.matches("AB3XY9", "ab3xy9"));
    assertTrue(CaptchaGenerator.matches("AB3XY9", "  AB3 XY9 "));
  }

  @Test
  void matchingRejectsWrongEmptyAndPartialAnswers() {
    assertFalse(CaptchaGenerator.matches("AB3XY9", "AB3XY8"));
    assertFalse(CaptchaGenerator.matches("AB3XY9", ""));
    assertFalse(CaptchaGenerator.matches("AB3XY9", "AB3"));
    assertFalse(CaptchaGenerator.matches("AB3XY9", "AB3XY99"));
  }
}
