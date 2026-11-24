package com.nearvanilla.iceCream.modules.coordinatesHud.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.coordinatesHud.CoordinatesHud;
import com.nearvanilla.iceCream.modules.coordinatesHud.HudLine;
import com.nearvanilla.iceCream.modules.coordinatesHud.HudPreference;
import com.nearvanilla.iceCream.utils.ActionBarUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CoordinatesHudListenerTest {
  private ServerMock server;
  private CoordinatesHud hud;
  private CoordinatesHudListener listener;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    hud =
        new CoordinatesHud(
            new HudPreference("icecream"), new HudLine(HudLine.DEFAULT_TEMPLATE), true, 3000);
    listener = new CoordinatesHudListener(hud);
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    MockBukkit.unmock();
  }

  @Test
  void joiningShowsHud() {
    PlayerMock player = server.addPlayer();

    listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

    assertTrue(hud.isShown(player));
  }

  @Test
  void leavingStopsTracking() {
    PlayerMock player = server.addPlayer();
    listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

    listener.onQuit(
        new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertFalse(hud.isShown(player));
  }

  @Test
  void leavingForgetsLastActionBarMessage() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, Component.text("Vine growth stopped"));

    listener.onQuit(
        new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }
}
