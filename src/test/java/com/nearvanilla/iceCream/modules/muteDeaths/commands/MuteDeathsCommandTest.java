package com.nearvanilla.iceCream.modules.muteDeaths.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

class MuteDeathsCommandTest {
  private static final String MUTED = "✔ Your death messages are now muted.";
  private static final String UNMUTED = "✖ Your death messages are now visible to others.";

  private ServerMock server;
  private PlayerMock player;
  private final MuteDeathsCommand command = new MuteDeathsCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer("Sleeper");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private void run(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.muteDeathMessages(source);
  }

  private Boolean storedPreference(Player target) {
    return target
        .getPersistentDataContainer()
        .get(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN);
  }

  @Test
  void firstUseMutesDeathMessages() {
    run(player);

    assertEquals(Boolean.TRUE, storedPreference(player));
  }

  @Test
  void firstUseTellsThePlayerTheyAreMuted() {
    run(player);

    assertEquals(MUTED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void secondUseUnmutesDeathMessages() {
    run(player);
    nextMessage(player);

    run(player);

    assertEquals(Boolean.FALSE, storedPreference(player));
  }

  @Test
  void secondUseTellsThePlayerTheyAreVisible() {
    run(player);
    nextMessage(player);

    run(player);

    assertEquals(UNMUTED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void thirdUseMutesDeathMessagesAgain() {
    run(player);
    run(player);
    nextMessage(player);
    nextMessage(player);

    run(player);

    assertEquals(Boolean.TRUE, storedPreference(player));
    assertEquals(MUTED, nextMessage(player));
  }

  @Test
  void aStoredFalsePreferenceIsMutedByTheNextUse() {
    player
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, false);

    run(player);

    assertEquals(Boolean.TRUE, storedPreference(player));
    assertEquals(MUTED, nextMessage(player));
  }

  @Test
  void aStoredTruePreferenceIsUnmutedByTheNextUse() {
    player
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, true);

    run(player);

    assertEquals(Boolean.FALSE, storedPreference(player));
    assertEquals(UNMUTED, nextMessage(player));
  }

  @Test
  void onlyTheSendersPreferenceChanges() {
    PlayerMock other = server.addPlayer("Other");

    run(player);

    assertNull(storedPreference(other));
    assertNull(nextMessage(other));
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    run(console);

    Component message = console.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "This command can only be used by players.",
        PlainTextComponentSerializer.plainText().serialize(message));
    assertNull(console.nextComponentMessage());
    assertNull(storedPreference(player));
    assertNull(nextMessage(player));
  }

  @Test
  void reportsAnErrorWhenAStoredPreferenceCannotBeRead() {
    // A container that claims to hold the key but returns nothing for it, which a real container
    // never does.
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    when(container.has(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN)).thenReturn(true);
    when(container.get(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN)).thenReturn(null);
    Player broken = mock(Player.class);
    when(broken.getPersistentDataContainer()).thenReturn(container);

    run(broken);

    ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
    verify(broken).sendMessage(message.capture());
    assertEquals(
        "An error occurred while trying to mute death messages. Please contact the server manager.",
        PlainTextComponentSerializer.plainText().serialize(message.getValue()));
    verify(container, never()).set(any(), any(), any());
  }
}
