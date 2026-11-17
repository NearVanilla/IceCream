package com.nearvanilla.iceCream.modules.phantomToggle.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleModule;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PhantomToggleCommandTest {
  private static final String NOW_DISABLED =
      "Phantom spawning is now disabled for you. No more phantom visits.";
  private static final String NOW_ENABLED =
      "Phantom spawning is now enabled for you. Don't forget to sleep!";
  private static final String STATUS_DISABLED =
      "Phantom spawning is currently disabled for you. Enjoy the peace and quiet!";
  private static final String STATUS_ENABLED =
      "Phantom spawning is currently enabled for you. Remember to sleep!";
  private static final String PLAYERS_ONLY = "This command can only be used by players.";

  private ServerMock server;
  private PlayerMock player;
  private final PhantomToggleCommand command = new PhantomToggleCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    PhantomToggleModule.PHANTOM_DISABLED_KEY =
        new NamespacedKey(IceCream.instance, "phantomtoggle.disabled");
    player = server.addPlayer("Sleeper");
  }

  @AfterEach
  void tearDown() {
    PhantomToggleModule.PHANTOM_DISABLED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private CommandSourceStack source(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    return source;
  }

  private void toggle(CommandSender sender) {
    command.togglePhantoms(source(sender));
  }

  private void status(CommandSender sender) {
    command.phantomStatus(source(sender));
  }

  private String consoleMessage(ConsoleCommandSenderMock console) {
    Component message = console.nextComponentMessage();
    assertNotNull(message);
    return PlainTextComponentSerializer.plainText().serialize(message);
  }

  @Test
  void firstToggleDisablesPhantoms() {
    toggle(player);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void firstToggleTellsThePlayerPhantomsAreDisabled() {
    toggle(player);

    assertEquals(NOW_DISABLED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void secondToggleEnablesPhantoms() {
    toggle(player);
    nextMessage(player);

    toggle(player);

    assertFalse(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void secondToggleTellsThePlayerPhantomsAreEnabled() {
    toggle(player);
    nextMessage(player);

    toggle(player);

    assertEquals(NOW_ENABLED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void thirdToggleDisablesPhantomsAgain() {
    toggle(player);
    toggle(player);

    toggle(player);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void toggleOnlyChangesTheSendersPreference() {
    PlayerMock other = server.addPlayer("Other");

    toggle(player);

    assertFalse(PhantomToggleUtils.isPhantomDisabled(other));
    assertNull(nextMessage(other));
  }

  @Test
  void toggleRejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    toggle(console);

    assertEquals(PLAYERS_ONLY, consoleMessage(console));
    assertNull(console.nextComponentMessage());
    assertFalse(PhantomToggleUtils.isPhantomDisabled(player));
    assertNull(nextMessage(player));
  }

  @Test
  void statusReportsEnabledForAPlayerWithNoPreference() {
    status(player);

    assertEquals(STATUS_ENABLED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void statusReportsDisabledForAPlayerWhoDisabledPhantoms() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    status(player);

    assertEquals(STATUS_DISABLED, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void statusReportsEnabledForAPlayerWhoEnabledPhantomsAgain() {
    PhantomToggleUtils.setPhantomDisabled(player, true);
    PhantomToggleUtils.setPhantomDisabled(player, false);

    status(player);

    assertEquals(STATUS_ENABLED, nextMessage(player));
  }

  @Test
  void statusDoesNotChangeThePreference() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    status(player);
    status(player);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void statusRejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    status(console);

    assertEquals(PLAYERS_ONLY, consoleMessage(console));
    assertNull(console.nextComponentMessage());
    assertNull(nextMessage(player));
  }
}
