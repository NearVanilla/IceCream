package com.nearvanilla.iceCream.modules.coordinatesHud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.utils.ActionBarUtils;
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
  private static final long PAUSE_MILLIS = 3000;
  private static final Component OTHER = Component.text("Vine growth stopped");

  private ServerMock server;
  private long now;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    now = 0;
    ActionBarUtils.useClock(() -> now);
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    MockBukkit.unmock();
  }

  private static CoordinatesHud hud(boolean enabledByDefault) {
    return hud(enabledByDefault, PAUSE_MILLIS);
  }

  private static CoordinatesHud hud(boolean enabledByDefault, long pauseMillis) {
    return new CoordinatesHud(
        new HudPreference("icecream"),
        new HudLine(HudLine.DEFAULT_TEMPLATE),
        enabledByDefault,
        pauseMillis);
  }

  private PlayerMock shownPlayer(CoordinatesHud hud) {
    PlayerMock player = server.addPlayer();
    hud.load(player);
    return player;
  }

  /** Drains the player's action bar, telling whether the HUD was among what they got. */
  private static boolean gotHud(PlayerMock player) {
    boolean got = false;
    Component next;
    while ((next = player.nextActionBar()) != null) {
      if (plain(next).startsWith("XYZ: ")) got = true;
    }
    return got;
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

  @Test
  void pausesAfterAnotherModulesMessage() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = shownPlayer(hud);

    ActionBarUtils.send(player, OTHER);
    now += PAUSE_MILLIS - 1;
    hud.tick();

    assertEquals(OTHER, player.nextActionBar());
    assertNull(player.nextActionBar());
  }

  @Test
  void resumesWhenPauseIsOver() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = shownPlayer(hud);
    ActionBarUtils.send(player, OTHER);
    player.nextActionBar();

    now += PAUSE_MILLIS;
    hud.tick();

    assertTrue(gotHud(player));
  }

  @Test
  void staysPausedWhileCountdownRefreshesAndResumesAfter() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = shownPlayer(hud);

    // A 20 second countdown refreshed every second, with the HUD ticking every 100 ms.
    for (long elapsed = 0; elapsed <= 20_000; elapsed += 100) {
      now = elapsed;
      if (elapsed % 1000 == 0) ActionBarUtils.send(player, Component.text(elapsed / 1000));
      hud.tick();
      assertFalse(gotHud(player), "HUD showed during the countdown at " + elapsed + " ms");
    }

    now = 20_000 + PAUSE_MILLIS;
    hud.tick();
    assertTrue(gotHud(player));
  }

  @Test
  void resumesAtOnceWhenMessageIsCleared() {
    CoordinatesHud hud = hud(true);
    PlayerMock player = shownPlayer(hud);
    ActionBarUtils.send(player, OTHER);

    ActionBarUtils.clear(player);
    hud.tick();

    assertTrue(gotHud(player));
  }

  @Test
  void pauseOnlyAffectsThatPlayer() {
    CoordinatesHud hud = hud(true);
    PlayerMock reading = shownPlayer(hud);
    PlayerMock other = shownPlayer(hud);

    ActionBarUtils.send(reading, OTHER);
    hud.tick();

    assertFalse(gotHud(reading));
    assertTrue(gotHud(other));
  }

  @Test
  void zeroPauseNeverStepsAside() {
    CoordinatesHud hud = hud(true, 0);
    PlayerMock player = shownPlayer(hud);

    ActionBarUtils.send(player, OTHER);
    hud.tick();

    assertTrue(gotHud(player));
  }
}
