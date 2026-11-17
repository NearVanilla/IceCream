package com.nearvanilla.iceCream.modules.deathLocation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class DeathLocationModuleTest {
  private static final String ENABLED = "modules:\n  deathlocation:\n    enabled: true\n";

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

  private DeathLocationModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new DeathLocationModule();
  }

  private void kill(PlayerMock player) {
    server
        .getPluginManager()
        .callEvent(
            new PlayerDeathEvent(
                player,
                DamageSource.builder(DamageType.GENERIC).build(),
                new ArrayList<ItemStack>(),
                0,
                Component.text("died"),
                false));
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  deathlocation:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    DeathLocationModule module = module("");
    PlayerMock player = server.addPlayer();

    module.register();
    kill(player);

    assertFalse(module.isEnabled());
    assertNull(player.nextComponentMessage());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    DeathLocationModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();
    PlayerMock player = server.addPlayer();

    kill(player);

    String message =
        PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    assertTrue(message.startsWith("You died at <"), message);
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    DeathLocationModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
