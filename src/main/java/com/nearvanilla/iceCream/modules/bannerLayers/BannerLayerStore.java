package com.nearvanilla.iceCream.modules.bannerLayers;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hides the layers of a banner from the loom, and puts them back.
 *
 * <p>The loom, on both the server and the client, refuses a banner that already has 6 layers. To
 * get past it, the banner in the loom is swapped for a copy that shows only its first 5 layers and
 * remembers all of them at {@code <namespace>:banner_layers}, as a list of {@code <pattern>
 * <color>} entries. Revealing a copy puts the remembered layers back, followed by any layers the
 * loom added on top of the 5 it could see, so it also turns the loom's result into the real banner.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class BannerLayerStore {
  /** The number of layers at which the loom refuses a banner. */
  public static final int LOOM_LIMIT = 6;

  /** The number of layers a hidden copy shows, one below the loom's limit. */
  public static final int VISIBLE_LAYERS = LOOM_LIMIT - 1;

  private static final PersistentDataType<List<String>, List<String>> STRINGS =
      PersistentDataType.LIST.strings();

  private final NamespacedKey layersKey;
  private final Registry<PatternType> patterns;

  /**
   * Creates the store.
   *
   * @param namespace the namespace of the layers key, normally the plugin's
   * @param patterns the banner pattern registry, used to save and read pattern keys
   */
  public BannerLayerStore(String namespace, Registry<PatternType> patterns) {
    this.layersKey = new NamespacedKey(namespace, "banner_layers");
    this.patterns = patterns;
  }

  /**
   * Reads the layers of a banner.
   *
   * @param stack the item
   * @return its layers, empty when it has none
   */
  public static List<Pattern> layers(ItemStack stack) {
    if (stack == null) return List.of();
    BannerPatternLayers layers = stack.getData(DataComponentTypes.BANNER_PATTERNS);
    return layers == null ? List.of() : layers.patterns();
  }

  /**
   * Checks whether an item is a banner the loom would refuse, but which may still get more layers.
   *
   * @param stack the item in the loom's banner slot
   * @param maxLayers the most layers a banner may have
   * @return true when the item should be hidden before the loom sees it
   */
  public boolean shouldHide(ItemStack stack, int maxLayers) {
    if (stack == null || !Tag.ITEMS_BANNERS.isTagged(stack.getType()) || isHidden(stack)) {
      return false;
    }
    int count = layers(stack).size();
    return count >= LOOM_LIMIT && count < maxLayers;
  }

  /**
   * Checks whether an item is a hidden copy, or a loom result made from one.
   *
   * @param stack the item
   * @return true when the item remembers hidden layers
   */
  public boolean isHidden(ItemStack stack) {
    return stack != null
        && !stack.isEmpty()
        && stack.getPersistentDataContainer().has(layersKey, STRINGS);
  }

  /**
   * Makes a copy of a banner that shows only its first {@value #VISIBLE_LAYERS} layers and
   * remembers all of them.
   *
   * @param banner the banner
   * @return the hidden copy
   */
  public ItemStack hide(ItemStack banner) {
    List<Pattern> all = layers(banner);
    List<String> saved = new ArrayList<>(all.size());
    for (Pattern pattern : all) {
      saved.add(write(pattern));
    }
    ItemStack copy = banner.clone();
    copy.editPersistentDataContainer(pdc -> pdc.set(layersKey, STRINGS, saved));
    setLayers(copy, all.subList(0, Math.min(VISIBLE_LAYERS, all.size())));
    return copy;
  }

  /**
   * Puts back the layers of a hidden copy: the remembered layers, then any the loom added after the
   * {@value #VISIBLE_LAYERS} it could see. Other items are returned unchanged.
   *
   * @param stack a hidden copy, or a loom result made from one
   * @return the real banner
   */
  public ItemStack reveal(ItemStack stack) {
    if (!isHidden(stack)) return stack;
    List<String> saved = stack.getPersistentDataContainer().get(layersKey, STRINGS);
    List<Pattern> current = layers(stack);
    List<Pattern> restored = new ArrayList<>();
    if (saved != null) {
      for (String entry : saved) {
        Pattern pattern = read(entry);
        if (pattern != null) restored.add(pattern);
      }
    }
    if (current.size() > VISIBLE_LAYERS) {
      restored.addAll(current.subList(VISIBLE_LAYERS, current.size()));
    }
    ItemStack copy = stack.clone();
    copy.editPersistentDataContainer(pdc -> pdc.remove(layersKey));
    setLayers(copy, restored);
    return copy;
  }

  /** Changes only the layers, leaving every other part of the item as it was. */
  private static void setLayers(ItemStack stack, List<Pattern> layers) {
    stack.setData(
        DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(layers));
  }

  private String write(Pattern pattern) {
    return patterns.getKeyOrThrow(pattern.getPattern()).asString()
        + " "
        + pattern.getColor().name().toLowerCase(Locale.ROOT);
  }

  /** Reads a saved layer, or returns null when its pattern or color no longer exists. */
  private Pattern read(String entry) {
    String[] parts = entry.split(" ", 2);
    if (parts.length != 2) return null;
    NamespacedKey key = NamespacedKey.fromString(parts[0]);
    PatternType type = key == null ? null : patterns.get(key);
    if (type == null) return null;
    try {
      return new Pattern(DyeColor.valueOf(parts[1].toUpperCase(Locale.ROOT)), type);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
