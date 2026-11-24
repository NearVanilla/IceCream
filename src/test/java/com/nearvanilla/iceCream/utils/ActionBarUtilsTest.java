package com.nearvanilla.iceCream.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ActionBarUtilsTest {
  private static final Component MESSAGE = Component.text("Vine growth stopped");

  private ServerMock server;
  private long now;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    now = 1_000;
    ActionBarUtils.useClock(() -> now);
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    MockBukkit.unmock();
  }

  @Test
  void sendShowsMessage() {
    PlayerMock player = server.addPlayer();

    ActionBarUtils.send(player, MESSAGE);

    assertEquals(MESSAGE, player.nextActionBar());
  }

  @Test
  void nothingSentMeansNotInUse() {
    assertFalse(ActionBarUtils.sentWithin(server.addPlayer(), 3000));
  }

  @Test
  void inUseUntilTimeHasPassed() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, MESSAGE);

    now += 2999;
    assertTrue(ActionBarUtils.sentWithin(player, 3000));

    now += 1;
    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void sendingAgainRestartsTheWindow() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, MESSAGE);
    now += 2000;
    ActionBarUtils.send(player, MESSAGE);

    now += 2000;

    assertTrue(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void expiredMessageStaysExpired() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, MESSAGE);
    now += 5000;
    ActionBarUtils.sentWithin(player, 3000);

    // Asking with a longer window afterwards does not bring the old message back.
    assertFalse(ActionBarUtils.sentWithin(player, 10_000));
  }

  @Test
  void clearEmptiesAndGivesBack() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, MESSAGE);
    player.nextActionBar();

    ActionBarUtils.clear(player);

    assertEquals(Component.empty(), player.nextActionBar());
    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void releaseGivesBackWithoutSending() {
    PlayerMock player = server.addPlayer();
    ActionBarUtils.send(player, MESSAGE);
    player.nextActionBar();

    ActionBarUtils.release(player);

    assertEquals(null, player.nextActionBar());
    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void tracksEachPlayerSeparately() {
    PlayerMock sent = server.addPlayer();
    PlayerMock other = server.addPlayer();

    ActionBarUtils.send(sent, MESSAGE);

    assertTrue(ActionBarUtils.sentWithin(sent, 3000));
    assertFalse(ActionBarUtils.sentWithin(other, 3000));
  }
}
