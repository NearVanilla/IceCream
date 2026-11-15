package com.nearvanilla.iceCream.modules.spectator.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.spectator.AutoSpectator;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AutoSpectatorListenerTest {
  private static final long THIRTY_ONE_MINUTES = 31 * 60 * 1000L;

  private ServerMock server;
  private final AutoSpectatorListener listener = new AutoSpectatorListener();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    SpectatorModule.AUTO_SPECTATOR_KEY = new NamespacedKey("icecream", "spectator.auto_enabled");
    SpectatorModule.LAST_QUIT_KEY = new NamespacedKey("icecream", "spectator.last_quit");
    SpectatorModule.HIDDEN_SINCE_KEY = new NamespacedKey("icecream", "spectator.hidden_since");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = new NamespacedKey("icecream", "staff_mode.enabled");
    AutoSpectator.configure(true, 30);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    SpectatorModule.AUTO_SPECTATOR_KEY = null;
    SpectatorModule.LAST_QUIT_KEY = null;
    SpectatorModule.HIDDEN_SINCE_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
  }

  /** A staff member with the permission who last quit 31 minutes ago. */
  private PlayerMock staffAwayAWhile(boolean optedIn) {
    PlayerMock player = server.addPlayer("Staff");
    player.addAttachment(MockBukkit.createMockPlugin(), AutoSpectator.PERMISSION, true);
    if (optedIn) AutoSpectator.toggleAuto(player);
    player
        .getPersistentDataContainer()
        .set(
            SpectatorModule.LAST_QUIT_KEY,
            PersistentDataType.LONG,
            System.currentTimeMillis() - THIRTY_ONE_MINUTES);
    return player;
  }

  private void join(PlayerMock player) {
    listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));
  }

  @Test
  void joinMarksOptedInStaffAsSpectatingAndSendsNotice() {
    PlayerMock player = staffAwayAWhile(true);

    join(player);

    assertTrue(SpectatorUtils.isInSpectatorMode(player));
    assertEquals(listener.autoSpectatorNotice, player.nextComponentMessage());
  }

  @Test
  void joinBackdatesHiddenSinceToLastQuit() {
    // /seen reports a spectator as last seen when they were hidden. Others last saw an
    // auto-spectated player when they quit, not when they silently rejoined.
    PlayerMock player = staffAwayAWhile(true);
    long lastQuit =
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG);

    join(player);

    assertEquals(lastQuit, SpectatorUtils.getHiddenSince(player));
  }

  @Test
  void joinLeavesNonOptedInPlayerAlone() {
    PlayerMock player = staffAwayAWhile(false);

    join(player);

    assertFalse(SpectatorUtils.isInSpectatorMode(player));
    assertNull(player.nextComponentMessage());
  }

  @Test
  void joinLeavesAlreadySpectatingPlayerAlone() {
    // Someone who logged off while spectating rejoins spectating anyway, so they must not be told
    // it happened automatically.
    PlayerMock player = staffAwayAWhile(true);
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    join(player);

    assertNull(player.nextComponentMessage());
  }

  @Test
  void quitRecordsTimeForStaff() {
    PlayerMock player = server.addPlayer("Staff");
    player.addAttachment(MockBukkit.createMockPlugin(), AutoSpectator.PERMISSION, true);

    long before = System.currentTimeMillis();
    listener.onPlayerQuit(
        new PlayerQuitEvent(
            player, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED));
    long after = System.currentTimeMillis();

    Long recorded =
        player
            .getPersistentDataContainer()
            .get(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG);
    assertNotNull(recorded);
    assertTrue(recorded >= before && recorded <= after);
  }

  @Test
  void joinHandlerRunsBeforeSpectatorJoinHandler() throws NoSuchMethodException {
    // The spectator join handler suppresses the join message and hides the player only if the
    // spectator flag is already set when it runs, so the auto handler must run first.
    EventHandler auto =
        AutoSpectatorListener.class
            .getMethod("onPlayerJoin", PlayerJoinEvent.class)
            .getAnnotation(EventHandler.class);
    EventHandler spectator =
        SpectatorPlayerJoinEvent.class
            .getMethod("onPlayerJoin", PlayerJoinEvent.class)
            .getAnnotation(EventHandler.class);

    assertTrue(auto.priority().getSlot() < spectator.priority().getSlot());
  }
}
