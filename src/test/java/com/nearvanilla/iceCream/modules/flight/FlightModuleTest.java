package com.nearvanilla.iceCream.modules.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.flight.commands.FlyCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FlightModuleTest {
  private static final String ENABLED = "modules:\n  flight:\n    enabled: true\n";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    FlightModule.FLIGHT_ENABLED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private FlightModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    FlightModule.FLIGHT_ENABLED_KEY = new NamespacedKey(IceCream.instance, "flight.enabled");
    return new FlightModule();
  }

  // A survival player who has saved the flight preference and may fly, so flight is restored for
  // them whenever the module runs.
  private PlayerMock flier() {
    PlayerMock player = server.addPlayer();
    player.addAttachment(IceCream.instance, FlightUtils.PERMISSION, true);
    player
        .getPersistentDataContainer()
        .set(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, true);
    return player;
  }

  private void quit(PlayerMock player) {
    server
        .getPluginManager()
        .callEvent(
            new PlayerQuitEvent(
                player, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED));
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  flight:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    FlightModule module = module("");
    PlayerMock player = flier();
    player.setAllowFlight(true);

    module.register();
    quit(player);

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertTrue(player.getAllowFlight());
  }

  @Test
  void registerWhenDisabledLeavesOnlinePlayersAlone() {
    FlightModule module = module("");
    PlayerMock player = flier();

    module.register();

    assertFalse(player.getAllowFlight());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    FlightModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(FlyCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    FlightModule module = module(ENABLED);
    PlayerMock player = flier();
    module.register();
    assertTrue(player.getAllowFlight());

    quit(player);

    assertFalse(player.getAllowFlight());
  }

  @Test
  void registerWhenEnabledRestoresFlightForPlayersAlreadyOnline() {
    FlightModule module = module(ENABLED);
    PlayerMock player = flier();
    assertFalse(player.getAllowFlight());

    module.register();

    assertTrue(player.getAllowFlight());
  }

  @Test
  void registerCreatesTheFlightKey() {
    IceCreamFixture.install(server, ENABLED);
    assertNull(FlightModule.FLIGHT_ENABLED_KEY);

    new FlightModule().register();

    assertEquals(new NamespacedKey("icecream", "flight.enabled"), FlightModule.FLIGHT_ENABLED_KEY);
  }

  @Test
  void registerCreatesTheFlightKeyEvenWhenDisabled() {
    IceCreamFixture.install(server, "");

    new FlightModule().register();

    assertEquals(new NamespacedKey("icecream", "flight.enabled"), FlightModule.FLIGHT_ENABLED_KEY);
  }

  @Test
  void unregisterSuspendsFlightForOnlinePlayers() {
    FlightModule module = module(ENABLED);
    PlayerMock player = flier();
    module.register();
    player.setFlying(true);

    module.unregister();

    assertFalse(player.getAllowFlight());
    assertFalse(player.isFlying());
  }

  @Test
  void unregisterMarksTheModuleDisabled() {
    FlightModule module = module(ENABLED);
    module.register();

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterKeepsPlayersSavedPreferences() {
    FlightModule module = module(ENABLED);
    PlayerMock player = flier();
    module.register();

    module.unregister();

    assertTrue(FlightUtils.isEnabled(player));
  }

  @Test
  void unregisterWhenNotEnabledDoesNothing() {
    FlightModule module = module("");
    PlayerMock player = flier();
    module.register();
    player.setAllowFlight(true);

    module.unregister();

    assertTrue(player.getAllowFlight());
    assertFalse(module.isEnabled());
  }
}
