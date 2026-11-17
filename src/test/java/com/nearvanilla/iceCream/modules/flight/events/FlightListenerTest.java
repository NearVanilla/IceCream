package com.nearvanilla.iceCream.modules.flight.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.flight.FlightModule;
import com.nearvanilla.iceCream.modules.flight.FlightUtils;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FlightListenerTest {
  private ServerMock server;
  private World world;
  private PlayerMock player;
  private final FlightListener listener = new FlightListener();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    FlightModule.FLIGHT_ENABLED_KEY = new NamespacedKey(IceCream.instance, "flight.enabled");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY =
        new NamespacedKey(IceCream.instance, "staff_mode.enabled");
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Flyer");
    player.addAttachment(IceCream.instance, FlightUtils.PERMISSION, true);
    player
        .getPersistentDataContainer()
        .set(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, true);
  }

  @AfterEach
  void tearDown() {
    FlightModule.FLIGHT_ENABLED_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // MockBukkit's disconnect() clears the player's permission attachments. Without the permission a
  // restore would suspend flight, which also leaves allowFlight false, so the permission is granted
  // again to make only the online check keep the player from being restored.
  private void allowFlyingAfterDisconnect() {
    player.addAttachment(IceCream.instance, FlightUtils.PERMISSION, true);
    assertTrue(player.hasPermission(FlightUtils.PERMISSION));
  }

  private PlayerJoinEvent join() {
    return new PlayerJoinEvent(player, Component.text("joined"));
  }

  private PlayerGameModeChangeEvent gameModeChange(GameMode newMode) {
    return new PlayerGameModeChangeEvent(
        player, newMode, PlayerGameModeChangeEvent.Cause.PLUGIN, null);
  }

  private PlayerQuitEvent quit() {
    return new PlayerQuitEvent(
        player, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
  }

  @Test
  void joiningDoesNotRestoreFlightUntilTheNextTick() {
    listener.onJoin(join());

    assertFalse(player.getAllowFlight());
  }

  @Test
  void joiningRestoresFlightOnTheNextTick() {
    listener.onJoin(join());

    server.getScheduler().performTicks(1);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void joiningDoesNotRestoreFlightForAPlayerWithoutAPreference() {
    player.getPersistentDataContainer().remove(FlightModule.FLIGHT_ENABLED_KEY);

    listener.onJoin(join());
    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void joiningDoesNotRestoreFlightForAPlayerWhoLeftBeforeTheTick() {
    listener.onJoin(join());
    player.disconnect();
    allowFlyingAfterDisconnect();

    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void joiningDoesNotRestoreFlightForADeadPlayer() {
    player.setHealth(0);
    assertTrue(player.isDead());

    listener.onJoin(join());
    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void respawningRestoresFlightImmediately() {
    listener.onRespawn(
        new PlayerPostRespawnEvent(
            player,
            new Location(world, 0, 64, 0),
            false,
            false,
            false,
            PlayerRespawnEvent.RespawnReason.DEATH));

    assertTrue(player.getAllowFlight());
  }

  @Test
  void changingGameModeRestoresFlightOnTheNextTick() {
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(false);
    player.setGameMode(GameMode.SURVIVAL);
    player.setAllowFlight(false);

    listener.onGameModeChange(gameModeChange(GameMode.SURVIVAL));
    assertFalse(player.getAllowFlight());
    server.getScheduler().performTicks(1);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void changingToCreativeLeavesFlightToTheGameMode() {
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(false);

    listener.onGameModeChange(gameModeChange(GameMode.CREATIVE));
    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void aServerGameModeChangeRestoresFlightOnTheNextTick() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);

    server.getPluginManager().callEvent(gameModeChange(GameMode.SURVIVAL));
    server.getScheduler().performTicks(1);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void aCancelledGameModeChangeIsIgnored() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    PlayerGameModeChangeEvent event = gameModeChange(GameMode.SURVIVAL);
    event.setCancelled(true);

    server.getPluginManager().callEvent(event);
    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void changingWorldRestoresFlightOnTheNextTick() {
    listener.onWorldChange(new PlayerChangedWorldEvent(player, world));
    assertFalse(player.getAllowFlight());

    server.getScheduler().performTicks(1);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void changingWorldDoesNotRestoreFlightForAPlayerWhoLeftBeforeTheTick() {
    listener.onWorldChange(new PlayerChangedWorldEvent(player, world));
    player.disconnect();
    allowFlyingAfterDisconnect();

    server.getScheduler().performTicks(1);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void quittingSuspendsFlightImmediately() {
    player.setAllowFlight(true);
    player.setFlying(true);

    listener.onQuit(quit());

    assertFalse(player.getAllowFlight());
    assertFalse(player.isFlying());
  }

  @Test
  void quittingKeepsThePreferenceForTheNextSession() {
    player.setAllowFlight(true);

    listener.onQuit(quit());

    assertTrue(FlightUtils.isEnabled(player));
  }

  @Test
  void quittingLeavesFlightFromOtherSourcesAlone() {
    player.getPersistentDataContainer().remove(FlightModule.FLIGHT_ENABLED_KEY);
    player.setAllowFlight(true);

    listener.onQuit(quit());

    assertTrue(player.getAllowFlight());
  }

  @Test
  void quittingLeavesACreativePlayersFlightAlone() {
    player.setGameMode(GameMode.CREATIVE);
    player.setAllowFlight(true);

    listener.onQuit(quit());

    assertTrue(player.getAllowFlight());
  }

  @Test
  void aServerJoinEventRestoresFlightOnTheNextTick() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);

    server.getPluginManager().callEvent(join());
    server.getScheduler().performTicks(1);

    assertTrue(player.getAllowFlight());
  }

  @Test
  void aServerQuitEventSuspendsFlight() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    player.setAllowFlight(true);

    server.getPluginManager().callEvent(quit());

    assertFalse(player.getAllowFlight());
  }
}
