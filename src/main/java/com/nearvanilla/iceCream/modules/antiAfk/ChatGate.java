package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The single place that decides what happens to a chat message: pass it through as activity, or
 * hide it because it is an answer to an open captcha. Both the Paper chat listener and the
 * CarbonChat hook ask this gate, and whichever asks first decides. The verdict is cached briefly
 * per player and message so the second hook gets the same answer without the message being handled
 * twice.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class ChatGate {
  /** How long a verdict is reused for the same player and message. */
  public static final long DEDUPE_WINDOW_MS = 2_000;

  /** What to do with a chat message. */
  public enum Verdict {
    PASS,
    HIDE_CORRECT,
    HIDE_WRONG
  }

  /** Receives the result of an answer. Called once per message, on the chat thread. */
  public interface Listener {
    void onCorrect(UUID playerId);

    void onWrong(UUID playerId);
  }

  private record Cached(String text, Verdict verdict, long atMs) {}

  private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
  private final AfkTracker tracker;
  private final LongSupplier clock;
  private final Listener listener;

  public ChatGate(AfkTracker tracker, LongSupplier clock, Listener listener) {
    this.tracker = tracker;
    this.clock = clock;
    this.listener = listener;
  }

  /**
   * Decides what to do with a chat message.
   *
   * @param playerId the sender
   * @param text the plain text of the message
   * @return the verdict for this message
   */
  public Verdict onChat(UUID playerId, String text) {
    long now = clock.getAsLong();
    boolean[] fresh = {false};
    Cached cached =
        cache.compute(
            playerId,
            (id, previous) -> {
              if (previous != null
                  && previous.text().equals(text)
                  && now - previous.atMs() < DEDUPE_WINDOW_MS) {
                return previous;
              }
              fresh[0] = true;
              return new Cached(text, evaluate(playerId, text), now);
            });
    if (fresh[0]) {
      switch (cached.verdict()) {
        case HIDE_CORRECT -> listener.onCorrect(playerId);
        case HIDE_WRONG -> listener.onWrong(playerId);
        case PASS -> {}
      }
    }
    return cached.verdict();
  }

  private Verdict evaluate(UUID playerId, String text) {
    if (tracker.challenge(playerId) == null) {
      tracker.markActive(playerId);
      return Verdict.PASS;
    }
    return tracker.tryResolve(playerId, text) ? Verdict.HIDE_CORRECT : Verdict.HIDE_WRONG;
  }

  public void forget(UUID playerId) {
    cache.remove(playerId);
  }

  public void clear() {
    cache.clear();
  }
}
