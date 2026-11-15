package com.nearvanilla.iceCream.modules.trialVaultReset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nearvanilla.iceCream.modules.trialVaultReset.ResetDecision.Outcome;
import org.junit.jupiter.api.Test;

class ResetDecisionTest {
  private static final long DAY = 86_400_000;

  @Test
  void ignoresPlayerWhoHasNotLooted() {
    assertEquals(Outcome.IGNORE, ResetDecision.decide(false, null, 1000, 0).outcome());
    assertEquals(Outcome.IGNORE, ResetDecision.decide(false, 500L, 1000, 0).outcome());
  }

  @Test
  void resetsWhenNoTimeRecorded() {
    assertEquals(Outcome.RESET, ResetDecision.decide(true, null, 1000, DAY).outcome());
  }

  @Test
  void resetsWhenDelayPassed() {
    assertEquals(Outcome.RESET, ResetDecision.decide(true, 0L, DAY, DAY).outcome());
  }

  @Test
  void waitsWithTimeLeft() {
    ResetDecision decision = ResetDecision.decide(true, 0L, DAY - 1_000, DAY);

    assertEquals(Outcome.WAIT, decision.outcome());
    assertEquals(1_000, decision.remainingMs());
  }

  @Test
  void zeroDelayResetsImmediately() {
    assertEquals(Outcome.RESET, ResetDecision.decide(true, 1000L, 1000, 0).outcome());
  }

  @Test
  void negativeDelayActsAsZero() {
    assertEquals(Outcome.RESET, ResetDecision.decide(true, 1000L, 1000, -5).outcome());
  }

  @Test
  void lootTimeInTheFutureWaitsFullDelayAtMost() {
    ResetDecision decision = ResetDecision.decide(true, 5000L, 1000, 10_000);

    assertEquals(Outcome.WAIT, decision.outcome());
    assertEquals(10_000, decision.remainingMs());
  }
}
