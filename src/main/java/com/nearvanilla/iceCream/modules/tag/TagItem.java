package com.nearvanilla.iceCream.modules.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original files: me.machinemaker.papertweaks.modules.hermitcraft.tag.Tag and TagManager
 * Adaptations: the item is marked in its persistent data so a renamed name tag cannot pass for it,
 *   and PaperTweaks' unmarked "Tag!" name tags are still recognised.
 */
/**
 * The "Tag!" name tag that whoever is "it" hits other players with.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class TagItem {
  private static final Component NAME =
      Component.text("Tag!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);

  private final NamespacedKey markerKey;

  /**
   * Creates the item factory.
   *
   * @param namespace the namespace of the item's marker key, normally the plugin's
   */
  public TagItem(String namespace) {
    this.markerKey = new NamespacedKey(namespace, "tag.item");
  }

  /**
   * Creates a new tag item.
   *
   * @return the item
   */
  public ItemStack create() {
    ItemStack item = legacy();
    item.editMeta(
        meta -> meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true));
    return item;
  }

  /**
   * Checks whether an item is a tag item, including those PaperTweaks handed out.
   *
   * @param item the item, may be null
   * @return true if the item is a tag item
   */
  public boolean isTagItem(ItemStack item) {
    if (item == null || item.getType() != Material.NAME_TAG) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta != null && meta.getPersistentDataContainer().has(markerKey)) return true;
    return legacy().isSimilar(item);
  }

  /**
   * Gives a player a tag item, dropping it at their feet when their inventory is full.
   *
   * @param player the player
   */
  public void giveTo(Player player) {
    ItemStack item = create();
    PlayerInventory inventory = player.getInventory();
    int empty = inventory.firstEmpty();
    if (empty > -1) {
      inventory.setItem(empty, item);
      return;
    }
    Item dropped = player.getWorld().dropItem(player.getLocation(), item);
    dropped.setPickupDelay(0);
    dropped.setOwner(player.getUniqueId());
  }

  /**
   * Removes every tag item from a player's inventory, including armor and off hand slots.
   *
   * @param player the player
   */
  public void removeFrom(Player player) {
    PlayerInventory inventory = player.getInventory();
    ItemStack[] contents = inventory.getContents();
    for (int slot = 0; slot < contents.length; slot++) {
      if (isTagItem(contents[slot])) inventory.setItem(slot, null);
    }
  }

  /** The item PaperTweaks used: a name tag named "Tag!" and nothing else. */
  private static ItemStack legacy() {
    ItemStack item = new ItemStack(Material.NAME_TAG);
    item.editMeta(meta -> meta.displayName(NAME));
    return item;
  }
}
