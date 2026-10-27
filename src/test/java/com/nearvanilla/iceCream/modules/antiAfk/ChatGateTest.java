package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.modules.antiAfk.ChatGate.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatGateTest {
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final long TIMEOUT = 60_000;
  private static final long WINDOW = 20_000;

  private final AtomicLong now = new AtomicLong(1_000_000);
  private final List<String> events = new ArrayList<>();
  private AfkTracker tracker;
  private ChatGate gate;

  @BeforeEach
  void setUp() {
    tracker = new AfkTracker(now::get);
    tracker.start(ALICE);
    gate =
        new ChatGate(
            tracker,
            new ChatGate.Listener() {
              @Override
              public void onCorrect(UUID playerId) {
                events.add("correct");
              }

              @Override
              public void onWrong(UUID playerId) {
                events.add("wrong");
              }
            });
  }

  private void openCaptcha() {
    now.addAndGet(TIMEOUT);
    tracker.issue(ALICE, "ABC234", WINDOW);
  }

  @Test
  void normalChatPassesAndCountsAsActivity() {
    now.addAndGet(TIMEOUT - 1);
    assertEquals(Verdict.PASS, gate.onChat(ALICE, "hello"));
    assertEquals(0, tracker.idleMs(ALICE));
    assertEquals(List.of(), events);
  }

  @Test
  void correctAnswerIsHiddenAndClosesTheCaptcha() {
    openCaptcha();
    assertEquals(Verdict.HIDE_CORRECT, gate.onChat(ALICE, "abc 234"));
    assertNull(tracker.challenge(ALICE));
    assertEquals(List.of("correct"), events);
  }

  @Test
  void wrongAnswerIsHiddenAndKeepsTheCaptcha() {
    openCaptcha();
    assertEquals(Verdict.HIDE_WRONG, gate.onChat(ALICE, "hello"));
    assertNotNull(tracker.challenge(ALICE));
    assertEquals(List.of("wrong"), events);
  }

  @Test
  void sameMessageIsHiddenOnceACaptchaOpens() {
    assertEquals(Verdict.PASS, gate.onChat(ALICE, "ok"));
    tracker.issue(ALICE, "ABC234", WINDOW);
    assertEquals(Verdict.HIDE_WRONG, gate.onChat(ALICE, "ok"));
    assertEquals(List.of("wrong"), events);
  }

  @Test
  void eachMessageIsEvaluated() {
    openCaptcha();
    gate.onChat(ALICE, "wrong");
    gate.onChat(ALICE, "wrong");
    assertEquals(Verdict.HIDE_CORRECT, gate.onChat(ALICE, "ABC234"));
    assertEquals(List.of("wrong", "wrong", "correct"), events);
  }
}
