package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotationGateTest {

  @Test
  void yawWrapsAroundTheSeam() {
    assertFalse(RotationGate.exceeded(179f, 0f, -179f, 0f, 3));
    assertTrue(RotationGate.exceeded(179f, 0f, -179f, 0f, 2));
  }

  @Test
  void pitchOnlyTurnCounts() {
    assertTrue(RotationGate.exceeded(0f, -10f, 0f, 15f, 20));
  }

  @Test
  void thresholdIsInclusive() {
    assertFalse(RotationGate.exceeded(0f, 0f, 19.9f, 0f, 20));
    assertTrue(RotationGate.exceeded(0f, 0f, 20f, 0f, 20));
  }

  @Test
  void smallJigglesNeverPass() {
    float yaw = 90f;
    for (int i = 0; i < 100; i++) {
      float jiggle = (i % 2 == 0) ? 3f : -3f;
      assertFalse(RotationGate.exceeded(90f, 0f, yaw + jiggle, jiggle, 20));
    }
  }
}
