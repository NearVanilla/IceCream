package com.nearvanilla.iceCream.modules.silenceMobs;

import com.nearvanilla.iceCream.IceCream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

/**
 * Settings for the silence mobs module, read from {@code modules.silencemobs}.
 *
 * @param triggerNames name tag names that silence a mob, in lower case
 * @param silencedName the name given to a mob once it is silenced, or {@code null} to keep the name
 *     from the name tag
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public record SilenceMobsConfig(Set<String> triggerNames, @Nullable Component silencedName) {
  private static final String CONFIG = "modules.silencemobs.";
  private static final List<String> DEFAULT_TRIGGER_NAMES = List.of("silence me", "silence_me");
  private static final String DEFAULT_SILENCED_NAME = "silenced";

  /**
   * Creates the settings, lower casing the trigger names.
   *
   * @param triggerNames name tag names that silence a mob, in any case
   * @param silencedName the name given to a mob once it is silenced, or {@code null} to keep the
   *     name from the name tag
   */
  public SilenceMobsConfig {
    triggerNames =
        triggerNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Reads the settings from the plugin config. The silenced name is MiniMessage, and an empty one
   * keeps the name from the name tag.
   *
   * @return the settings
   */
  public static SilenceMobsConfig load() {
    List<String> triggerNames =
        IceCream.config.contains(CONFIG + "trigger-names")
            ? IceCream.config.getStringList(CONFIG + "trigger-names")
            : DEFAULT_TRIGGER_NAMES;
    String silencedName =
        IceCream.config.getString(CONFIG + "silenced-name", DEFAULT_SILENCED_NAME);
    return new SilenceMobsConfig(
        Set.copyOf(triggerNames),
        silencedName == null || silencedName.isEmpty()
            ? null
            : MiniMessage.miniMessage().deserialize(silencedName));
  }

  /**
   * Checks whether a name is one of the trigger names, ignoring case and formatting.
   *
   * @param name the name to check, such as a name tag's name
   * @return true if the name silences a mob
   */
  public boolean isTrigger(@Nullable Component name) {
    if (name == null) return false;
    String plain = PlainTextComponentSerializer.plainText().serialize(name);
    return triggerNames.contains(plain.toLowerCase(Locale.ROOT));
  }
}
