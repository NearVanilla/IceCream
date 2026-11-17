package com.nearvanilla.iceCream.modules.playerHeadDrops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlayerHeadDropsModuleTest {
  private static final String ENABLED =
      """
      modules:
        playerheaddrops:
          enabled: true
          drop-chance: 1.0
          require-player-kill: false
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

  private PlayerHeadDropsModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new PlayerHeadDropsModule();
  }

  private boolean deathDropsAHead() {
    PlayerMock victim = server.addPlayer();
    PlayerDeathEvent event =
        new PlayerDeathEvent(
            victim,
            DamageSource.builder(DamageType.GENERIC).build(),
            new ArrayList<>(),
            0,
            Component.text("died"),
            false);
    server.getPluginManager().callEvent(event);
    return event.getDrops().stream().anyMatch(item -> item.getType() == Material.PLAYER_HEAD);
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  playerheaddrops:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    PlayerHeadDropsModule module =
        module(
            "modules:\n  playerheaddrops:\n    drop-chance: 1.0\n    require-player-kill: false\n");

    module.register();

    assertFalse(module.isEnabled());
    assertFalse(deathDropsAHead());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    PlayerHeadDropsModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();

    assertTrue(deathDropsAHead());
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    PlayerHeadDropsModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
