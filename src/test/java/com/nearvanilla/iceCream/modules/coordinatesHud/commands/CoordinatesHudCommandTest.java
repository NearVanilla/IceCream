package com.nearvanilla.iceCream.modules.coordinatesHud.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.coordinatesHud.CoordinatesHud;
import com.nearvanilla.iceCream.modules.coordinatesHud.HudLine;
import com.nearvanilla.iceCream.modules.coordinatesHud.HudPreference;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CoordinatesHudCommandTest {
  private ServerMock server;
  private CoordinatesHud hud;
  private CoordinatesHudCommand command;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    hud =
        new CoordinatesHud(
            new HudPreference("icecream"), new HudLine(HudLine.DEFAULT_TEMPLATE), false, 3000);
    command =
        new CoordinatesHudCommand(
            hud, new CoordinatesHudCommand.Messages("on", "off", "toggled for <count>"));
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static CommandSourceStack source(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    return source;
  }

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @Test
  void toggleTurnsHudOnThenOff() {
    PlayerMock player = server.addPlayer();
    hud.load(player);

    command.toggle(source(player));
    assertTrue(hud.isShown(player));
    assertEquals("on", plain(player.nextComponentMessage()));

    command.toggle(source(player));
    assertFalse(hud.isShown(player));
    assertEquals("off", plain(player.nextComponentMessage()));
  }

  @Test
  void toggleNeedsPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.toggle(source(console));

    assertEquals(
        "This command can only be used by players.", plain(console.nextComponentMessage()));
  }

  @Test
  void toggleOthersFlipsEachPlayerAndReportsCount() {
    PlayerMock shown = server.addPlayer("Shown");
    PlayerMock hidden = server.addPlayer("Hidden");
    hud.load(shown);
    hud.toggle(shown);
    hud.load(hidden);
    shown.nextActionBar();
    MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
    when(selector.values()).thenReturn(List.of(shown, hidden));
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.toggleOthers(source(console), selector);

    assertFalse(hud.isShown(shown));
    assertTrue(hud.isShown(hidden));
    assertEquals(Component.empty(), shown.nextActionBar());
    assertEquals("toggled for 2", plain(console.nextComponentMessage()));
  }

  @Test
  void toggleOthersSendsNothingToTargets() {
    PlayerMock target = server.addPlayer();
    hud.load(target);
    MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
    when(selector.values()).thenReturn(List.of(target));

    command.toggleOthers(source(server.getConsoleSender()), selector);

    assertEquals(null, target.nextComponentMessage());
  }
}
