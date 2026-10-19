package com.nearvanilla.iceCream.modules.deathDb.gui;

import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Shared item, text and formatting helpers for the death GUIs. */
final class DeathGuiItems {
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private DeathGuiItems() {}

  /** Deserializes a MiniMessage template with italics disabled (item text is italic by default). */
  static Component line(String template, TagResolver... resolvers) {
    return MiniMessage.miniMessage().deserialize("<!italic>" + template, resolvers);
  }

  static ItemStack named(Material material, Component name, Component... lore) {
    ItemStack stack = new ItemStack(material);
    stack.editMeta(
        meta -> {
          meta.displayName(name);
          if (lore.length > 0) {
            meta.lore(List.of(lore));
          }
        });
    return stack;
  }

  static ItemStack filler() {
    return named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
  }

  static String formatTime(long epochMillis) {
    return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
  }

  static String formatPosition(DeathSummary death) {
    return (long) Math.floor(death.x())
        + ", "
        + (long) Math.floor(death.y())
        + ", "
        + (long) Math.floor(death.z());
  }
}
