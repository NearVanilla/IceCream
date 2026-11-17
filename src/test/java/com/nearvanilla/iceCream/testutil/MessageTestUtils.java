package com.nearvanilla.iceCream.testutil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Helpers for reading the messages a {@link PlayerMock} or the console received, as the plain text
 * a reader would see, so a test can assert the wording without comparing styled components.
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
    return plainText(player.nextComponentMessage());
  }

  /**
   * Takes the next message the console received and returns it as plain text, without any colours,
   * click events or hover events. Each call consumes one message, in the order they were sent.
   *
   * @param console the console sender that received the messages
   * @return the plain text of the next message, or null when the console has no more messages
   */
  public static String nextMessage(ConsoleCommandSenderMock console) {
    return plainText(console.nextComponentMessage());
  }

  /**
   * Returns the plain text of a message, without any colours, click events or hover events.
   *
   * @param message the message to read, which may be null
   * @return the plain text, or null when the message is null
   */
  public static String plainText(Component message) {
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }
}
