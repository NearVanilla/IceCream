package com.nearvanilla.iceCream.modules.mapTools.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MapToolsChannelTest {

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void acceptsMapToolsCommands() {
    String command = "maptools create claim polygon \"0 0;10 0;10 10\" spawn gold Spawn Town";
    assertEquals(command, MapToolsChannel.commandFrom(bytes(command)));
  }

  @Test
  void acceptsAnyTextInLabels() {
    String command = "maptools set claim spawn label Café 'Nord' – ☆";
    assertEquals(command, MapToolsChannel.commandFrom(bytes(command)));
  }

  /** The channel only runs map tools commands, never others. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "op Dynant",
        "minecraft:op Dynant",
        "maptools",
        "maptoolsx list",
        " maptools list claims",
        "/maptools list claims",
        ""
      })
  void refusesOtherCommands(String command) {
    assertNull(MapToolsChannel.commandFrom(bytes(command)));
  }

  /** One line without formatting codes, like typed chat. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "maptools set claim a label x\nop Dynant",
        "maptools set claim a label x\r",
        "maptools set claim a label \u0000",
        "maptools set claim a label §cred"
      })
  void refusesCharactersChatDoesNotAllow(String command) {
    assertNull(MapToolsChannel.commandFrom(bytes(command)));
  }

  @Test
  void refusesMessagesOver16Kilobytes() {
    String fits = "maptools " + "a".repeat(16_384 - 9);
    assertEquals(fits, MapToolsChannel.commandFrom(bytes(fits)));
    assertNull(MapToolsChannel.commandFrom(bytes(fits + "a")));
  }

  /** A claim edit from the mod sends a burst of commands; a flood beyond that is refused. */
  @Test
  void allowsABurstThenRefuses() {
    UUID player = UUID.randomUUID();
    for (int i = 0; i < 16; i++) assertTrue(MapToolsChannel.allow(player), "command " + i);
    assertFalse(MapToolsChannel.allow(player));
    MapToolsChannel.forget(player);
    assertTrue(MapToolsChannel.allow(player));
    MapToolsChannel.forget(player);
  }

  @Test
  void playersHaveTheirOwnAllowance() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    for (int i = 0; i < 16; i++) MapToolsChannel.allow(first);
    assertFalse(MapToolsChannel.allow(first));
    assertTrue(MapToolsChannel.allow(second));
    MapToolsChannel.forget(first);
    MapToolsChannel.forget(second);
  }
}
