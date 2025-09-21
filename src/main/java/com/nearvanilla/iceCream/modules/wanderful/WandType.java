package com.nearvanilla.iceCream.modules.wanderful;

import com.nearvanilla.iceCream.IceCream;
import java.util.List;
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

  public List<NamespacedKey> getPersistentKeys() {
    return switch (this) {
      case ITEM_FRAME -> List.of(persistentKey);
      case ARMOR_STAND -> {
        NamespacedKey toolField = ArmorStandEditorWrapper.getFlag();
        if (toolField == null) {
          throw new IllegalStateException(
              "ArmorStandEditor plugin not found or not configured properly.");
        }

        yield List.of(persistentKey, toolField);
      }
    };
  }

  public ItemStack createItem() {
    return WanderfulItems.createWand(this);
  }
}
