package com.nearvanilla.iceCream.modules.desertMobs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DesertMobsModuleTest {
  private static final String ENABLED =
      """
      modules:
        desertmobs:
          enabled: true
          spawn:
            zombie-to-husk-chance: 1.0
      """;

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private DesertMobsModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new DesertMobsModule();
  }

  // Spawns a zombie in a desert before the module registers, so the spawn itself is not seen by
  // the listener, then reports whether the listener cancelled a fresh spawn event for it.
  private boolean registeredListenerCancelsADesertZombie(DesertMobsModule module) {
    World world = server.addSimpleWorld("world");
    Location desert = new Location(world, 0, 64, 0);
    world.setBiome(desert, Biome.DESERT);
    CreatureSpawnEvent event =
        new CreatureSpawnEvent(world.spawn(desert, Zombie.class), SpawnReason.NATURAL);

    module.register();
    server.getPluginManager().callEvent(event);

    return event.isCancelled();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  desertmobs:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    DesertMobsModule module =
        module("modules:\n  desertmobs:\n    spawn:\n      zombie-to-husk-chance: 1.0\n");

    boolean cancelled = registeredListenerCancelsADesertZombie(module);

    assertFalse(module.isEnabled());
    assertFalse(cancelled);
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    DesertMobsModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    assertTrue(registeredListenerCancelsADesertZombie(module(ENABLED)));
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    DesertMobsModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
