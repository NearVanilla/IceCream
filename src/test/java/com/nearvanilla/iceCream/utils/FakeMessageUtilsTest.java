package com.nearvanilla.iceCream.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FakeMessageUtilsTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // A player that keeps the plugin messages it is sent, which MockBukkit's player drops.
  private static final class MessagingPlayer extends PlayerMock {
    record Sent(Plugin source, String channel, byte[] data) {}

    final List<Sent> sent = new ArrayList<>();

    MessagingPlayer(ServerMock server, String name) {
      super(server, name, UUID.randomUUID());
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
      sent.add(new Sent(source, channel, message));
    }
  }

  private MessagingPlayer addMessagingPlayer(String name) {
    MessagingPlayer player = new MessagingPlayer(server, name);
    server.addPlayer(player);
    return player;
  }

  // broadcastFakeMessage

  @Test
  void broadcastReachesEveryPlayerExceptTheExcludedOne() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock first = server.addPlayer("First");
    PlayerMock second = server.addPlayer("Second");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> left the game");

    assertEquals("Leaver left the game", MessageTestUtils.nextMessage(first));
    assertEquals("Leaver left the game", MessageTestUtils.nextMessage(second));
    assertNull(excluded.nextComponentMessage());
  }

  @Test
  void broadcastSendsEachPlayerOneMessage() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> left the game");

    assertNotNull(other.nextComponentMessage());
    assertNull(other.nextComponentMessage());
  }

  @Test
  void broadcastUsesTheDisplayNameOfTheExcludedPlayer() {
    PlayerMock excluded = server.addPlayer("Leaver");
    excluded.displayName(Component.text("Captain Leaver"));
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> joined the game");

    assertEquals("Captain Leaver joined the game", MessageTestUtils.nextMessage(other));
  }

  @Test
  void broadcastKeepsTheStyleOfTheDisplayName() {
    PlayerMock excluded = server.addPlayer("Leaver");
    excluded.displayName(Component.text("Leaver", NamedTextColor.RED));
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> joined the game");

    Component message = other.nextComponentMessage();
    assertNotNull(message);
    assertEquals("<red>Leaver</red> joined the game", MiniMessage.miniMessage().serialize(message));
  }

  @Test
  void broadcastDoesNotParseTheDisplayNameAsMiniMessage() {
    PlayerMock excluded = server.addPlayer("Leaver");
    excluded.displayName(Component.text("<red>Mallory"));
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> joined the game");

    assertEquals("<red>Mallory joined the game", MessageTestUtils.nextMessage(other));
  }

  @Test
  void broadcastAppliesTheFormatStyling() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<yellow><player> left the game</yellow>");

    Component message = other.nextComponentMessage();
    assertNotNull(message);
    assertEquals("Leaver left the game", MessageTestUtils.plainText(message));
    assertEquals("<yellow>Leaver left the game", MiniMessage.miniMessage().serialize(message));
  }

  @Test
  void broadcastReplacesEveryOccurrenceOfThePlayerTag() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> and <player> again");

    assertEquals("Leaver and Leaver again", MessageTestUtils.nextMessage(other));
  }

  @Test
  void broadcastSendsAFormatWithoutThePlayerTagAsItIs() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock other = server.addPlayer("Other");

    FakeMessageUtils.broadcastFakeMessage(excluded, "Someone left");

    assertEquals("Someone left", MessageTestUtils.nextMessage(other));
  }

  @Test
  void broadcastWithNoOtherPlayersSendsNothing() {
    PlayerMock excluded = server.addPlayer("Leaver");

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> left the game");

    assertNull(excluded.nextComponentMessage());
  }

  @Test
  void broadcastSkipsPlayersWhoAreNoLongerOnline() {
    PlayerMock excluded = server.addPlayer("Leaver");
    PlayerMock gone = server.addPlayer("Gone");
    PlayerMock here = server.addPlayer("Here");
    gone.disconnect();

    FakeMessageUtils.broadcastFakeMessage(excluded, "<player> left the game");

    assertEquals("Leaver left the game", MessageTestUtils.nextMessage(here));
    assertNull(gone.nextComponentMessage());
  }

  // sendStateToVelocity

  private DataInputStream decode(MessagingPlayer.Sent message) {
    return new DataInputStream(new ByteArrayInputStream(message.data()));
  }

  @Test
  void sendsOneMessageOnTheGivenChannelFromThePlugin() {
    MessagingPlayer player = addMessagingPlayer("Hider");

    FakeMessageUtils.sendStateToVelocity(player, "icecream:spectator", true);

    assertEquals(1, player.sent.size());
    assertEquals("icecream:spectator", player.sent.getFirst().channel());
    assertSame(IceCream.instance, player.sent.getFirst().source());
  }

  @Test
  void writesThePlayerIdAndTheStateAndNothingElse() throws IOException {
    MessagingPlayer player = addMessagingPlayer("Hider");

    FakeMessageUtils.sendStateToVelocity(player, "icecream:spectator", true);

    DataInputStream in = decode(player.sent.getFirst());
    assertEquals(player.getUniqueId().toString(), in.readUTF());
    assertTrue(in.readBoolean());
    assertEquals(0, in.available());
  }

  @Test
  void writesAFalseState() throws IOException {
    MessagingPlayer player = addMessagingPlayer("Hider");

    FakeMessageUtils.sendStateToVelocity(player, "icecream:spectator", false);

    DataInputStream in = decode(player.sent.getFirst());
    assertEquals(player.getUniqueId().toString(), in.readUTF());
    assertFalse(in.readBoolean());
  }

  @Test
  void eachPlayerSendsTheirOwnId() throws IOException {
    MessagingPlayer first = addMessagingPlayer("First");
    MessagingPlayer second = addMessagingPlayer("Second");

    FakeMessageUtils.sendStateToVelocity(second, "icecream:spectator", true);

    assertEquals(0, first.sent.size());
    assertEquals(second.getUniqueId().toString(), decode(second.sent.getFirst()).readUTF());
  }

  @Test
  void eachCallSendsItsOwnState() throws IOException {
    MessagingPlayer player = addMessagingPlayer("Hider");

    FakeMessageUtils.sendStateToVelocity(player, "icecream:spectator", true);
    FakeMessageUtils.sendStateToVelocity(player, "icecream:spectator", false);

    assertEquals(2, player.sent.size());
    DataInputStream first = decode(player.sent.get(0));
    first.readUTF();
    assertTrue(first.readBoolean());
    DataInputStream second = decode(player.sent.get(1));
    second.readUTF();
    assertFalse(second.readBoolean());
  }
}
