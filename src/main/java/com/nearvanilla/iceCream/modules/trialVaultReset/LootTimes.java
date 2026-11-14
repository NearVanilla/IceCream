package com.nearvanilla.iceCream.modules.trialVaultReset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stores when each player last looted a trial vault, in the vault's {@link
 * PersistentDataContainer}. Each player has one {@code LONG} entry at {@code
 * <namespace>:trialvaultreset.loot.<uuid>}.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 */
public final class LootTimes {
  private static final String PREFIX = "trialvaultreset.loot.";

  private final String namespace;

  /**
   * Creates the store.
   *
   * @param namespace the namespace of the keys, normally the plugin's
   */
  public LootTimes(String namespace) {
    this.namespace = namespace;
  }

  /**
   * Gets a player's loot time.
   *
   * @param pdc the vault's container
   * @param player the player
   * @return the loot time in milliseconds, or null when none is recorded
   */
  public Long get(PersistentDataContainer pdc, UUID player) {
    return pdc.get(key(player), PersistentDataType.LONG);
  }

  /**
   * Records a player's loot time.
   *
   * @param pdc the vault's container
   * @param player the player
   * @param timeMs the loot time in milliseconds
   */
  public void put(PersistentDataContainer pdc, UUID player, long timeMs) {
    pdc.set(key(player), PersistentDataType.LONG, timeMs);
  }

  /**
   * Removes a player's loot time.
   *
   * @param pdc the vault's container
   * @param player the player
   */
  public void remove(PersistentDataContainer pdc, UUID player) {
    pdc.remove(key(player));
  }

  /**
   * Removes the loot times of every player not listed. Other keys are left alone.
   *
   * @param pdc the vault's container
   * @param players the players whose loot times are kept
   */
  public void retainOnly(PersistentDataContainer pdc, Collection<UUID> players) {
    for (NamespacedKey key : new ArrayList<>(pdc.getKeys())) {
      if (!key.getNamespace().equals(namespace) || !key.getKey().startsWith(PREFIX)) continue;
      UUID player;
      try {
        player = UUID.fromString(key.getKey().substring(PREFIX.length()));
      } catch (IllegalArgumentException exception) {
        continue;
      }
      if (!players.contains(player)) pdc.remove(key);
    }
  }

  private NamespacedKey key(UUID player) {
    return new NamespacedKey(namespace, PREFIX + player);
  }
}
