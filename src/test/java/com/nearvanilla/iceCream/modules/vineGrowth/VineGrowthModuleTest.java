package com.nearvanilla.iceCream.modules.vineGrowth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vineGrowth.events.VineGrowthListener;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockGrowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class VineGrowthModuleTest {
  private static final String ENABLED = "modules:\n  vinegrowth:\n    enabled: true\n";

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

  private VineGrowthModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new VineGrowthModule();
  }

  private static long vineListeners() {
    return HandlerList.getRegisteredListeners(IceCream.instance).stream()
        .map(registered -> registered.getListener())
        .filter(VineGrowthListener.class::isInstance)
        .distinct()
        .count();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  vinegrowth:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    VineGrowthModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    assertEquals(0, HandlerList.getRegisteredListeners(IceCream.instance).size());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    VineGrowthModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersNoCommands() {
    module(ENABLED).register();

    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();
    WorldMock world = server.addSimpleWorld("world");
    Block vine = world.getBlockAt(0, 64, 0);
    vine.setType(Material.VINE);
    VineGrowthUtils.setSheared(vine, true);

    BlockGrowEvent growth = new BlockGrowEvent(vine, vine.getState());
    server.getPluginManager().callEvent(growth);

    assertEquals(1, vineListeners());
    assertTrue(growth.isCancelled());
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabledAndReportsIt() {
    VineGrowthModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register Vine Growth module: no server"), log.messages(Level.SEVERE));
      assertTrue(log.messages(Level.INFO).isEmpty());
    }

    assertFalse(module.isEnabled());
  }
}
