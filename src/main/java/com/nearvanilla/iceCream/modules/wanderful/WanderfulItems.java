package com.nearvanilla.iceCream.modules.wanderful;

import com.nearvanilla.iceCream.IceCream;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * WanderfulItems is a utility class for creating and managing wand items in the Wanderful module.
 * It provides methods to create wand items based on configuration settings and to add crafting
 * recipes for these items.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public class WanderfulItems {
  private WanderfulItems() {}

  /**
   * Create a wand ItemStack based on the specified WandType.
   *
   * @param WandType The prefix for config keys (e.g. "modules.wanderful.item_frame.")
   * @return The created ItemStack, or null if creation fails
   */
  public static ItemStack createWand(WandType type) {
    String configKeyPrefix = type.getConfigKeyPrefix();
    List<NamespacedKey> persistentKeys = type.getPersistentKeys();

    String wandMaterialType = IceCream.config.getString(configKeyPrefix + "type");
    String wandName = IceCream.config.getString(configKeyPrefix + "name");
    List<String> loreList = IceCream.config.getStringList(configKeyPrefix + "lore");

    if (wandMaterialType == null) {
      IceCream.instance.getLogger().warning("Wand material type not found for " + configKeyPrefix);
      return null;
    }

    Material wandMaterial = Material.valueOf(wandMaterialType);
    ItemStack stack = new ItemStack(wandMaterial);
    ItemMeta meta = stack.getItemMeta();

    if (wandName != null) {
      meta.displayName(MiniMessage.miniMessage().deserialize(wandName));
    }

    if (!loreList.isEmpty()) {
      List<Component> lore = loreList.stream().map(MiniMessage.miniMessage()::deserialize).toList();
      meta.lore(lore);
    }

    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

    // Make the item glow by adding an enchantment
    stack.addUnsafeEnchantment(Enchantment.LURE, 1);

    // Set persistent data keys
    for (NamespacedKey key : persistentKeys) {
      meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    stack.setItemMeta(meta);

    return stack;
  }

  /**
   * Adds crafting recipes for all wand types defined in WandType enum. Recipes are loaded from the
   * configuration file.
   */
  public static void addRecipes() {
    for (WandType type : WandType.values()) {
      String configKey = type.getConfigKeyPrefix();
      ConfigurationSection ingredientsConfig =
          IceCream.config.getConfigurationSection(configKey + "crafting.ingredients");
      List<String> shapeList = IceCream.config.getStringList(configKey + "crafting.shape");

      ItemStack wandItem = type.createItem();
      if (wandItem == null) continue;

      NamespacedKey recipeKey = new NamespacedKey(IceCream.instance, type.getId() + "_recipe");
      ShapedRecipe recipe = new ShapedRecipe(recipeKey, wandItem);

      recipe.shape(shapeList.toArray(new String[0]));

      if (ingredientsConfig != null) {
        for (String key : ingredientsConfig.getKeys(false)) {
          String materialName = ingredientsConfig.getString(key);
          Material material = Material.valueOf(materialName);
          recipe.setIngredient(key.charAt(0), material);
        }
      }

      IceCream.instance.getServer().addRecipe(recipe);
    }
  }
}
