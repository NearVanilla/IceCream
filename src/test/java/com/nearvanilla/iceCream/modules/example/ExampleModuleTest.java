package com.nearvanilla.iceCream.modules.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.example.commands.ExampleCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A module is tested with {@link IceCreamFixture}, which installs a mocked plugin with the given
 * configuration, so {@code shouldEnable()} reads a real config. Whether {@code register()} wired
 * the listener is proved by causing its event and reading what the player received, using {@link
 * MessageTestUtils}, not by verifying calls on a mock.
 */
class ExampleModuleTest {
  private static final String ENABLED = "modules:\n  example:\n    enabled: true\n";

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

  private ExampleModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new ExampleModule();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  example:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    ExampleModule module = module("");

    module.register();
    PlayerMock player = server.addPlayer();

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertNull(player.nextComponentMessage());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    ExampleModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(ExampleCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();

    PlayerMock player = server.addPlayer();

    assertEquals(
        "Welcome to the server! This is an example event.", MessageTestUtils.nextMessage(player));
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    ExampleModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
