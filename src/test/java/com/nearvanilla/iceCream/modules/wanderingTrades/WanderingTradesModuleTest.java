package com.nearvanilla.iceCream.modules.wanderingTrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import tools.jackson.core.type.TypeReference;

class WanderingTradesModuleTest {
  private static final String RESOURCE = "data/wandering_trades.json";
  private static final String ENABLED = "modules:\n  wanderingtrades:\n    enabled: true\n";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    WanderingTradesTestUtils.reset();
  }

  @AfterEach
  void tearDown() {
    WanderingTradesTestUtils.reset();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private WanderingTradesModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new WanderingTradesModule();
  }

  private boolean recipeIsRegistered(String id) {
    return Bukkit.getRecipe(new NamespacedKey(IceCream.instance, id)) != null;
  }

  private int bundledTradeCount() throws IOException {
    try (InputStream in = IceCream.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      assertNotNull(in);
      return JsonLoader.load(in, new TypeReference<List<HeadTradeData>>() {}).size();
    }
  }

  // MockBukkit announces the spawn with a CreatureSpawnEvent, like a real server.
  private WanderingTrader spawnTrader() {
    World world = server.addSimpleWorld("world");
    return world.spawn(new Location(world, 0, 64, 0), WanderingTrader.class);
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  wanderingtrades:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void notEnabledBeforeItIsRegistered() {
    assertFalse(module(ENABLED).isEnabled());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    WanderingTradesModule module = module("");
    WanderingTradesModule.headTradePool.add(new MerchantRecipe(new ItemStack(Material.DIRT), 1));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("Wandering Trades module is disabled."), log.messages(Level.INFO));
    }
    WanderingTrader trader = spawnTrader();

    assertFalse(module.isEnabled());
    assertEquals(1, WanderingTradesModule.headTradePool.size());
    assertEquals(List.of(), trader.getRecipes());
    assertFalse(recipeIsRegistered("miniblock/mini_chest"));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    WanderingTradesModule module = module(ENABLED);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("Wandering Trades module has been enabled."), log.messages(Level.INFO));
    }

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersNoCommands() {
    module(ENABLED).register();

    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerLoadsTheBundledTradesIntoThePool() throws IOException {
    module(ENABLED).register();

    assertTrue(bundledTradeCount() > 0);
    assertEquals(bundledTradeCount(), WanderingTradesModule.headTradePool.size());
  }

  @Test
  void registerRegistersStonecutterRecipesWhenTheKeyIsMissing() {
    module(ENABLED).register();

    assertTrue(recipeIsRegistered("miniblock/mini_chest"));
  }

  @Test
  void registerRegistersStonecutterRecipesWhenEnabledInTheConfig() {
    module(WanderingTradesTestUtils.CONFIG).register();

    assertTrue(recipeIsRegistered("miniblock/mini_chest"));
  }

  @Test
  void registerSkipsStonecutterRecipesWhenTheyAreDisabled() {
    WanderingTradesModule module =
        module(
            """
            modules:
              wanderingtrades:
                enabled: true
                stonecutter: false
            """);

    module.register();

    assertTrue(module.isEnabled());
    assertFalse(recipeIsRegistered("miniblock/mini_chest"));
    assertFalse(WanderingTradesModule.headTradePool.isEmpty());
  }

  @Test
  void registerRegistersTheSpawnListener() {
    module(
            """
            modules:
              wanderingtrades:
                enabled: true
                min-heads: 3
                max-heads: 3
            """)
        .register();

    WanderingTrader trader = spawnTrader();

    assertEquals(3, trader.getRecipes().size());
  }

  @Test
  void registerWhenTheTradesFileIsMissingStaysDisabled() {
    WanderingTradesModule module = module(ENABLED);
    when(IceCream.instance.getResource(RESOURCE)).thenReturn(null);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of(
              "Failed to register Wandering Trades module: Failed to copy Wandering Trades config"
                  + " from JAR."),
          log.messages(Level.SEVERE));
      assertEquals(List.of(), log.messages(Level.INFO));
    }

    assertFalse(module.isEnabled());
    assertTrue(WanderingTradesModule.headTradePool.isEmpty());
    assertFalse(recipeIsRegistered("miniblock/mini_chest"));
  }

  @Test
  void registerWhenTheTradesFileIsMalformedStaysDisabled() {
    WanderingTradesModule module = module(ENABLED);
    when(IceCream.instance.getResource(RESOURCE))
        .thenReturn(WanderingTradesTestUtils.stream("[{\"name\":"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      List<String> severe = log.messages(Level.SEVERE);
      assertEquals(1, severe.size());
      assertTrue(
          severe.getFirst().startsWith("Failed to register Wandering Trades module: "),
          severe.getFirst());
    }

    assertFalse(module.isEnabled());
    assertTrue(WanderingTradesModule.headTradePool.isEmpty());
    assertFalse(recipeIsRegistered("miniblock/mini_chest"));
  }

  @Test
  void registerWhenListenerRegistrationFailsStaysDisabled() {
    WanderingTradesModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register Wandering Trades module: no server"),
          log.messages(Level.SEVERE));
    }

    assertFalse(module.isEnabled());
    assertTrue(WanderingTradesModule.headTradePool.isEmpty());
  }

  @Test
  void registerClosesTheTradesFile() {
    WanderingTradesModule module = module(ENABLED);
    boolean[] closed = {false};
    InputStream tracked =
        new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)) {
          @Override
          public void close() throws IOException {
            closed[0] = true;
            super.close();
          }
        };
    when(IceCream.instance.getResource(RESOURCE)).thenReturn(tracked);

    module.register();

    assertTrue(module.isEnabled());
    assertTrue(closed[0]);
  }

  @Test
  void unregisterRemovesTheStonecutterRecipesAndMarksTheModuleDisabled() {
    WanderingTradesModule module = module(ENABLED);
    module.register();
    assertTrue(recipeIsRegistered("miniblock/mini_chest"));

    module.unregister();

    assertFalse(module.isEnabled());
    assertFalse(recipeIsRegistered("miniblock/mini_chest"));
    assertNull(Bukkit.getRecipe(new NamespacedKey(IceCream.instance, "miniblock/mini_furnace")));
  }

  @Test
  void unregisterWhenNeverRegisteredLeavesTheModuleDisabled() {
    WanderingTradesModule module = module(ENABLED);

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void registerAfterUnregisterEnablesTheModuleAgain() {
    WanderingTradesModule module = module(ENABLED);
    module.register();
    module.unregister();

    module.register();

    assertTrue(module.isEnabled());
    assertTrue(recipeIsRegistered("miniblock/mini_chest"));
  }
}
