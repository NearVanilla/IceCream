package com.nearvanilla.iceCream.modules.persistentHeads;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The item data a placed player head remembers, read from its persistent data container.
 *
 * @param lore the lore of the item that was placed, or null when none is stored
 * @param legacyName the custom name stored by PaperTweaks, or null when none is stored. Vanilla
 *     keeps custom names itself, so only heads placed under PaperTweaks have this.
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 */
public record StoredHead(List<Component> lore, Component legacyName) {

  /** A head with nothing stored. */
  public static final StoredHead EMPTY = new StoredHead(null, null);

  /**
   * Checks whether anything is stored.
   *
   * @return true when neither lore nor a name is stored
   */
  public boolean isEmpty() {
    return lore == null && legacyName == null;
  }

  /**
   * Puts the stored data back onto a dropped player head. Other items are left alone, and a custom
   * name vanilla already restored is kept.
   *
   * @param stack the dropped item
   */
  public void applyTo(ItemStack stack) {
    if (isEmpty() || stack.getType() != Material.PLAYER_HEAD) return;
    stack.editMeta(
        meta -> {
          if (lore != null) meta.lore(lore);
          if (legacyName != null && !meta.hasCustomName()) meta.customName(legacyName);
        });
  }
}
