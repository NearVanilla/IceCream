package com.nearvanilla.iceCream.modules.wanderingTrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.io.IOException;
import org.junit.jupiter.api.Test;

// HeadTradeData only holds what the data file says, so these tests read it the way the module does.
class HeadTradeDataTest {

  @Test
  void aNewTradeHasNoValues() {
    HeadTradeData data = new HeadTradeData();

    assertEquals(0, data.maxUses);
    assertEquals(0, data.headCount);
    assertNull(data.secondaryCost);
    assertNull(data.name);
    assertNull(data.texture);
  }

  @Test
  void readsEveryFieldFromTheDataFileNames() throws IOException {
    String json =
        """
        {
          "maxUses": 3,
          "secondaryCost": "minecraft:chest",
          "headCount": 8,
          "name": "\\"Mini Chest\\"",
          "texture": "abc="
        }
        """;

    HeadTradeData data =
        JsonLoader.load(WanderingTradesTestUtils.stream(json), HeadTradeData.class);

    assertEquals(3, data.maxUses);
    assertEquals("minecraft:chest", data.secondaryCost);
    assertEquals(8, data.headCount);
    assertEquals("\"Mini Chest\"", data.name);
    assertEquals("abc=", data.texture);
  }

  @Test
  void aFieldMissingFromTheFileKeepsItsDefault() throws IOException {
    HeadTradeData data =
        JsonLoader.load(WanderingTradesTestUtils.stream("{\"name\": \"x\"}"), HeadTradeData.class);

    assertEquals("x", data.name);
    assertEquals(0, data.maxUses);
    assertEquals(0, data.headCount);
    assertNull(data.secondaryCost);
    assertNull(data.texture);
  }
}
