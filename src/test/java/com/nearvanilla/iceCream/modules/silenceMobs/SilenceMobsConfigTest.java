package com.nearvanilla.iceCream.modules.silenceMobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SilenceMobsConfigTest {
  private FileConfiguration previousConfig;

  @BeforeEach
  void setUp() {
    previousConfig = IceCream.config;
    IceCream.config = new YamlConfiguration();
  }

  @AfterEach
  void tearDown() {
    IceCream.config = previousConfig;
  }

  @Test
  void missingSettingsUsePaperTweaksDefaults() {
    SilenceMobsConfig config = SilenceMobsConfig.load();

    assertEquals(Set.of("silence me", "silence_me"), config.triggerNames());
    assertEquals(Component.text("silenced"), config.silencedName());
  }

  @Test
  void readsConfiguredSettings() {
    IceCream.config.set("modules.silencemobs.trigger-names", List.of("Hush"));
    IceCream.config.set("modules.silencemobs.silenced-name", "<red>quiet</red>");

    SilenceMobsConfig config = SilenceMobsConfig.load();

    assertEquals(Set.of("hush"), config.triggerNames());
    assertEquals(Component.text("quiet", NamedTextColor.RED), config.silencedName());
  }

  @Test
  void emptySilencedNameKeepsTheNameTagName() {
    IceCream.config.set("modules.silencemobs.silenced-name", "");

    assertNull(SilenceMobsConfig.load().silencedName());
  }

  @Test
  void emptyTriggerListSilencesNothing() {
    IceCream.config.set("modules.silencemobs.trigger-names", List.of());

    assertFalse(SilenceMobsConfig.load().isTrigger(Component.text("silence me")));
  }

  @Test
  void triggerMatchesPlainTextIgnoringCase() {
    SilenceMobsConfig config = new SilenceMobsConfig(Set.of("Silence Me"), null);

    assertTrue(config.isTrigger(Component.text("SILENCE me", NamedTextColor.GOLD)));
    assertTrue(config.isTrigger(Component.text("silence ").append(Component.text("me"))));
    assertFalse(config.isTrigger(Component.text("silence me please")));
    assertFalse(config.isTrigger(null));
  }
}
