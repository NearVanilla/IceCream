package com.nearvanilla.iceCream.modules.mapTools.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nearvanilla.iceCream.libs.JsonLoader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The mod reads these exact field names. */
class MapToolsRepliesTest {

  private static String json(Object message) {
    return new String(JsonLoader.toBytes(message), StandardCharsets.UTF_8);
  }

  @Test
  void hello() {
    assertEquals(
        "{\"v\":1,\"type\":\"hello\"}",
        json(new MapToolsReplies.Hello(MapToolsReplies.VERSION, "hello")));
  }

  @Test
  void changed() {
    assertEquals(
        "{\"v\":1,\"type\":\"changed\",\"change\":\"renamed\",\"kind\":\"claim\",\"id\":\"new\","
            + "\"previousId\":\"old\"}",
        json(new MapToolsReplies.Changed(1, "changed", "renamed", "claim", "new", "old")));
  }

  @Test
  void error() {
    assertEquals(
        "{\"v\":1,\"type\":\"error\",\"message\":\"There is no claim called x.\"}",
        json(new MapToolsReplies.Error(1, "error", "There is no claim called x.")));
  }
}
