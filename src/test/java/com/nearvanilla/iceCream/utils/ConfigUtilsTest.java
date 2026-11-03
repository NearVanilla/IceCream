package com.nearvanilla.iceCream.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigUtilsTest {
  private static final String DEFAULTS =
      """
      modules:
        hat:
          enabled: true
        antiafk:
          enabled: true
          messages:
            title: Hi
        maptools:
          icons: {}
      """;

  private static YamlConfiguration configWithDefaults(String yaml)
      throws InvalidConfigurationException {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.loadFromString(DEFAULTS);
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(yaml);
    config.setDefaults(defaults);
    return config;
  }

  @Test
  void nothingMissingWhenConfigMatchesDefaults() throws Exception {
    assertEquals(List.of(), ConfigUtils.findMissingKeys(configWithDefaults(DEFAULTS)));
  }

  @Test
  void findsKeysOfAMissingModule() throws Exception {
    YamlConfiguration config =
        configWithDefaults(
            """
            modules:
              hat:
                enabled: true
              maptools:
                icons: {}
            """);
    assertEquals(
        List.of("modules.antiafk.enabled", "modules.antiafk.messages.title"),
        ConfigUtils.findMissingKeys(config));
  }

  @Test
  void keepsExistingValues() throws Exception {
    YamlConfiguration config =
        configWithDefaults(
            """
            modules:
              hat:
                enabled: false
              antiafk:
                enabled: false
                messages:
                  title: Custom
              maptools:
                icons:
                  pin: red_dye
            """);
    assertEquals(List.of(), ConfigUtils.findMissingKeys(config));
  }

  @Test
  void findsMissingEmptySection() throws Exception {
    YamlConfiguration config =
        configWithDefaults(
            """
            modules:
              hat:
                enabled: true
              antiafk:
                enabled: true
                messages:
                  title: Hi
            """);
    assertEquals(List.of("modules.maptools.icons"), ConfigUtils.findMissingKeys(config));
  }

  @Test
  void findsRemovedAndMisspelledModules() throws Exception {
    YamlConfiguration config =
        configWithDefaults(
            """
            modules:
              vanish:
                enabled: true
              hat:
                enabled: true
              antiafkk:
                enabled: true
            """);
    assertEquals(List.of("vanish", "antiafkk"), ConfigUtils.findUnknownModules(config));
  }

  @Test
  void noUnknownModulesWhenAllAreBundled() throws Exception {
    assertEquals(List.of(), ConfigUtils.findUnknownModules(configWithDefaults(DEFAULTS)));
    assertEquals(List.of(), ConfigUtils.findUnknownModules(configWithDefaults("other: 1\n")));
  }

  @Test
  void summarizesBySection() {
    assertEquals(
        "Added missing config keys in modules.antiafk, modules.hat.",
        ConfigUtils.summarize(
            List.of("modules.antiafk.enabled", "modules.antiafk.messages.title", "modules.hat.x")));
    assertEquals(
        "Added missing config keys in debug.", ConfigUtils.summarize(List.of("debug.level")));
  }

  @Test
  void rejectsBrokenOrMissingYaml(@TempDir Path dir) throws Exception {
    Path valid = Files.writeString(dir.resolve("valid.yml"), DEFAULTS);
    Path broken = Files.writeString(dir.resolve("broken.yml"), "modules:\n  hat: [unclosed\n");
    assertTrue(ConfigUtils.isValidYaml(valid.toFile()));
    assertFalse(ConfigUtils.isValidYaml(broken.toFile()));
    assertFalse(ConfigUtils.isValidYaml(new File(dir.toFile(), "absent.yml")));
  }
}
