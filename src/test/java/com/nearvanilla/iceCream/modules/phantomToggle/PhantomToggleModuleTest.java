package com.nearvanilla.iceCream.modules.phantomToggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.phantomToggle.commands.PhantomToggleCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PhantomMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PhantomToggleModuleTest {
  private static final String ENABLED = "modules:\n  phantomtoggle:\n    enabled: true\n";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    PhantomToggleModule.PHANTOM_DISABLED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private PhantomToggleModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new PhantomToggleModule();
  }

  private PlayerMock phantomFreePlayer() {
    PlayerMock player = server.addPlayer();
    PhantomToggleUtils.setPhantomDisabled(player, true);
    return player;
  }

  private PhantomPreSpawnEvent preSpawn(PlayerMock player) {
    return new PhantomPreSpawnEvent(
        player.getLocation(), player, CreatureSpawnEvent.SpawnReason.NATURAL);
  }

  private EntityTargetLivingEntityEvent target(PlayerMock player) {
    return new EntityTargetLivingEntityEvent(
        new PhantomMock(server, UUID.randomUUID()),
        player,
        EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  phantomtoggle:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    PhantomToggleModule module = module("");
    // register() creates the key even when the module is disabled, and marking a player needs it.
    module.register();
    PlayerMock player = phantomFreePlayer();
    PhantomPreSpawnEvent spawn = preSpawn(player);
    EntityTargetLivingEntityEvent targeting = target(player);

    server.getPluginManager().callEvent(spawn);
    server.getPluginManager().callEvent(targeting);

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertFalse(spawn.isCancelled());
    assertFalse(targeting.isCancelled());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    PhantomToggleModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(PhantomToggleCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersThePreSpawnListener() {
    module(ENABLED).register();
    PhantomPreSpawnEvent event = preSpawn(phantomFreePlayer());

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void registerWhenEnabledRegistersTheTargetListener() {
    module(ENABLED).register();
    EntityTargetLivingEntityEvent event = target(phantomFreePlayer());

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void registerCreatesTheKey() {
    IceCreamFixture.install(server, ENABLED);
    assertNull(PhantomToggleModule.PHANTOM_DISABLED_KEY);

    new PhantomToggleModule().register();

    assertEquals(
        new NamespacedKey("icecream", "phantomtoggle.disabled"),
        PhantomToggleModule.PHANTOM_DISABLED_KEY);
  }

  @Test
  void registerCreatesTheKeyEvenWhenDisabled() {
    IceCreamFixture.install(server, "");

    new PhantomToggleModule().register();

    assertEquals(
        new NamespacedKey("icecream", "phantomtoggle.disabled"),
        PhantomToggleModule.PHANTOM_DISABLED_KEY);
  }

  @Test
  void registerKeepsAnExistingKey() {
    PhantomToggleModule module = module(ENABLED);
    NamespacedKey existing = new NamespacedKey("other", "phantoms");
    PhantomToggleModule.PHANTOM_DISABLED_KEY = existing;

    module.register();

    assertSame(existing, PhantomToggleModule.PHANTOM_DISABLED_KEY);
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    PhantomToggleModule module = module(ENABLED);
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no parser"));

    module.register();
    PlayerMock player = phantomFreePlayer();
    PhantomPreSpawnEvent spawn = preSpawn(player);
    server.getPluginManager().callEvent(spawn);

    assertFalse(module.isEnabled());
    assertFalse(spawn.isCancelled());
  }

  @Test
  void registerWhenEventRegistrationFailsStaysDisabled() {
    PhantomToggleModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
