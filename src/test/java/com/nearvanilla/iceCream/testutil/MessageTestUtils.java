package com.nearvanilla.iceCream.testutil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Helpers for reading the messages a {@link PlayerMock} received, as the plain text a player would
 * read, so a test can assert the wording without comparing styled components.
 */
public final class MessageTestUtils {

  private MessageTestUtils() {}

  /**
   * Takes the next message the player received and returns it as plain text, without any colours,
   * click events or hover events. Each call consumes one message, in the order they were sent.
   *
   * @param player the player that received the messages
   * @return the plain text of the next message, or null when the player has no more messages
   */
  public static String nextMessage(PlayerMock player) {
    Component message = player.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }
}
