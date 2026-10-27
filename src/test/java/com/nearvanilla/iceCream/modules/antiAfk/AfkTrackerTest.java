package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AfkTrackerTest {
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final long TIMEOUT = 60_000;
  private static final long WINDOW = 20_000;

  private final AtomicLong now = new AtomicLong(1_000_000);
  private AfkTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new AfkTracker(now::get);
    tracker.start(ALICE);
  }

  @Test
  void dueExactlyAtTheTimeout() {
    now.addAndGet(TIMEOUT - 1);
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
    now.addAndGet(1);
    assertTrue(tracker.isDue(ALICE, TIMEOUT));
  }

  @Test
  void activityResetsTheTimer() {
    now.addAndGet(TIMEOUT - 1);
    assertTrue(tracker.markActive(ALICE));
    now.addAndGet(TIMEOUT - 1);
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
  }

  @Test
  void activityDoesNotClearAnOpenCaptcha() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertFalse(tracker.markActive(ALICE));
    assertNotNull(tracker.challenge(ALICE));
  }

  @Test
  void openCaptchaIsNotDueAgain() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
  }

  @Test
  void correctAnswerClearsCaptchaAndResetsTimer() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    now.addAndGet(5_000);
    assertTrue(tracker.tryResolve(ALICE, "abc234"));
    assertNull(tracker.challenge(ALICE));
    now.addAndGet(TIMEOUT - 1);
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
  }

  @Test
  void wrongAnswerKeepsTheCaptcha() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertFalse(tracker.tryResolve(ALICE, "XYZ789"));
    assertEquals("ABC234", tracker.challenge(ALICE).code());
  }

  @Test
  void expiresOnlyAtTheDeadline() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    now.addAndGet(WINDOW - 1);
    assertFalse(tracker.tryExpire(ALICE));
    now.addAndGet(1);
    assertTrue(tracker.tryExpire(ALICE));
    assertNull(tracker.challenge(ALICE));
  }

  @Test
  void answerAfterTheDeadlineFails() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    now.addAndGet(WINDOW);
    assertFalse(tracker.tryResolve(ALICE, "ABC234"));
    assertTrue(tracker.tryExpire(ALICE));
  }

  @Test
  void onlyOneOfResolveAndExpireWins() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertTrue(tracker.tryResolve(ALICE, "ABC234"));
    now.addAndGet(WINDOW);
    assertFalse(tracker.tryExpire(ALICE));
  }

  @Test
  void cancellingClearsTheCaptchaAndResetsTheTimer() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertTrue(tracker.cancelChallenge(ALICE));
    assertNull(tracker.challenge(ALICE));
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
  }

  @Test
  void markingIdleMakesThePlayerDueImmediately() {
    assertTrue(tracker.markIdle(ALICE, TIMEOUT));
    assertTrue(tracker.isDue(ALICE, TIMEOUT));
    assertEquals(TIMEOUT, tracker.idleMs(ALICE));
  }

  @Test
  void markingIdleNeverShortensTheIdleTime() {
    now.addAndGet(TIMEOUT * 3);
    assertTrue(tracker.markIdle(ALICE, TIMEOUT));
    assertEquals(TIMEOUT * 3, tracker.idleMs(ALICE));
  }

  @Test
  void markingIdleFailsWithAnOpenCaptchaOrUntrackedPlayer() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertFalse(tracker.markIdle(ALICE, TIMEOUT));
    tracker.remove(ALICE);
    assertFalse(tracker.markIdle(ALICE, TIMEOUT));
  }

  @Test
  void removeDropsAllState() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
    tracker.remove(ALICE);
    assertNull(tracker.challenge(ALICE));
    assertFalse(tracker.isDue(ALICE, TIMEOUT));
    assertFalse(tracker.markActive(ALICE));
  }
}
