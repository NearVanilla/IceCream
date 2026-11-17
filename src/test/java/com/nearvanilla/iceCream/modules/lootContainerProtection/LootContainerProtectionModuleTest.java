package com.nearvanilla.iceCream.modules.lootContainerProtection;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.lootContainerProtection.events.LootContainerProtectionListener;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LootableTestUtils;
import java.util.ArrayList;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LootContainerProtectionModuleTest {
  private static final String ENABLED = "modules:\n  lootcontainerprotection:\n    enabled: true\n";

  private ServerMock server;
  private World world;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Looter");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private LootContainerProtectionModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new LootContainerProtectionModule();
  }

  private BlockBreakEvent breakLootChest() {
    LootTable table = LootableTestUtils.lootTable("chests/simple_dungeon");
    Block chest = LootableTestUtils.chest(world, 0, 64, 0, table);
    return new BlockBreakEvent(chest, player);
  }

  private long protectionListeners() {
    return HandlerList.getRegisteredListeners(IceCream.instance).stream()
        .map(RegisteredListener::getListener)
        .filter(LootContainerProtectionListener.class::isInstance)
        .distinct()
        .count();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(
        module("modules:\n  lootcontainerprotection:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    LootContainerProtectionModule module = module("");
    BlockBreakEvent event = breakLootChest();

    module.register();
    server.getPluginManager().callEvent(event);

    assertFalse(module.isEnabled());
    assertFalse(event.isCancelled());
    assertEquals(0, protectionListeners());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    LootContainerProtectionModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();
    BlockBreakEvent event = breakLootChest();

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertEquals("You need to sneak to break loot containers!", nextMessage(player));
  }

  @Test
  void registerWhenEnabledRegistersNoCommands() {
    module(ENABLED).register();

    verify(IceCream.annotationParser, never()).parse(any(Object.class));
  }

  @Test
  void registerWhenEnabledSavesTablesUnderTheModulesOwnKey() {
    module(ENABLED).register();
    LootTable table = LootableTestUtils.lootTable("chests/simple_dungeon");
    Block chest = LootableTestUtils.chest(world, 0, 64, 0, table);
    LootGenerateEvent event =
        new LootGenerateEvent(
            world,
            null,
            (InventoryHolder) chest.getState(false),
            table,
            mock(LootContext.class),
            new ArrayList<>(),
            false);

    server.getPluginManager().callEvent(event);

    assertEquals(
        "minecraft:chests/simple_dungeon",
        ((TileState) chest.getState())
            .getPersistentDataContainer()
            .get(
                new NamespacedKey("icecream", "lootcontainer.original_table"),
                PersistentDataType.STRING));
  }

  @Test
  void registerTwiceRegistersTheListenerOnce() {
    LootContainerProtectionModule module = module(ENABLED);

    module.register();
    module.register();

    assertEquals(1, protectionListeners());
    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    LootContainerProtectionModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
    assertEquals(0, protectionListeners());
  }

  @Test
  void registerWhenRegistrationFailsAfterTheListenerWasAddedRemovesIt() {
    LootContainerProtectionModule module = module(ENABLED);
    PluginManager realManager = server.getPluginManager();
    PluginManager failingManager = mock(PluginManager.class);
    doAnswer(
            invocation -> {
              realManager.registerEvents(invocation.getArgument(0), invocation.getArgument(1));
              throw new IllegalStateException("registered, then failed");
            })
        .when(failingManager)
        .registerEvents(any(Listener.class), any(Plugin.class));
    Server failingServer = mock(Server.class);
    when(failingServer.getPluginManager()).thenReturn(failingManager);
    doReturn(failingServer).when(IceCream.instance).getServer();
    BlockBreakEvent event = breakLootChest();

    module.register();
    server.getPluginManager().callEvent(event);

    assertFalse(module.isEnabled());
    assertFalse(event.isCancelled());
    assertEquals(0, protectionListeners());
  }

  @Test
  void registerCanBeRetriedAfterARegistrationFailure() {
    LootContainerProtectionModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));
    module.register();
    doReturn(server).when(IceCream.instance).getServer();

    module.register();

    assertTrue(module.isEnabled());
    assertEquals(1, protectionListeners());
  }

  @Test
  void unregisterMarksTheModuleDisabled() {
    LootContainerProtectionModule module = module(ENABLED);
    module.register();

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void unregisterRemovesTheListener() {
    LootContainerProtectionModule module = module(ENABLED);
    module.register();
    BlockBreakEvent event = breakLootChest();

    module.unregister();
    server.getPluginManager().callEvent(event);

    assertFalse(event.isCancelled());
    assertNull(nextMessage(player));
    assertEquals(0, protectionListeners());
  }

  @Test
  void unregisterWhenNeverRegisteredDoesNothing() {
    LootContainerProtectionModule module = module(ENABLED);

    module.unregister();

    assertFalse(module.isEnabled());
  }

  @Test
  void registerAfterUnregisterProtectsAgain() {
    LootContainerProtectionModule module = module(ENABLED);
    module.register();
    module.unregister();

    module.register();
    BlockBreakEvent event = breakLootChest();
    server.getPluginManager().callEvent(event);

    assertTrue(module.isEnabled());
    assertTrue(event.isCancelled());
    assertEquals(1, protectionListeners());
  }
}
