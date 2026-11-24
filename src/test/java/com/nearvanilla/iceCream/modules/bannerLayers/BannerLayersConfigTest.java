package com.nearvanilla.iceCream.modules.bannerLayers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BannerLayersConfigTest {
  private final List<String> warnings = new ArrayList<>();

  private int parse(Object value) {
    return BannerLayersConfig.parseMaxLayers(value, warnings::add);
  }

  @Test
  void usesValuesInRange() {
    assertEquals(16, parse(16));
    assertEquals(6, parse(6));
    assertEquals(10, parse(10));
    assertTrue(warnings.isEmpty());
  }

  @Test
  void clampsValuesOutOfRange() {
    assertEquals(6, parse(3));
    assertEquals(16, parse(40));
    assertEquals(2, warnings.size());
  }

  @Test
  void fallsBackToSixteenForInvalidValues() {
    assertEquals(16, parse("lots"));
    assertEquals(16, parse(8.5));
    assertEquals(16, parse(null));
    assertEquals(3, warnings.size());
  }
}
