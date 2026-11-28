package com.nearvanilla.iceCream.modules.tag;

import org.bukkit.scoreboard.DisplaySlot;

/**
 * Where the "Times Tagged" counter can be shown, or {@link #NONE} to hide it.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public enum TagCounterSlot {
  BELOW_NAME(DisplaySlot.BELOW_NAME),
  PLAYER_LIST(DisplaySlot.PLAYER_LIST),
  SIDEBAR(DisplaySlot.SIDEBAR),
  NONE(null);

  private final DisplaySlot slot;

  TagCounterSlot(DisplaySlot slot) {
    this.slot = slot;
  }

  /**
   * Gets the scoreboard display slot.
   *
   * @return the slot, or null for {@link #NONE}
   */
  public DisplaySlot slot() {
    return slot;
  }
}
