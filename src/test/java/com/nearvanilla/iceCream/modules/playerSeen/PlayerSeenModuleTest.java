package com.nearvanilla.iceCream.modules.playerSeen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.playerSeen.commands.SeenCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentMatchers;

class PlayerSeenModuleTest {
  private static final String ENABLED = "modules:\n  playerseen:\n    enabled: true\n";

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

  private PlayerSeenModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new PlayerSeenModule();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  playerseen:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    PlayerSeenModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    PlayerSeenModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(SeenCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersNoListeners() {
    module(ENABLED).register();

    assertTrue(HandlerList.getRegisteredListeners(IceCream.instance).isEmpty());
  }

  @Test
  void registerWhenTheCommandFailsStaysDisabledAndReportsIt() {
    PlayerSeenModule module = module(ENABLED);
    doThrow(new IllegalStateException("no commands"))
        .when(IceCream.annotationParser)
        .parse(ArgumentMatchers.<Object>any());

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register PlayerSeen module: no commands"), log.messages(Level.SEVERE));
      assertTrue(log.messages(Level.INFO).isEmpty());
    }

    assertFalse(module.isEnabled());
  }
}
