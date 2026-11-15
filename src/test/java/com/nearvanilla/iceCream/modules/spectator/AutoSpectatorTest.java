package com.nearvanilla.iceCream.modules.spectator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import org.bukkit.NamespacedKey;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AutoSpectatorTest {
  private static final long NOW = 10_000_000_000L;
  private static final long THIRTY_MINUTES = 30 * 60 * 1000L;

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    SpectatorModule.AUTO_SPECTATOR_KEY = new NamespacedKey("icecream", "spectator.auto_enabled");
    SpectatorModule.LAST_QUIT_KEY = new NamespacedKey("icecream", "spectator.last_quit");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = new NamespacedKey("icecream", "staff_mode.enabled");
    AutoSpectator.configure(true, 30);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    SpectatorModule.AUTO_SPECTATOR_KEY = null;
    SpectatorModule.LAST_QUIT_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
  }

  private PermissionAttachment grantPermission(PlayerMock player) {
    return player.addAttachment(MockBukkit.createMockPlugin(), AutoSpectator.PERMISSION, true);
  }

  /** A staff member who has opted in and last quit the given number of millis before NOW. */
  private PlayerMock optedInStaff(long awayMillis) {
    PlayerMock player = server.addPlayer("Staff");
    grantPermission(player);
    AutoSpectator.toggleAuto(player);
    AutoSpectator.recordQuit(player, NOW - awayMillis);
    return player;
  }

  @Test
  void autoSpectatesWhenAwayExactlyTheThreshold() {
    PlayerMock player = optedInStaff(THIRTY_MINUTES);

    assertTrue(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWhenAwayShorterThanThreshold() {
    PlayerMock player = optedInStaff(THIRTY_MINUTES - 1);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWhenNotOptedIn() {
    PlayerMock player = server.addPlayer("Staff");
    grantPermission(player);
    AutoSpectator.recordQuit(player, NOW - THIRTY_MINUTES);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWithoutPermission() {
    PlayerMock player = server.addPlayer("Staff");
    PermissionAttachment attachment = grantPermission(player);
    AutoSpectator.toggleAuto(player);
    AutoSpectator.recordQuit(player, NOW - THIRTY_MINUTES);

    attachment.setPermission(AutoSpectator.PERMISSION, false);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWhenFeatureDisabled() {
    PlayerMock player = optedInStaff(THIRTY_MINUTES);
    AutoSpectator.configure(false, 30);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWithoutRecordedQuit() {
    PlayerMock player = server.addPlayer("Staff");
    grantPermission(player);
    AutoSpectator.toggleAuto(player);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateInStaffMode() {
    PlayerMock player = optedInStaff(THIRTY_MINUTES);
    player
        .getPersistentDataContainer()
        .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWhenAlreadySpectating() {
    PlayerMock player = optedInStaff(THIRTY_MINUTES);
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void doesNotAutoSpectateWhenLastQuitIsInTheFuture() {
    // A clock that moved backwards must not count as time away.
    PlayerMock player = optedInStaff(-THIRTY_MINUTES);
    AutoSpectator.configure(true, 0);

    assertFalse(AutoSpectator.shouldAutoSpectate(player, NOW));
  }

  @Test
  void toggleAutoDefaultsOffAndFlips() {
    PlayerMock player = server.addPlayer("Staff");

    assertFalse(AutoSpectator.isAutoEnabled(player));
    assertTrue(AutoSpectator.toggleAuto(player));
    assertTrue(AutoSpectator.isAutoEnabled(player));
    assertFalse(AutoSpectator.toggleAuto(player));
    assertFalse(AutoSpectator.isAutoEnabled(player));
  }

  @Test
  void recordQuitIgnoresPlayersWithoutPermission() {
    PlayerMock player = server.addPlayer("Player");

    AutoSpectator.recordQuit(player, NOW);

    assertFalse(
        player
            .getPersistentDataContainer()
            .has(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG));
  }

  @Test
  void recordQuitForOnlinePlayersRecordsStaffOnly() {
    // Paper disables plugins before players are removed at shutdown, so no quit event reaches the
    // listener and the module records the time itself.
    PlayerMock staff = server.addPlayer("Staff");
    grantPermission(staff);
    PlayerMock player = server.addPlayer("Player");

    AutoSpectator.recordQuitForOnlinePlayers(NOW);

    assertEquals(
        NOW,
        staff
            .getPersistentDataContainer()
            .get(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG));
    assertFalse(
        player
            .getPersistentDataContainer()
            .has(SpectatorModule.LAST_QUIT_KEY, PersistentDataType.LONG));
  }

  @Test
  void negativeOfflineMinutesFallsBackToDefault() {
    AutoSpectator.configure(true, -5);

    assertEquals(THIRTY_MINUTES, AutoSpectator.getOfflineMillis());
  }
}
