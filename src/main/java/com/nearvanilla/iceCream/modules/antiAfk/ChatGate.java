package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.UUID;

/**
 * The single place that decides what happens to a chat message: pass it through as activity, or
 * hide it because it is an answer to an open captcha.
 *
 * <p>Both the Paper chat listener and the CarbonChat hook ask this gate, and every call is decided
 * on its own. A message typed in normal chat reaches the Paper listener first, at the lowest
 * priority. If it is hidden there, the event is cancelled and CarbonChat, which skips cancelled
 * chat events, never sees it. If it passes, the CarbonChat hook asks again, which only counts the
 * same activity twice. Messages sent through CarbonChat commands reach only the CarbonChat hook.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class ChatGate {

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

  private final AfkTracker tracker;
  private final Listener listener;

  public ChatGate(AfkTracker tracker, Listener listener) {
    this.tracker = tracker;
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
    if (tracker.challenge(playerId) == null) {
      tracker.markActive(playerId);
      return Verdict.PASS;
    }
    if (tracker.tryResolve(playerId, text)) {
      listener.onCorrect(playerId);
      return Verdict.HIDE_CORRECT;
    }
    listener.onWrong(playerId);
    return Verdict.HIDE_WRONG;
  }
}
