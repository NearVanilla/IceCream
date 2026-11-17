package com.nearvanilla.iceCream.modules.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.IntegrationTestUtils;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.jpenilla.squaremap.api.PlayerManager;
import xyz.jpenilla.squaremap.api.Squaremap;

/**
 * The squaremap integration with the plugin present and absent. The squaremap API is a Mockito mock
 * handed out by squaremap's own provider.
 */
class SquaremapIntegrationTest {
  private ServerMock server;
  private Squaremap api;
  private PlayerManager players;
  private LogCapture log;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    log = LogCapture.of(IceCream.logger);
    api = mock(Squaremap.class);
    players = mock(PlayerManager.class);
    when(api.playerManager()).thenReturn(players);
  }

  @AfterEach
  void tearDown() {
    log.close();
    IntegrationTestUtils.resetSquaremapIntegration();
    IntegrationTestUtils.unregisterSquaremap();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  /** squaremap is installed and has loaded its API. */
  private void squaremapPresent() {
    IntegrationTestUtils.installPlugin("squaremap");
    IntegrationTestUtils.registerSquaremap(api);
  }

  // Looking up the API

  @Test
  void lookupIsNullWhenSquaremapIsNotInstalled() {
    assertNull(SquaremapIntegration.lookup());

    assertEquals(List.of("squaremap not found, skipping integration."), log.messages(Level.INFO));
  }

  @Test
  void lookupIsNullWhenTheApiIsLoadedButTheSquaremapPluginIsNotInstalled() {
    // The API is loaded, for instance left behind by an earlier plugin, but the plugin is gone.
    IntegrationTestUtils.registerSquaremap(api);

    assertNull(SquaremapIntegration.lookup());
  }

  @Test
  void lookupReturnsTheApiWhenSquaremapIsInstalled() {
    squaremapPresent();

    assertSame(api, SquaremapIntegration.lookup());
    assertTrue(log.records().isEmpty());
  }

  @Test
  void lookupIsNullAndWarnsWhenSquaremapHasNotLoadedItsApi() {
    IntegrationTestUtils.installPlugin("squaremap");

    assertNull(SquaremapIntegration.lookup());

    assertEquals(
        List.of("squaremap found but its API is not loaded."), log.messages(Level.WARNING));
  }

  // Initializing

  @Test
  void initEnablesTheIntegrationWhenSquaremapIsInstalled() {
    squaremapPresent();
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.init();

    assertEquals(List.of("squaremap integration enabled."), log.messages(Level.INFO));
    SquaremapIntegration.hidePlayer(player);
    verify(players).hide(player.getUniqueId(), true);
  }

  @Test
  void initLeavesTheIntegrationDisabledWhenSquaremapIsNotInstalled() {
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.init();

    assertEquals(List.of("squaremap not found, skipping integration."), log.messages(Level.INFO));
    SquaremapIntegration.hidePlayer(player);
    SquaremapIntegration.showPlayer(player);
    assertTrue(SquaremapIntegration.isPlayerVisible(player));
    verifyNoInteractions(players);
  }

  @Test
  void initLeavesTheIntegrationDisabledWhenTheApiIsNotLoaded() {
    IntegrationTestUtils.installPlugin("squaremap");
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.init();

    assertEquals(
        List.of("squaremap found but its API is not loaded."), log.messages(Level.WARNING));
    assertTrue(log.messages(Level.INFO).isEmpty());
    SquaremapIntegration.hidePlayer(player);
    verifyNoInteractions(players);
  }

  @Test
  void initAgainAfterSquaremapWentAwayDisablesTheIntegration() {
    squaremapPresent();
    PlayerMock player = server.addPlayer();
    SquaremapIntegration.init();
    IntegrationTestUtils.unregisterSquaremap();

    SquaremapIntegration.init();

    SquaremapIntegration.hidePlayer(player);
    verifyNoInteractions(players);
  }

  // Hiding and showing players

  @Test
  void nothingHappensBeforeInitEvenWithSquaremapInstalled() {
    squaremapPresent();
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.hidePlayer(player);
    SquaremapIntegration.showPlayer(player);

    assertTrue(SquaremapIntegration.isPlayerVisible(player));
    verifyNoInteractions(players);
  }

  @Test
  void hidePlayerHidesThePlayerWithTheirId() {
    squaremapPresent();
    SquaremapIntegration.init();
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.hidePlayer(player);

    verify(players).hide(player.getUniqueId(), true);
  }

  @Test
  void showPlayerShowsThePlayerWithTheirId() {
    squaremapPresent();
    SquaremapIntegration.init();
    PlayerMock player = server.addPlayer();

    SquaremapIntegration.showPlayer(player);

    verify(players).show(player.getUniqueId(), true);
  }

  @Test
  void aPlayerSquaremapHidesIsNotVisible() {
    squaremapPresent();
    SquaremapIntegration.init();
    PlayerMock player = server.addPlayer();
    when(players.hidden(player.getUniqueId())).thenReturn(true);

    assertFalse(SquaremapIntegration.isPlayerVisible(player));
  }

  @Test
  void aPlayerSquaremapDoesNotHideIsVisible() {
    squaremapPresent();
    SquaremapIntegration.init();
    PlayerMock player = server.addPlayer();
    when(players.hidden(player.getUniqueId())).thenReturn(false);

    assertTrue(SquaremapIntegration.isPlayerVisible(player));
  }

  @Test
  void visibilityIsAskedAboutTheGivenPlayerOnly() {
    squaremapPresent();
    SquaremapIntegration.init();
    PlayerMock hidden = server.addPlayer();
    PlayerMock shown = server.addPlayer();
    when(players.hidden(hidden.getUniqueId())).thenReturn(true);

    assertFalse(SquaremapIntegration.isPlayerVisible(hidden));
    assertTrue(SquaremapIntegration.isPlayerVisible(shown));
  }
}
