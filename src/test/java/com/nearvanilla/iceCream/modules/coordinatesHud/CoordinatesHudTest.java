package com.nearvanilla.iceCream.modules.coordinatesHud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CoordinatesHudTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static CoordinatesHud hud(boolean enabledByDefault) {
    return new CoordinatesHud(
        new HudPreference("icecream"), new HudLine(HudLine.DEFAULT_TEMPLATE), enabledByDefault);
  }

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @Test
  void hiddenForNewPlayersByDefault() {
    CoordinatesHud hud = hud(false);
    PlayerMock player = server.addPlayer();

    hud.load(player);
    hud.tick();

    assertFalse(hud.isShown(player));
    assertNull(player.nextActionBar());
  }

  @Test
  void shownForNewPlayersWhenEnabledByDefault() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = server.addPlayer();

    hud.load(player);

    assertTrue(hud.isShown(player));
  }

  @Test
  void showsCoordinatesDirectionAndTime() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = server.addPlayer();
    player.getWorld().setTime(6000);
    player.teleport(new Location(player.getWorld(), 10.7, 64, -3.2, 180, 0));
    hud.load(player);

    hud.tick();

    assertEquals("XYZ: 10 64 -4   N      12:00", plain(player.nextActionBar()));
  }

  @Test
  void toggleTurnsHudOnAndStoresChoice() {
    CoordinatesHud hud = hud(false);
    PlayerMock player = server.addPlayer();
    hud.load(player);

    assertTrue(hud.toggle(player));

    assertTrue(hud.isShown(player));
    hud.tick();
    assertTrue(plain(player.nextActionBar()).startsWith("XYZ: "));
  }

  @Test
  void toggleOffClearsActionBar() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = server.addPlayer();
    hud.load(player);

    assertFalse(hud.toggle(player));

    assertFalse(hud.isShown(player));
    assertEquals(Component.empty(), player.nextActionBar());
    hud.tick();
    assertNull(player.nextActionBar());
  }

  @Test
  void choiceSurvivesLeavingAndRejoining() {
    CoordinatesHud hud = hud(false);
    PlayerMock player = server.addPlayer();
    hud.load(player);
    hud.toggle(player);

    hud.forget(player);
    assertFalse(hud.isShown(player));
    hud.load(player);

    assertTrue(hud.isShown(player));
  }

  @Test
  void choiceSurvivesNewHudInstance() {
    PlayerMock player = server.addPlayer();
    CoordinatesHud before = hud(true);
    before.load(player);
    before.toggle(player);

    CoordinatesHud after = hud(true);
    after.load(player);

    assertFalse(after.isShown(player));
  }

  @Test
  void tickDropsPlayersWhoLeft() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = server.addPlayer();
    hud.load(player);

    player.disconnect();
    hud.tick();

    assertFalse(hud.isShown(player));
  }

  @Test
  void clearStopsShowingEveryoneButKeepsChoices() {
    CoordinatesHud hud = hud(false);
    PlayerMock player = server.addPlayer();
    hud.load(player);
    hud.toggle(player);

    hud.clear();
    hud.tick();

    assertNull(player.nextActionBar());
    hud.load(player);
    assertTrue(hud.isShown(player));
  }
}
