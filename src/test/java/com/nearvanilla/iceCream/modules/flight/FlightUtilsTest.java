package com.nearvanilla.iceCream.modules.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FlightUtilsTest {
  private ServerMock server;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    FlightModule.FLIGHT_ENABLED_KEY = new NamespacedKey(IceCream.instance, "flight.enabled");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY =
        new NamespacedKey(IceCream.instance, "staff_mode.enabled");
    player = server.addPlayer("Flyer");
  }

  @AfterEach
  void tearDown() {
    FlightModule.FLIGHT_ENABLED_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void allowFlying() {
    player.addAttachment(IceCream.instance, FlightUtils.PERMISSION, true);
  }

  private void savePreference() {
    player
        .getPersistentDataContainer()
        .set(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, true);
  }

  private boolean hasSavedPreference() {
    return player.getPersistentDataContainer().has(FlightModule.FLIGHT_ENABLED_KEY);
  }

  private void enterStaffMode() {
    player
        .getPersistentDataContainer()
        .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
  }

  @Test
  void permissionIsTheFlyNode() {
    assertEquals("icecream.modules.flight.fly", FlightUtils.PERMISSION);
  }

  @Test
  void isNotEnabledByDefault() {
    assertFalse(FlightUtils.isEnabled(player));
  }

  @Test
  void isEnabledOnceThePreferenceIsSaved() {
    savePreference();

    assertTrue(FlightUtils.isEnabled(player));
  }

  @Test
  void isNotEnabledWhenTheSavedPreferenceIsFalse() {
    player
        .getPersistentDataContainer()
        .set(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, false);

    assertFalse(FlightUtils.isEnabled(player));
  }

  @Test
  void survivalHasNoOtherFlight() {
    player.setGameMode(GameMode.SURVIVAL);

    assertFalse(FlightUtils.hasOtherFlight(player));
  }

  @Test
  void adventureHasNoOtherFlight() {
    player.setGameMode(GameMode.ADVENTURE);

    assertFalse(FlightUtils.hasOtherFlight(player));
  }

  @Test
  void creativeHasOtherFlight() {
    player.setGameMode(GameMode.CREATIVE);

    assertTrue(FlightUtils.hasOtherFlight(player));
  }

  @Test
  void spectatorHasOtherFlight() {
    player.setGameMode(GameMode.SPECTATOR);

    assertTrue(FlightUtils.hasOtherFlight(player));
  }

  @Test
  void staffModeHasOtherFlight() {
    enterStaffMode();

    assertTrue(FlightUtils.hasOtherFlight(player));
  }

  @Test
  void enablingSavesThePreference() {
    FlightUtils.setEnabled(player, true);

    assertTrue(FlightUtils.isEnabled(player));
  }

  @Test
  void enablingAllowsFlightForAPlayerWithPermission() {
    allowFlying();

    FlightUtils.setEnabled(player, true);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void enablingDoesNotAllowFlightWithoutPermission() {
    FlightUtils.setEnabled(player, true);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void enablingKeepsThePreferenceEvenWithoutPermission() {
    FlightUtils.setEnabled(player, true);

    assertTrue(hasSavedPreference());
  }

  @Test
  void enablingLeavesACreativePlayersFlightAlone() {
    allowFlying();
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(false);

    FlightUtils.setEnabled(player, true);

    assertTrue(hasSavedPreference());
    assertFalse(player.getAllowFlight());
  }

  @Test
  void disablingRemovesThePreference() {
    savePreference();

    FlightUtils.setEnabled(player, false);

    assertFalse(hasSavedPreference());
    assertFalse(FlightUtils.isEnabled(player));
  }

  @Test
  void disablingTakesFlightAwayFromAPlayerWhoWasFlying() {
    allowFlying();
    FlightUtils.setEnabled(player, true);
    player.setFlying(true);

    FlightUtils.setEnabled(player, false);

    assertFalse(player.getAllowFlight());
    assertFalse(player.isFlying());
  }

  @Test
  void disablingKeepsACreativePlayersFlight() {
    savePreference();
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(true);
    player.setFlying(true);

    FlightUtils.setEnabled(player, false);

    assertFalse(hasSavedPreference());
    assertTrue(player.getAllowFlight());
    assertTrue(player.isFlying());
  }

  @Test
  void disablingWithoutAPreferenceLeavesFlightFromOtherSourcesAlone() {
    player.setAllowFlight(true);

    FlightUtils.setEnabled(player, false);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void restoreDoesNothingWithoutAPreference() {
    allowFlying();

    FlightUtils.restoreFlight(player);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void restoreAllowsFlightForAPlayerWithPermission() {
    allowFlying();
    savePreference();

    FlightUtils.restoreFlight(player);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void restoreSuspendsFlightWhenThePermissionWasLost() {
    savePreference();
    player.setAllowFlight(true);
    player.setFlying(true);

    FlightUtils.restoreFlight(player);

    assertFalse(player.getAllowFlight());
    assertFalse(player.isFlying());
  }

  @Test
  void restoreKeepsThePreferenceWhenThePermissionWasLost() {
    savePreference();

    FlightUtils.restoreFlight(player);

    assertTrue(hasSavedPreference());
  }

  @Test
  void restoreLeavesACreativePlayerAlone() {
    allowFlying();
    savePreference();
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(false);

    FlightUtils.restoreFlight(player);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void restoreLeavesAPlayerInStaffModeAlone() {
    allowFlying();
    savePreference();
    enterStaffMode();

    FlightUtils.restoreFlight(player);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void suspendRemovesFlightAndStopsFlying() {
    savePreference();
    player.setAllowFlight(true);
    player.setFlying(true);

    FlightUtils.suspendFlight(player);

    assertFalse(player.getAllowFlight());
    assertFalse(player.isFlying());
  }

  @Test
  void suspendRemembersThePreference() {
    savePreference();
    player.setAllowFlight(true);

    FlightUtils.suspendFlight(player);

    assertTrue(hasSavedPreference());
  }

  @Test
  void suspendLeavesFlightAloneWithoutAPreference() {
    player.setAllowFlight(true);
    player.setFlying(true);

    FlightUtils.suspendFlight(player);

    assertTrue(player.getAllowFlight());
    assertTrue(player.isFlying());
  }

  @Test
  void suspendLeavesACreativePlayerAlone() {
    savePreference();
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(true);
    player.setFlying(true);

    FlightUtils.suspendFlight(player);

    assertTrue(player.getAllowFlight());
    assertTrue(player.isFlying());
  }

  @Test
  void suspendLeavesAPlayerInStaffModeAlone() {
    savePreference();
    enterStaffMode();
    player.setAllowFlight(true);

    FlightUtils.suspendFlight(player);

    assertTrue(player.getAllowFlight());
  }
}
