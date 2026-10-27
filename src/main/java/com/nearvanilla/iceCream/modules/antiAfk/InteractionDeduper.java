package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers each player's last interaction so that the same interaction repeated over and over (an
 * auto-clicker on a mob grinder, a held fishing rod) stops counting as activity.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class InteractionDeduper {
  private final Map<UUID, String> lastFingerprints = new ConcurrentHashMap<>();

  /**
   * Records an interaction.
   *
   * @param playerId the player who interacted
   * @param fingerprint a description of the interaction, such as action, target and held item
   * @return true when it differs from the player's previous interaction
   */
  public boolean isNew(UUID playerId, String fingerprint) {
    return !fingerprint.equals(lastFingerprints.put(playerId, fingerprint));
  }

  public void forget(UUID playerId) {
    lastFingerprints.remove(playerId);
  }

  public void clear() {
    lastFingerprints.clear();
  }
}
