package com.nearvanilla.iceCream.modules.isSlimeChunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.isSlimeChunk.commands.isSlimeChunkCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class isSlimeChunkModuleTest {
  private static final String ENABLED = "modules:\n  isslimechunk:\n    enabled: true\n";

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

  private isSlimeChunkModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new isSlimeChunkModule();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  isslimechunk:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    isSlimeChunkModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    isSlimeChunkModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(isSlimeChunkCommand.class));
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    isSlimeChunkModule module = module(ENABLED);
    doThrow(new IllegalStateException("no parser"))
        .when(IceCream.annotationParser)
        .parse(any(Object.class));

    module.register();

    assertFalse(module.isEnabled());
  }
}
