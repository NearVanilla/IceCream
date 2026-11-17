package com.nearvanilla.iceCream.modules.locatorBar.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.locatorBar.LocatorBarModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LocatorBarCommandTest {
  private ServerMock server;
  private PlayerMock player;
  private final LocatorBarCommand command = new LocatorBarCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY =
        new NamespacedKey(IceCream.instance, "locatorbar.previous_range");
    player = playerWithReceiveRange("Navigator");
  }

  @AfterEach
  void tearDown() {
    LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // MockBukkit has no default for the waypoint receive range attribute, so players start without
  // it. This player carries a real attribute instance with the vanilla default of 60 million.
  private PlayerMock playerWithReceiveRange(String name) {
    AttributeInstance range =
        new AttributeInstanceMock(Attribute.WAYPOINT_RECEIVE_RANGE, 60_000_000.0);
    PlayerMock created =
        new PlayerMock(server, name, UUID.nameUUIDFromBytes(name.getBytes())) {
          @Override
          public AttributeInstance getAttribute(Attribute attribute) {
            return attribute == Attribute.WAYPOINT_RECEIVE_RANGE
                ? range
                : super.getAttribute(attribute);
          }
        };
    server.addPlayer(created);
    return created;
  }

  private void toggle(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.toggleLocatorBar(source);
  }

  private String nextMessage(PlayerMock target) {
    Component message = target.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  private AttributeInstance receiveRange(PlayerMock target) {
    AttributeInstance range = target.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE);
    assertNotNull(range);
    return range;
  }

  private Double storedRange(PlayerMock target) {
    return target
        .getPersistentDataContainer()
        .get(LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY, PersistentDataType.DOUBLE);
  }

  @Test
  void disablingLowersTheReceiveRangeToTheMinimumByDefault() {
    receiveRange(player).setBaseValue(1234.0);

    toggle(player);

    assertEquals(0.1, receiveRange(player).getBaseValue());
  }

  @Test
  void disablingRemembersThePreviousReceiveRange() {
    receiveRange(player).setBaseValue(1234.0);

    toggle(player);

    assertEquals(1234.0, storedRange(player));
  }

  @Test
  void disablingTellsThePlayerTheLocatorBarIsDisabled() {
    toggle(player);

    assertEquals("The locator bar is now disabled.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void disablingUsesTheConfiguredDisabledRange() {
    IceCreamFixture.install(server, "modules:\n  locatorbar:\n    disabled-receive-range: 24.0\n");
    receiveRange(player).setBaseValue(1234.0);

    toggle(player);

    assertEquals(24.0, receiveRange(player).getBaseValue());
  }

  @Test
  void enablingRestoresThePreviousReceiveRange() {
    receiveRange(player).setBaseValue(1234.0);
    toggle(player);

    toggle(player);

    assertEquals(1234.0, receiveRange(player).getBaseValue());
  }

  @Test
  void enablingForgetsThePreviousReceiveRange() {
    receiveRange(player).setBaseValue(1234.0);
    toggle(player);

    toggle(player);

    assertNull(storedRange(player));
  }

  @Test
  void enablingTellsThePlayerTheLocatorBarIsEnabled() {
    toggle(player);
    nextMessage(player);

    toggle(player);

    assertEquals("The locator bar is now enabled.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void togglingAgainDisablesTheLocatorBarAgain() {
    receiveRange(player).setBaseValue(500.0);
    toggle(player);
    toggle(player);

    toggle(player);

    assertEquals(0.1, receiveRange(player).getBaseValue());
    assertEquals(500.0, storedRange(player));
  }

  @Test
  void enablingRestoresAStoredRangeEvenWhenTheConfiguredRangeChanged() {
    receiveRange(player).setBaseValue(77.0);
    toggle(player);
    IceCreamFixture.install(server, "modules:\n  locatorbar:\n    disabled-receive-range: 99.0\n");

    toggle(player);

    assertEquals(77.0, receiveRange(player).getBaseValue());
  }

  @Test
  void eachPlayerKeepsTheirOwnPreviousRange() {
    PlayerMock other = playerWithReceiveRange("Other");
    receiveRange(player).setBaseValue(10.0);
    receiveRange(other).setBaseValue(20.0);

    toggle(player);
    toggle(other);
    toggle(player);

    assertEquals(10.0, receiveRange(player).getBaseValue());
    assertEquals(0.1, receiveRange(other).getBaseValue());
    assertEquals(20.0, storedRange(other));
  }

  @Test
  void tellsThePlayerWhenTheReceiveRangeAttributeIsUnavailable() {
    PlayerMock noAttribute =
        new PlayerMock(server, "NoAttribute", UUID.randomUUID()) {
          @Override
          public AttributeInstance getAttribute(Attribute attribute) {
            return attribute == Attribute.WAYPOINT_RECEIVE_RANGE
                ? null
                : super.getAttribute(attribute);
          }
        };
    server.addPlayer(noAttribute);

    toggle(noAttribute);

    assertEquals("Your locator bar setting could not be changed.", nextMessage(noAttribute));
    assertNull(nextMessage(noAttribute));
    assertNull(storedRange(noAttribute));
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    toggle(console);

    Component message = console.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "This command can only be used by players.",
        PlainTextComponentSerializer.plainText().serialize(message));
    assertNull(console.nextComponentMessage());
    assertNull(nextMessage(player));
  }
}
