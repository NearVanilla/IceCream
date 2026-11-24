package com.nearvanilla.iceCream.modules.coordinatesHud;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.survival.coordinateshud.HUDRunnable
 * Adaptations: the choice is stored under the plugin's own namespace; PaperTweaks'
 *   vanillatweaks:coordinateshud entry is only read, so players keep the choice they made there.
 */
/**
 * Stores whether a player wants the Coordinates HUD, in the player's {@link
 * PersistentDataContainer}, so the choice survives leaving and restarts.
 *
 * <p>The choice is stored at {@code <namespace>:coordinateshud.enabled}. A player with no choice
 * stored yet takes PaperTweaks' choice when one exists, and otherwise the configured default. That
 * starting value is then stored, so a later change of the default only affects new players.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class HudPreference {
  static final NamespacedKey LEGACY_KEY = new NamespacedKey("vanillatweaks", "coordinateshud");

  private final NamespacedKey key;

  /**
   * Creates the store.
   *
   * @param namespace the namespace of the key, normally the plugin's
   */
  public HudPreference(String namespace) {
    this.key = new NamespacedKey(namespace, "coordinateshud.enabled");
  }

  /**
   * Reads a player's choice, storing the starting value first if they have none.
   *
   * @param pdc the player's container
   * @param enabledByDefault whether players with no choice anywhere see the HUD
   * @return whether the player wants the HUD
   */
  public boolean resolve(PersistentDataContainer pdc, boolean enabledByDefault) {
    if (pdc.has(key, PersistentDataType.BOOLEAN)) {
      return pdc.get(key, PersistentDataType.BOOLEAN);
    }
    boolean shown =
        pdc.has(LEGACY_KEY, PersistentDataType.BOOLEAN)
            ? pdc.get(LEGACY_KEY, PersistentDataType.BOOLEAN)
            : enabledByDefault;
    set(pdc, shown);
    return shown;
  }

  /**
   * Stores a player's choice.
   *
   * @param pdc the player's container
   * @param shown whether the player wants the HUD
   */
  public void set(PersistentDataContainer pdc, boolean shown) {
    pdc.set(key, PersistentDataType.BOOLEAN, shown);
  }
}
