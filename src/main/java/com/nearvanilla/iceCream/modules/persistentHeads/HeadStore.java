package com.nearvanilla.iceCream.modules.persistentHeads;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original files: me.machinemaker.papertweaks.modules.other.persistentheads.BlockListener,
 *   me.machinemaker.papertweaks.pdc.types.ComponentDataType and ComponentListDataType
 * Adaptations: lore is stored as a list of JSON strings under the plugin's own namespace;
 *   PaperTweaks' vanillatweaks:head_lore and vanillatweaks:head_name entries are only read, so
 *   heads placed while PaperTweaks ran keep their data.
 */
/**
 * Stores the lore of a placed player head in the head's {@link PersistentDataContainer}, and reads
 * the entries PaperTweaks' Persistent Heads module left on heads placed before the switch.
 *
 * <p>Lore is stored at {@code <namespace>:head_lore} as a list of JSON text components.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 */
public final class HeadStore {
  private static final NamespacedKey LEGACY_LORE = new NamespacedKey("vanillatweaks", "head_lore");
  private static final NamespacedKey LEGACY_NAME = new NamespacedKey("vanillatweaks", "head_name");
  private static final PersistentDataType<List<String>, List<String>> STRINGS =
      PersistentDataType.LIST.strings();

  private final NamespacedKey loreKey;

  /**
   * Creates the store.
   *
   * @param namespace the namespace of the lore key, normally the plugin's
   */
  public HeadStore(String namespace) {
    this.loreKey = new NamespacedKey(namespace, "head_lore");
  }

  /**
   * Saves the lore of the item a head was placed from.
   *
   * @param pdc the placed head's container
   * @param lore the item's lore
   */
  public void save(PersistentDataContainer pdc, List<Component> lore) {
    List<String> lines = new ArrayList<>(lore.size());
    for (Component line : lore) {
      lines.add(GsonComponentSerializer.gson().serialize(line));
    }
    pdc.set(loreKey, STRINGS, lines);
  }

  /**
   * Reads what a placed head remembers. Lore saved by IceCream wins over lore saved by PaperTweaks.
   *
   * @param pdc the placed head's container
   * @return the stored data, empty when nothing readable is stored
   */
  public StoredHead read(PersistentDataContainer pdc) {
    List<Component> lore = readLore(pdc);
    if (lore == null) lore = readLegacyLore(pdc);
    return new StoredHead(lore, readLegacyName(pdc));
  }

  private List<Component> readLore(PersistentDataContainer pdc) {
    List<String> lines = pdc.get(loreKey, STRINGS);
    if (lines == null) return null;
    List<Component> lore = new ArrayList<>(lines.size());
    for (String line : lines) {
      lore.add(GsonComponentSerializer.gson().deserialize(line));
    }
    return lore;
  }

  private static List<Component> readLegacyLore(PersistentDataContainer pdc) {
    String raw = legacyString(pdc, LEGACY_LORE);
    if (raw == null) return null;
    try {
      JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
      List<Component> lore = new ArrayList<>(array.size());
      for (JsonElement element : array) {
        lore.add(legacyComponent(element));
      }
      return lore;
    } catch (JsonParseException | IllegalStateException exception) {
      return null;
    }
  }

  private static Component readLegacyName(PersistentDataContainer pdc) {
    String raw = legacyString(pdc, LEGACY_NAME);
    if (raw == null) return null;
    try {
      return GsonComponentSerializer.gson().deserialize(raw);
    } catch (JsonParseException exception) {
      return LegacyComponentSerializer.legacySection().deserialize(raw);
    }
  }

  /** PaperTweaks wrote plain section-coded strings before it stored JSON components. */
  private static Component legacyComponent(JsonElement element) {
    if (element.isJsonPrimitive()) {
      return LegacyComponentSerializer.legacySection().deserialize(element.getAsString());
    }
    return GsonComponentSerializer.gson().deserializeFromTree(element);
  }

  private static String legacyString(PersistentDataContainer pdc, NamespacedKey key) {
    return pdc.has(key, PersistentDataType.STRING) ? pdc.get(key, PersistentDataType.STRING) : null;
  }
}
