package com.nearvanilla.iceCream.modules.trialVaultReset;

/**
 * Decides what happens when a player uses a key on a trial vault.
 *
 * @param outcome what the module should do
 * @param remainingMs milliseconds until the player can reset the vault; 0 unless {@link
 *     Outcome#WAIT}
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 */
public record ResetDecision(Outcome outcome, long remainingMs) {

  /** What the module should do with the key click. */
  public enum Outcome {
    /** The player has not looted the vault; vanilla handles the key. */
    IGNORE,
    /** Remove the player from the vault's rewarded players. */
    RESET,
    /** The delay has not passed yet. */
    WAIT
  }

  /**
   * Decides whether a player can reset a vault.
   *
   * @param rewarded whether the vault lists the player as already rewarded
   * @param lootTimeMs when the player last looted the vault, or null when no time is recorded
   * @param nowMs the current time
   * @param delayMs the reset delay; negative values count as 0
   * @return the decision
   */
  public static ResetDecision decide(boolean rewarded, Long lootTimeMs, long nowMs, long delayMs) {
    if (!rewarded) return new ResetDecision(Outcome.IGNORE, 0);
    long delay = Math.max(0, delayMs);
    if (lootTimeMs == null) return new ResetDecision(Outcome.RESET, 0);
    // Capped at the delay, so a loot time in the future (clock moved back) never waits longer.
    long remaining = Math.min(delay, lootTimeMs + delay - nowMs);
    if (remaining <= 0) return new ResetDecision(Outcome.RESET, 0);
    return new ResetDecision(Outcome.WAIT, remaining);
  }
}
