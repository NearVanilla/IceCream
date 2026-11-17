package com.nearvanilla.iceCream.modules.example.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A command is tested by calling its annotated method directly. The Cloud framework is not
 * involved, so there is no command manager to set up, only the sender.
 */
class ExampleCommandTest {
  private ServerMock server;
  private final ExampleCommand command = new ExampleCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  // The method only reads the sender from the source, so a mock source that returns it is enough.
  private void run(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.exampleCommand(source);
  }

  private String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @Test
  void sendsTheExampleMessageToAPlayer() {
    PlayerMock player = server.addPlayer();

    run(player);

    assertEquals("This is an example command!", plain(player.nextComponentMessage()));
  }

  @Test
  void sendsExactlyOneMessageToAPlayer() {
    PlayerMock player = server.addPlayer();

    run(player);

    player.nextComponentMessage();
    assertNull(player.nextComponentMessage());
  }

  @Test
  void tellsTheConsoleThatOnlyPlayersCanUseIt() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    run(console);

    assertEquals("This command can only be used by players.", console.nextMessage());
  }
}
