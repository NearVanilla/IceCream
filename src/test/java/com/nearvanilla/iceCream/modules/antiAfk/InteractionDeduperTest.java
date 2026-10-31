package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InteractionDeduperTest {
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Test
  void repeatedFingerprintIsIgnored() {
    InteractionDeduper deduper = new InteractionDeduper();
    assertTrue(deduper.isNew(ALICE, "RIGHT_CLICK_AIR|FISHING_ROD"));
    assertFalse(deduper.isNew(ALICE, "RIGHT_CLICK_AIR|FISHING_ROD"));
  }

  @Test
  void changedFingerprintCounts() {
    InteractionDeduper deduper = new InteractionDeduper();
    deduper.isNew(ALICE, "ENTITY|ZOMBIE|DIAMOND_SWORD");
    assertTrue(deduper.isNew(ALICE, "ENTITY|SKELETON|DIAMOND_SWORD"));
  }

  @Test
  void playersAreTrackedSeparately() {
    InteractionDeduper deduper = new InteractionDeduper();
    deduper.isNew(ALICE, "same");
    assertTrue(deduper.isNew(BOB, "same"));
  }

  @Test
  void forgettingAPlayerResetsTheirFingerprint() {
    InteractionDeduper deduper = new InteractionDeduper();
    deduper.isNew(ALICE, "same");
    deduper.forget(ALICE);
    assertTrue(deduper.isNew(ALICE, "same"));
  }
}
