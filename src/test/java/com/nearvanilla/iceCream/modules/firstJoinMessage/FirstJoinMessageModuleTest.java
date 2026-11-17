package com.nearvanilla.iceCream.modules.firstJoinMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FirstJoinMessageModuleTest {
  private static final String ENABLED =
      """
      modules:
        firstjoinmessage:
          enabled: true
          announcement: 'Welcome <player>'
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

  private FirstJoinMessageModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new FirstJoinMessageModule();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  firstjoinmessage:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    FirstJoinMessageModule module =
        module("modules:\n  firstjoinmessage:\n    announcement: 'Welcome <player>'\n");

    module.register();
    PlayerMock player = server.addPlayer();

    assertFalse(module.isEnabled());
    assertNull(player.nextComponentMessage());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    FirstJoinMessageModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();

    PlayerMock player = server.addPlayer("Newbie");

    assertEquals(
        "Welcome Newbie",
        PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    FirstJoinMessageModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
