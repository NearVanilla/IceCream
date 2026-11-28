package com.nearvanilla.iceCream.modules.tag;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original files: me.machinemaker.papertweaks.modules.hermitcraft.tag.Tag and TagManager
 * Adaptations: entries are stored under the plugin's own namespace, players can opt out, and
 *   PaperTweaks' vanillatweaks:tag and vanillatweaks:cooldown entries are moved over once.
 */
/**
 * Stores each player's tag state in their {@link PersistentDataContainer}, so it survives logging
 * off and restarts.
 *
 * <ul>
 *   <li>{@code <namespace>:tag.it}: {@code BOOLEAN}, present while the player is "it"
 *   <li>{@code <namespace>:tag.cooldown}: {@code LONG}, when the player may next tag someone, in
 *       epoch milliseconds
 *   <li>{@code <namespace>:tag.opted_out}: {@code BOOLEAN}, present while the player is not playing
 * </ul>
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class TagStore {
  private static final NamespacedKey LEGACY_IT = new NamespacedKey("vanillatweaks", "tag");
  private static final NamespacedKey LEGACY_COOLDOWN =
      new NamespacedKey("vanillatweaks", "cooldown");
  private static final NamespacedKey LEGACY_AFK = new NamespacedKey("vanillatweaks", "afk");

  private final NamespacedKey itKey;
  private final NamespacedKey cooldownKey;
  private final NamespacedKey optedOutKey;

  /**
   * Creates the store.
   *
   * @param namespace the namespace of the keys, normally the plugin's
   */
  public TagStore(String namespace) {
    this.itKey = new NamespacedKey(namespace, "tag.it");
    this.cooldownKey = new NamespacedKey(namespace, "tag.cooldown");
    this.optedOutKey = new NamespacedKey(namespace, "tag.opted_out");
  }

  /**
   * Checks whether a player is "it".
   *
   * @param player the player
   * @return true if the player is "it"
   */
  public boolean isIt(Player player) {
    return flag(player.getPersistentDataContainer(), itKey);
  }

  /**
   * Marks a player as "it" until the given time, before which they cannot tag anyone.
   *
   * @param player the player
   * @param cooldownUntilMs when the player may first tag someone, in epoch milliseconds
   */
  public void setIt(Player player, long cooldownUntilMs) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    pdc.set(itKey, PersistentDataType.BOOLEAN, true);
    pdc.set(cooldownKey, PersistentDataType.LONG, cooldownUntilMs);
  }

  /**
   * Removes a player's "it" status and cooldown.
   *
   * @param player the player
   */
  public void clearIt(Player player) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    pdc.remove(itKey);
    pdc.remove(cooldownKey);
  }

  /**
   * Gets when a player may next tag someone.
   *
   * @param player the player
   * @return the time in epoch milliseconds, or {@link Long#MIN_VALUE} when there is no cooldown
   */
  public long cooldownUntil(Player player) {
    return player
        .getPersistentDataContainer()
        .getOrDefault(cooldownKey, PersistentDataType.LONG, Long.MIN_VALUE);
  }

  /**
   * Checks whether a player has opted out of tag.
   *
   * @param player the player
   * @return true if the player is not playing
   */
  public boolean isOptedOut(Player player) {
    return flag(player.getPersistentDataContainer(), optedOutKey);
  }

  /**
   * Opts a player out of tag, or back in. Players are in by default.
   *
   * @param player the player
   * @param optedOut true to opt out, false to opt back in
   */
  public void setOptedOut(Player player, boolean optedOut) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    if (optedOut) {
      pdc.set(optedOutKey, PersistentDataType.BOOLEAN, true);
    } else {
      pdc.remove(optedOutKey);
    }
  }

  /**
   * Moves PaperTweaks' "it" status over to this store and removes PaperTweaks' entries, so a reset
   * here is not undone by them later. The cooldown is not carried over.
   *
   * @param player the player
   * @return true if PaperTweaks had the player as "it"
   */
  public boolean takeLegacyIt(Player player) {
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    boolean wasIt = flag(pdc, LEGACY_IT);
    pdc.remove(LEGACY_IT);
    pdc.remove(LEGACY_COOLDOWN);
    return wasIt;
  }

  /**
   * Checks whether PaperTweaks' AFK Display module has marked a player as AFK. PaperTweaks would
   * not let AFK players be tagged.
   *
   * @param player the player
   * @return true if the player is marked as AFK
   */
  public boolean isAfk(Player player) {
    return flag(player.getPersistentDataContainer(), LEGACY_AFK);
  }

  private static boolean flag(PersistentDataContainer pdc, NamespacedKey key) {
    try {
      return pdc.getOrDefault(key, PersistentDataType.BOOLEAN, false);
    } catch (IllegalArgumentException exception) {
      // An entry of another type under the same key.
      return false;
    }
  }
}
