package com.nearvanilla.iceCream.modules.bannerLayers;

import com.nearvanilla.iceCream.IceCream;
import java.util.function.Consumer;

/**
 * Settings for the banner layers module, read from {@code modules.bannerlayers}.
 *
 * @param maxLayers the most layers a banner may get in the loom, from {@value #MIN_LAYERS} to
 *     {@value #MAX_LAYERS}
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public record BannerLayersConfig(int maxLayers) {
  /** The vanilla loom limit. A lower setting would change nothing, so it is the minimum. */
  public static final int MIN_LAYERS = BannerLayerStore.LOOM_LIMIT;

  /** The most layers the client draws on a banner or shield. */
  public static final int MAX_LAYERS = 16;

  private static final String KEY = "modules.bannerlayers.max-layers";

  /**
   * Reads the settings from the plugin config.
   *
   * @return the settings
   */
  public static BannerLayersConfig load() {
    return new BannerLayersConfig(
        parseMaxLayers(IceCream.config.get(KEY, MAX_LAYERS), IceCream.logger::warning));
  }

  /**
   * Reads the layer limit. A value that is not a whole number falls back to {@value #MAX_LAYERS},
   * and one outside {@value #MIN_LAYERS} to {@value #MAX_LAYERS} is clamped, each with a warning.
   *
   * @param value the configured value
   * @param warn receives a warning when the value is not used as given
   * @return the layer limit
   */
  public static int parseMaxLayers(Object value, Consumer<String> warn) {
    if (!(value instanceof Integer number)) {
      warn.accept("Invalid " + KEY + "; using " + MAX_LAYERS + ".");
      return MAX_LAYERS;
    }
    if (number < MIN_LAYERS || number > MAX_LAYERS) {
      int clamped = Math.clamp(number, MIN_LAYERS, MAX_LAYERS);
      warn.accept(
          KEY
              + " must be from "
              + MIN_LAYERS
              + " to "
              + MAX_LAYERS
              + " (the most the client draws); using "
              + clamped
              + ".");
      return clamped;
    }
    return number;
  }
}
