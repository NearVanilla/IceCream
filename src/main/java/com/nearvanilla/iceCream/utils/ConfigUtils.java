package com.nearvanilla.iceCream.utils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared utilities for keeping the plugin config file up to date with the bundled config.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-31
 */
public final class ConfigUtils {

  private ConfigUtils() {}

  /**
   * Adds keys from the bundled config that are missing from the config file, keeping existing
   * values.
   *
   * @param plugin the plugin whose config is updated
   * @return the plugin config, reloaded when keys were added
   */
  public static FileConfiguration addMissingDefaults(JavaPlugin plugin) {
    FileConfiguration config = plugin.getConfig();
    List<String> missing = findMissingKeys(config);
    if (missing.isEmpty()) return config;

    // A file with a YAML error loads as empty, so saving would replace it with the defaults.
    if (!isValidYaml(new File(plugin.getDataFolder(), "config.yml"))) {
      plugin
          .getLogger()
          .warning("config.yml could not be parsed; missing config keys were not added.");
      return config;
    }

    // Write the defaults, then reload so reads with their own fallback, like
    // getBoolean(key, false), see the new values.
    config.options().copyDefaults(true);
    plugin.saveConfig();
    plugin.reloadConfig();

    // saveConfig only logs a failed write, so check the keys actually reached the file.
    FileConfiguration reloaded = plugin.getConfig();
    if (findMissingKeys(reloaded).isEmpty()) {
      plugin.getLogger().info(summarize(missing));
    } else {
      plugin.getLogger().warning("Missing config keys could not be saved to config.yml.");
    }

    return reloaded;
  }

  /**
   * Warns about module sections the bundled config does not have, such as removed or misspelled
   * modules.
   *
   * @param plugin the plugin whose config is checked
   */
  public static void warnUnknownModules(JavaPlugin plugin) {
    for (String name : findUnknownModules(plugin.getConfig())) {
      plugin
          .getLogger()
          .warning(
              "Config section 'modules."
                  + name
                  + "' does not match any module (removed or misspelled) and is ignored.");
    }
  }

  /**
   * Lists module sections in the config that are not in its defaults.
   *
   * @param config a config with its defaults set
   * @return the unknown module names
   */
  static List<String> findUnknownModules(Configuration config) {
    Configuration defaults = config.getDefaults();
    ConfigurationSection modules = config.getConfigurationSection("modules");
    if (defaults == null || modules == null) return List.of();

    return modules.getKeys(false).stream()
        .filter(name -> !defaults.contains("modules." + name))
        .toList();
  }

  /**
   * Lists keys in the config defaults that are not set in the config.
   *
   * @param config a config with its defaults set
   * @return the missing keys
   */
  static List<String> findMissingKeys(Configuration config) {
    Configuration defaults = config.getDefaults();
    if (defaults == null) return List.of();

    return defaults.getKeys(true).stream()
        // Skip sections with keys, since their keys are listed themselves.
        .filter(
            key ->
                !config.isSet(key)
                    && (!defaults.isConfigurationSection(key)
                        || defaults.getConfigurationSection(key).getKeys(false).isEmpty()))
        .toList();
  }

  /**
   * Builds a log message listing the sections of the added keys.
   *
   * @param missing the added keys
   * @return the log message
   */
  static String summarize(List<String> missing) {
    Set<String> sections = new LinkedHashSet<>();
    for (String key : missing) {
      // Group per module: modules.antiafk.messages.title -> modules.antiafk.
      String[] parts = key.split("\\.");
      sections.add(parts.length > 2 ? parts[0] + "." + parts[1] : parts[0]);
    }

    return "Added missing config keys in " + String.join(", ", sections) + ".";
  }

  /** Whether the file exists and parses as YAML. */
  static boolean isValidYaml(File file) {
    try {
      new YamlConfiguration().load(file);
      return true;
    } catch (IOException | InvalidConfigurationException exception) {
      return false;
    }
  }
}
