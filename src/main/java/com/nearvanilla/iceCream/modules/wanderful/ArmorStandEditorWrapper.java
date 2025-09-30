package com.nearvanilla.iceCream.modules.wanderful;

import com.nearvanilla.iceCream.IceCream;
import java.lang.reflect.Field;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * ArmorStandEditorWrapper is a utility class for interacting with the ArmorStandEditor plugin. It
 * provides methods to retrieve and configure the edit tool used for armor stand editing.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public class ArmorStandEditorWrapper {
  private ArmorStandEditorWrapper() {}

  private static Plugin getPlugin() {
    return IceCream.instance.getServer().getPluginManager().getPlugin("ArmorStandEditor");
  }

  public static NamespacedKey getFlag() {
    Plugin plugin = getPlugin();
    if (plugin == null) {
      IceCream.instance.getLogger().warning("ArmorStandEditor plugin not found.");
      return null;
    }

    // Get the field "editToolKey" from the plugin instance
    try {
      Field toolField = plugin.getClass().getField("editToolKey");
      return (NamespacedKey) toolField.get(plugin);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      IceCream.instance
          .getLogger()
          .warning("Failed to access 'editToolKey' in ArmorStandEditor: " + e.getMessage());
      return null;
    }
  }

  public static void configure() {
    Plugin plugin = getPlugin();

    if (plugin == null) {
      IceCream.instance.getLogger().warning("ArmorStandEditor plugin not found.");
      return;
    }

    // Get the edit tool from config
    String materialName = IceCream.config.getString("modules.wanderful.armor_stand.type");
    Material editTool = Material.valueOf(materialName);

    Map<String, Object> toolConfig =
        Map.of(
            "editTool", editTool,
            "requireToolData", false,
            "requireToolLore", false,
            "requireToolKey", true);

    // Apply the configuration to the armor stand editor plugin
    for (var entry : toolConfig.entrySet()) {
      try {
        Field field = plugin.getClass().getDeclaredField(entry.getKey());
        boolean wasAccessible = field.canAccess(plugin);

        field.setAccessible(true);
        field.set(plugin, entry.getValue());
        field.setAccessible(wasAccessible);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        IceCream.instance
            .getLogger()
            .warning(
                "Failed to set '" + entry.getKey() + "' in ArmorStandEditor: " + e.getMessage());
      }
    }
  }
}
