package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Tracks when each player was last active and any captcha they have open. Chat answers arrive on an
 * async thread while timeouts are handled on the main thread, so a captcha is only ever closed
 * through a compare-and-set: either the answer or the expiry wins, never both.
 *
 * <p>Activity does not close an open captcha. Only a correct answer does.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class AfkTracker {

  /** An open captcha: the code to type and when it runs out, in clock milliseconds. */
  public record Challenge(String code, long deadlineMs) {}

  private static final class Session {
    private volatile long lastActiveMs;
    private final AtomicReference<Challenge> challenge = new AtomicReference<>();

    private Session(long lastActiveMs) {
      this.lastActiveMs = lastActiveMs;
    }
  }

  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final LongSupplier clock;

  /**
   * @param clock the current time in milliseconds
   */
  public AfkTracker(LongSupplier clock) {
    this.clock = clock;
  }

  /** Starts tracking a player with a fresh timer. */
  public void start(UUID playerId) {
    sessions.put(playerId, new Session(clock.getAsLong()));
  }

  public void remove(UUID playerId) {
    sessions.remove(playerId);
  }

  public void clear() {
    sessions.clear();
  }

  public boolean isTracked(UUID playerId) {
    return sessions.containsKey(playerId);
  }

  /**
   * Resets the player's timer.
   *
   * @return false when the player is not tracked or has a captcha open
   */
  public boolean markActive(UUID playerId) {
    Session session = sessions.get(playerId);
    if (session == null || session.challenge.get() != null) return false;
    session.lastActiveMs = clock.getAsLong();
    return true;
  }

  /**
   * Treats the player as inactive for at least {@code timeoutMs}, so they are due straight away.
   *
   * @return false when the player is not tracked or already has a captcha open
   */
  public boolean markIdle(UUID playerId, long timeoutMs) {
    Session session = sessions.get(playerId);
    if (session == null || session.challenge.get() != null) return false;
    session.lastActiveMs = Math.min(session.lastActiveMs, clock.getAsLong() - timeoutMs);
    return true;
  }

  /** Returns how long the player has been inactive, or 0 when they are not tracked. */
  public long idleMs(UUID playerId) {
    Session session = sessions.get(playerId);
    return session == null ? 0 : clock.getAsLong() - session.lastActiveMs;
  }

  /** Returns true when the player has no captcha open and has been inactive for the timeout. */
  public boolean isDue(UUID playerId, long timeoutMs) {
    Session session = sessions.get(playerId);
    return session != null
        && session.challenge.get() == null
        && clock.getAsLong() - session.lastActiveMs >= timeoutMs;
  }

  /**
   * Opens a captcha unless one is already open.
   *
   * @return the open captcha, or null when the player is not tracked
   */
  public Challenge issue(UUID playerId, String code, long windowMs) {
    Session session = sessions.get(playerId);
    if (session == null) return null;
    Challenge challenge = new Challenge(code, clock.getAsLong() + windowMs);
    return session.challenge.compareAndSet(null, challenge) ? challenge : session.challenge.get();
  }

  /** Returns the player's open captcha, or null. */
  public Challenge challenge(UUID playerId) {
    Session session = sessions.get(playerId);
    return session == null ? null : session.challenge.get();
  }

  /**
   * Closes the captcha if the answer matches and resets the timer. An answer that arrives after the
   * deadline still counts until {@link #tryExpire} has closed the captcha.
   *
   * @return true when this call closed the captcha
   */
  public boolean tryResolve(UUID playerId, String answer) {
    Session session = sessions.get(playerId);
    if (session == null) return false;
    Challenge challenge = session.challenge.get();
    if (challenge == null || !CaptchaGenerator.matches(challenge.code(), answer)) return false;
    // Reset the timer before closing the captcha, otherwise the heartbeat could see no captcha and
    // an expired timer in between and open a new one. If the expiry wins instead, the player is
    // kicked anyway.
    session.lastActiveMs = clock.getAsLong();
    return session.challenge.compareAndSet(challenge, null);
  }

  /**
   * Closes the captcha if its deadline has passed.
   *
   * @return true when this call closed the captcha, meaning the player should be kicked
   */
  public boolean tryExpire(UUID playerId) {
    Session session = sessions.get(playerId);
    if (session == null) return false;
    Challenge challenge = session.challenge.get();
    return challenge != null
        && clock.getAsLong() >= challenge.deadlineMs()
        && session.challenge.compareAndSet(challenge, null);
  }

  /**
   * Closes any open captcha without an answer and resets the timer, for players who became exempt.
   *
   * @return true when a captcha was open
   */
  public boolean cancelChallenge(UUID playerId) {
    Session session = sessions.get(playerId);
    if (session == null) return false;
    boolean hadChallenge = session.challenge.getAndSet(null) != null;
    session.lastActiveMs = clock.getAsLong();
    return hadChallenge;
  }

  /** Returns the players who currently have a captcha open. */
  public Set<UUID> withOpenChallenge() {
    return sessions.entrySet().stream()
        .filter(entry -> entry.getValue().challenge.get() != null)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }
}
