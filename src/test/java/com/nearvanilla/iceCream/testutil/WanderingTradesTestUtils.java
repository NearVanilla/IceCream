package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.wanderingTrades.HeadTradeData;
import com.nearvanilla.iceCream.modules.wanderingTrades.HeadUtils;
import com.nearvanilla.iceCream.modules.wanderingTrades.WanderingTradesModule;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/**
 * Helpers for testing the wandering trades module, which keeps its head trades and its stonecutter
 * recipe keys in static state that outlives a test.
 */
public final class WanderingTradesTestUtils {
  /** The configuration of an enabled wandering trades module with the shipped defaults. */
  public static final String CONFIG =
      """
      modules:
        wanderingtrades:
          enabled: true
          min-heads: 5
          max-heads: 10
          stonecutter: true
      """;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private WanderingTradesTestUtils() {}

  /**
   * Builds a head trade the way the bundled data file spells one.
   *
   * @param name the head name, as written in the file, so usually wrapped in double quotes
   * @param secondaryCost the block the trader also asks for, such as {@code minecraft:chest}
   * @param headCount how many heads one trade gives
   * @param maxUses how many times the trade can be used
   * @return the trade, with a valid texture derived from the name
   */
  public static HeadTradeData trade(String name, String secondaryCost, int headCount, int maxUses) {
    HeadTradeData data = new HeadTradeData();
    data.name = name;
    data.secondaryCost = secondaryCost;
    data.headCount = headCount;
    data.maxUses = maxUses;
    data.texture = texture(name);
    return data;
  }

  /**
   * Builds a texture value like Mojang's: base64 of a JSON document that points at a skin.
   *
   * @param skinId the id that ends the skin URL
   * @return the base64 texture value
   */
  public static String texture(String skinId) {
    String json =
        "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
            + skinId.hashCode()
            + "\"}}}";
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Serializes trades to the JSON the module loads.
   *
   * @param trades the trades to write
   * @return a stream of the JSON array
   */
  public static InputStream json(HeadTradeData... trades) {
    return new ByteArrayInputStream(MAPPER.writeValueAsBytes(List.of(trades)));
  }

  /**
   * Turns JSON text into a stream.
   *
   * @param json the text, which may be malformed on purpose
   * @return a stream of its UTF-8 bytes
   */
  public static InputStream stream(String json) {
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Empties the head trade pool and removes the stonecutter recipes the module registered. Call it
   * before and after a test that loads trades, while the server mock is still running.
   */
  public static void reset() {
    WanderingTradesModule.headTradePool.clear();
    HeadUtils.unregisterStonecutterRecipes();
  }
}
