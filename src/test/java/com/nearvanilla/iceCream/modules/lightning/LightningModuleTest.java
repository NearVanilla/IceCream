package com.nearvanilla.iceCream.modules.lightning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.lightning.commands.StrikeCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class LightningModuleTest {
  private static final String ENABLED = "modules:\n  lightning:\n    enabled: true\n";

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

  private LightningModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new LightningModule();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  lightning:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    LightningModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    LightningModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(StrikeCommand.class));
  }

  @Test
  void registersNoEventListeners() {
    LightningModule module = module(ENABLED);

    module.registerEvents();
    module.register();

    assertTrue(HandlerList.getRegisteredListeners(IceCream.instance).isEmpty());
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    LightningModule module = module(ENABLED);
    doThrow(new IllegalStateException("no parser"))
        .when(IceCream.annotationParser)
        .parse(any(Object.class));

    module.register();

    assertFalse(module.isEnabled());
  }
}
