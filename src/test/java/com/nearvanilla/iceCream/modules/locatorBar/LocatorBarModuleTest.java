package com.nearvanilla.iceCream.modules.locatorBar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.locatorBar.commands.LocatorBarCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class LocatorBarModuleTest {
  private static final String ENABLED = "modules:\n  locatorbar:\n    enabled: true\n";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private LocatorBarModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new LocatorBarModule();
  }

  private double disabledRange(String configuredRange) {
    module("modules:\n  locatorbar:\n    disabled-receive-range: " + configuredRange + "\n");
    return LocatorBarModule.getDisabledReceiveRange();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  locatorbar:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    LocatorBarModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    LocatorBarModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(LocatorBarCommand.class));
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    LocatorBarModule module = module(ENABLED);
    doThrow(new IllegalStateException("no parser"))
        .when(IceCream.annotationParser)
        .parse(any(Object.class));

    module.register();

    assertFalse(module.isEnabled());
  }

  @Test
  void registerCreatesThePreviousRangeKey() {
    assertNull(LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY);

    module(ENABLED).register();

    assertEquals(
        new NamespacedKey("icecream", "locatorbar.previous_range"),
        LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY);
  }

  @Test
  void registerCreatesThePreviousRangeKeyEvenWhenDisabled() {
    module("").register();

    assertNotNull(LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY);
  }

  @Test
  void registerKeepsAnExistingPreviousRangeKey() {
    LocatorBarModule module = module(ENABLED);
    module.register();
    NamespacedKey first = LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY;

    module.register();

    assertSame(first, LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY);
  }

  @Test
  void disabledReceiveRangeDefaultsToTheMinimumWhenNotConfigured() {
    module("");

    assertEquals(0.1, LocatorBarModule.getDisabledReceiveRange());
  }

  @Test
  void disabledReceiveRangeReturnsAConfiguredValueInRange() {
    assertEquals(32.5, disabledRange("32.5"));
  }

  @Test
  void disabledReceiveRangeAcceptsAWholeNumber() {
    assertEquals(16.0, disabledRange("16"));
  }

  @Test
  void disabledReceiveRangeAcceptsTheMinimumExactly() {
    assertEquals(0.1, disabledRange("0.1"));
  }

  @Test
  void disabledReceiveRangeAcceptsTheMaximumExactly() {
    assertEquals(60_000_000.0, disabledRange("60000000"));
  }

  @Test
  void disabledReceiveRangeRaisesZeroToTheMinimum() {
    assertEquals(0.1, disabledRange("0"));
  }

  @Test
  void disabledReceiveRangeRaisesANegativeValueToTheMinimum() {
    assertEquals(0.1, disabledRange("-5"));
  }

  @Test
  void disabledReceiveRangeRaisesAValueJustBelowTheMinimum() {
    assertEquals(0.1, disabledRange("0.05"));
  }

  @Test
  void disabledReceiveRangeLowersAHugeValueToTheMaximum() {
    assertEquals(60_000_000.0, disabledRange("999999999"));
  }

  @Test
  void disabledReceiveRangeFallsBackToTheMinimumForPositiveInfinity() {
    assertEquals(0.1, disabledRange(".inf"));
  }

  @Test
  void disabledReceiveRangeFallsBackToTheMinimumForNegativeInfinity() {
    assertEquals(0.1, disabledRange("-.inf"));
  }

  @Test
  void disabledReceiveRangeFallsBackToTheMinimumForNaN() {
    assertEquals(0.1, disabledRange(".nan"));
  }

  @Test
  void disabledReceiveRangeFallsBackToTheMinimumForANonNumber() {
    assertEquals(0.1, disabledRange("far"));
  }
}
