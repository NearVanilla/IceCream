package com.nearvanilla.iceCream.modules.wanderful;

import com.nearvanilla.iceCream.IceCream;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * WandType is an enum representing different types of wands in the Wanderful module.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public enum WandType {
  ITEM_FRAME(
      "modules.wanderful.item_frame.", new NamespacedKey(IceCream.instance, "item_frame_wand")),
  // ArmorStandEditor recognizes this wand by its material and display name (its own config.yml
  // sets tool, requireToolName and toolName), not by a persistent data key, so the key below is
  // only used to identify the item on our side. Keep the display name in sync with ASE's toolName.
  ARMOR_STAND(
      "modules.wanderful.armor_stand.", new NamespacedKey(IceCream.instance, "armor_stand_wand"));

  private final String configKeyPrefix;
  private final NamespacedKey persistentKey;

  WandType(String configKeyPrefix, NamespacedKey persistentKey) {
    this.configKeyPrefix = configKeyPrefix;
    this.persistentKey = persistentKey;
  }

  public String getConfigKeyPrefix() {
    return configKeyPrefix;
  }

  public NamespacedKey getMainPersistentKey() {
    return persistentKey;
  }

  public String getId() {
    return name().toLowerCase();
  }

  public String getName() {
    return getId().replace('_', ' ');
  }

  public ItemStack createItem() {
    return WanderfulItems.createWand(this);
  }
}
