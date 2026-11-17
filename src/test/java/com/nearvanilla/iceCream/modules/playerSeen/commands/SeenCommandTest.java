package com.nearvanilla.iceCream.modules.playerSeen.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.PlayerSeenTestUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SeenCommandTest {
  private ServerMock server;
  private PlayerMock sender;
  private final SeenCommand command = new SeenCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    PlayerSeenTestUtils.resetDateFormatter();
    sender = server.addPlayer("Asker");
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    SpectatorModule.HIDDEN_SINCE_KEY = new NamespacedKey("icecream", "spectator.hidden_since");
  }

  @AfterEach
  void tearDown() {
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    SpectatorModule.HIDDEN_SINCE_KEY = null;
    PlayerSeenTestUtils.resetDateFormatter();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String configYaml) {
    IceCreamFixture.install(server, configYaml);
  }

  private void seen(CommandSender asker, OfflinePlayer target) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(asker);
    command.seenCommand(source, target);
  }

  // The sender's only message, as plain text.
  private String reply() {
    String message = plainText(sender.nextComponentMessage());
    assertNotNull(message);
    assertNull(sender.nextComponentMessage());
    return message;
  }

  // The moment the given time ago. The command reads the clock again a moment later, so the tests
  // allow up to one second between the two clock reads, see PlayerSeenUtilsTest.
  private static long ago(Duration duration) {
    return System.currentTimeMillis() - duration.toMillis();
  }

  // A player the server has no online copy of.
  private static OfflinePlayer offline(String name, long lastSeen) {
    OfflinePlayer player = mock(OfflinePlayer.class);
    when(player.getName()).thenReturn(name);
    when(player.getLastSeen()).thenReturn(lastSeen);
    return player;
  }

  // A player who is online, with the data the spectator module keeps on them.
  private OfflinePlayer online(String name, boolean spectating, long hiddenSince, long lastLogin) {
    PlayerMock player = server.addPlayer(name);
    if (spectating) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    }
    if (hiddenSince > 0) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.HIDDEN_SINCE_KEY, PersistentDataType.LONG, hiddenSince);
    }
    OfflinePlayer target = mock(OfflinePlayer.class);
    when(target.getName()).thenReturn(name);
    when(target.getPlayer()).thenReturn(player);
    when(target.isOnline()).thenReturn(true);
    when(target.getLastSeen()).thenReturn(System.currentTimeMillis());
    when(target.getLastLogin()).thenReturn(lastLogin);
    return target;
  }

  @Test
  void reportsAPlayerWhoCouldNotBeFound() {
    config("");

    seen(sender, offline(null, 0));

    assertEquals("Player could not be found.", reply());
  }

  @Test
  void reportsAPlayerWhoIsOnline() {
    config("");

    seen(sender, online("Steve", false, 0, 0));

    assertEquals("Player Steve is currently online", reply());
  }

  @Test
  void reportsAPlayerWhoNeverPlayed() {
    config("");

    seen(sender, offline("Steve", 0));

    assertEquals("Player Steve has never played before", reply());
  }

  @Test
  void reportsHowLongAgoAnOfflinePlayerWasSeen() {
    config("");

    seen(sender, offline("Steve", ago(Duration.ofHours(2).plusMinutes(30))));

    assertEquals("Player Steve was last seen 2 hours and 30 minutes ago", reply());
  }

  @Test
  void colourCodesTheTimeByHowLongAgo() {
    config("");

    seen(sender, offline("Steve", ago(Duration.ofDays(30))));

    Component message = sender.nextComponentMessage();
    assertNotNull(message);
    assertEquals("Player Steve was last seen 4 weeks and 2 days ago", plainText(message));
    assertTrue(hasColour(message, NamedTextColor.RED));
  }

  private static boolean hasColour(Component message, TextColor colour) {
    return colour.equals(message.color())
        || message.children().stream().anyMatch(child -> hasColour(child, colour));
  }

  @Test
  void showsTheExactDateOnHover() {
    config("");
    long lastSeen =
        LocalDateTime.of(2024, 3, 5, 14, 7, 9)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

    seen(sender, offline("Steve", lastSeen));

    Component message = sender.nextComponentMessage();
    assertNotNull(message);
    HoverEvent<?> hover = message.hoverEvent();
    assertNotNull(hover);
    assertEquals(HoverEvent.Action.SHOW_TEXT, hover.action());
    assertEquals(
        "2024-03-05 14:07:09", plainText(assertInstanceOf(Component.class, hover.value())));
  }

  @Test
  void showsTheDateInTheConfiguredFormat() {
    config("modules:\n  playerseen:\n    date-format: 'dd/MM/yyyy'\n");
    long lastSeen =
        LocalDateTime.of(2024, 3, 5, 14, 7, 9)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

    seen(sender, offline("Steve", lastSeen));

    Component message = sender.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "05/03/2024", plainText(assertInstanceOf(Component.class, message.hoverEvent().value())));
  }

  @Test
  void otherMessagesHaveNoHover() {
    config("");

    seen(sender, online("Steve", false, 0, 0));

    Component message = sender.nextComponentMessage();
    assertNotNull(message);
    assertNull(message.hoverEvent());
  }

  @Test
  void usesTheConfiguredNotFoundMessage() {
    config("modules:\n  playerseen:\n    messages:\n      not-found: 'Nobody by that name'\n");

    seen(sender, offline(null, 0));

    assertEquals("Nobody by that name", reply());
  }

  @Test
  void usesTheConfiguredOnlineMessage() {
    config("modules:\n  playerseen:\n    messages:\n      online: '<player> is here'\n");

    seen(sender, online("Steve", false, 0, 0));

    assertEquals("Steve is here", reply());
  }

  @Test
  void usesTheConfiguredNeverPlayedMessage() {
    config("modules:\n  playerseen:\n    messages:\n      never-played: '<player> is new'\n");

    seen(sender, offline("Steve", 0));

    assertEquals("Steve is new", reply());
  }

  @Test
  void usesTheConfiguredLastSeenMessage() {
    config("modules:\n  playerseen:\n    messages:\n      last-seen: '<player> left <time>'\n");

    seen(sender, offline("Steve", ago(Duration.ofMinutes(5))));

    assertEquals("Steve left 5 minutes ago", reply());
  }

  @Test
  void answersTheConsoleToo() {
    config("");
    ConsoleCommandSenderMock console = server.getConsoleSender();

    seen(console, offline("Steve", 0));

    assertEquals("Player Steve has never played before", plainText(console.nextComponentMessage()));
    assertNull(console.nextComponentMessage());
    assertNull(sender.nextComponentMessage());
  }

  @Test
  void reportsAPlayerHiddenBySpectatorModeAsOffline() {
    config("");

    seen(sender, online("Steve", true, ago(Duration.ofHours(3).plusMinutes(15)), 0));

    assertEquals("Player Steve was last seen 3 hours and 15 minutes ago", reply());
  }

  @Test
  void reportsAHiddenPlayerWithoutAHiddenTimeSinceTheirLastLogin() {
    config("");

    seen(sender, online("Steve", true, 0, ago(Duration.ofDays(1).plusHours(2))));

    assertEquals("Player Steve was last seen 1 day and 2 hours ago", reply());
  }

  @Test
  void showsTheHiddenTimeOnHover() {
    config("");
    long hiddenSince =
        LocalDateTime.of(2024, 3, 5, 14, 7, 9)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

    seen(sender, online("Steve", true, hiddenSince, 0));

    Component message = sender.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "2024-03-05 14:07:09",
        plainText(assertInstanceOf(Component.class, message.hoverEvent().value())));
  }

  @Test
  void anOnlinePlayerNotInSpectatorModeIsOnlineEvenWithAHiddenTime() {
    config("");

    seen(sender, online("Steve", false, ago(Duration.ofHours(3)), 0));

    assertEquals("Player Steve is currently online", reply());
  }
}
