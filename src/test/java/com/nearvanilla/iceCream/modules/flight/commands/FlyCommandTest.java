package com.nearvanilla.iceCream.modules.flight.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.flight.FlightModule;
import com.nearvanilla.iceCream.modules.flight.FlightUtils;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FlyCommandTest {
  private ServerMock server;
  private PlayerMock player;
  private final FlyCommand command = new FlyCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    FlightModule.FLIGHT_ENABLED_KEY = new NamespacedKey(IceCream.instance, "flight.enabled");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY =
        new NamespacedKey(IceCream.instance, "staff_mode.enabled");
    player = server.addPlayer("Flyer");
    player.addAttachment(IceCream.instance, FlightUtils.PERMISSION, true);
  }

  @AfterEach
  void tearDown() {
    FlightModule.FLIGHT_ENABLED_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void fly(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.fly(source);
  }

  private String nextMessage(PlayerMock target) {
    Component message = target.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  private boolean hasSavedPreference() {
    return player.getPersistentDataContainer().has(FlightModule.FLIGHT_ENABLED_KEY);
  }

  @Test
  void firstUseEnablesFlight() {
    fly(player);

    assertTrue(FlightUtils.isEnabled(player));
    assertTrue(player.getAllowFlight());
  }

  @Test
  void firstUseTellsThePlayerFlightIsEnabled() {
    fly(player);

    assertEquals("Flight enabled. Double-tap jump to fly.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void secondUseDisablesFlight() {
    fly(player);
    nextMessage(player);

    fly(player);

    assertFalse(FlightUtils.isEnabled(player));
    assertFalse(player.getAllowFlight());
    assertFalse(hasSavedPreference());
  }

  @Test
  void secondUseTellsThePlayerFlightIsDisabled() {
    fly(player);
    nextMessage(player);

    fly(player);

    assertEquals("Flight disabled.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void secondUseStopsAFlyingPlayer() {
    fly(player);
    player.setFlying(true);

    fly(player);

    assertFalse(player.isFlying());
  }

  @Test
  void thirdUseEnablesFlightAgain() {
    fly(player);
    fly(player);

    fly(player);

    assertTrue(FlightUtils.isEnabled(player));
    assertTrue(player.getAllowFlight());
  }

  @Test
  void refusesACreativePlayer() {
    player.setGameMode(GameMode.CREATIVE);

    fly(player);

    assertEquals(
        "Your current game mode or staff mode already provides flight.", nextMessage(player));
    assertNull(nextMessage(player));
    assertFalse(hasSavedPreference());
  }

  @Test
  void refusesASpectator() {
    player.setGameMode(GameMode.SPECTATOR);

    fly(player);

    assertEquals(
        "Your current game mode or staff mode already provides flight.", nextMessage(player));
    assertFalse(hasSavedPreference());
  }

  @Test
  void refusesAPlayerInStaffMode() {
    player
        .getPersistentDataContainer()
        .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    fly(player);

    assertEquals(
        "Your current game mode or staff mode already provides flight.", nextMessage(player));
    assertFalse(hasSavedPreference());
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    fly(console);

    Component message = console.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "This command can only be used by players.",
        PlainTextComponentSerializer.plainText().serialize(message));
    assertNull(console.nextComponentMessage());
    assertNull(nextMessage(player));
  }
}
