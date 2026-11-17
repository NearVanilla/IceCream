package com.nearvanilla.iceCream.modules.muteDeaths;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.muteDeaths.commands.MuteDeathsCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MuteDeathsModuleTest {
  private static final String ENABLED = "modules:\n  mutedeaths:\n    enabled: true\n";

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

  private MuteDeathsModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new MuteDeathsModule();
  }

  private PlayerMock mutedPlayer() {
    PlayerMock player = server.addPlayer();
    player
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, true);
    return player;
  }

  private PlayerJoinEvent join(PlayerMock player) {
    return new PlayerJoinEvent(player, Component.text("joined"));
  }

  private PlayerDeathEvent death(PlayerMock player) {
    return new PlayerDeathEvent(
        player,
        DamageSource.builder(DamageType.GENERIC).build(),
        new ArrayList<>(),
        0,
        Component.text("died"),
        true);
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  mutedeaths:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    MuteDeathsModule module = module("");
    PlayerMock player = mutedPlayer();

    module.register();
    server.getPluginManager().callEvent(join(player));

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertNull(nextMessage(player));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    MuteDeathsModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(MuteDeathsCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheJoinListener() {
    module(ENABLED).register();
    PlayerMock player = mutedPlayer();

    server.getPluginManager().callEvent(join(player));

    assertEquals("🔕 Your death messages are muted. [Toggle]", nextMessage(player));
  }

  @Test
  void registerWhenEnabledRegistersTheDeathListener() {
    module(ENABLED).register();
    PlayerDeathEvent event = death(mutedPlayer());

    server.getPluginManager().callEvent(event);

    assertNull(event.deathMessage());
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    MuteDeathsModule module = module(ENABLED);
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no parser"));
    PlayerMock player = mutedPlayer();

    module.register();
    server.getPluginManager().callEvent(join(player));

    assertFalse(module.isEnabled());
    assertNull(nextMessage(player));
  }

  @Test
  void registerWhenEventRegistrationFailsStaysDisabled() {
    MuteDeathsModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }

  @Test
  void muteKeyIsInTheMuteDeathsNamespace() {
    assertEquals("mutedeaths:toggle", MuteDeathsModule.MUTE_KEY.asString());
  }
}
