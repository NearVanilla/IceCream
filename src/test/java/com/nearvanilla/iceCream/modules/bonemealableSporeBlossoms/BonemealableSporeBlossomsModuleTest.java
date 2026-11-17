package com.nearvanilla.iceCream.modules.bonemealableSporeBlossoms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BonemealableSporeBlossomsModuleTest {
  private static final String ENABLED =
      "modules:\n  bonemealablesporeblossoms:\n    enabled: true\n";

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

  private BonemealableSporeBlossomsModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new BonemealableSporeBlossomsModule();
  }

  // Uses bone meal on a spore blossom and reports whether it was duplicated.
  private boolean bonemealDuplicatesABlossom() {
    World world = server.addSimpleWorld("world");
    Block blossom = world.getBlockAt(0, 70, 0);
    blossom.setType(Material.SPORE_BLOSSOM);
    PlayerMock player = server.addPlayer();
    player.setGameMode(GameMode.SURVIVAL);
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

    server
        .getPluginManager()
        .callEvent(
            new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                boneMeal,
                blossom,
                BlockFace.DOWN,
                EquipmentSlot.HAND));

    return !world.getEntitiesByClass(Item.class).isEmpty();
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(
        module("modules:\n  bonemealablesporeblossoms:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    BonemealableSporeBlossomsModule module = module("");

    module.register();

    assertFalse(module.isEnabled());
    assertFalse(bonemealDuplicatesABlossom());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    BonemealableSporeBlossomsModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();

    assertTrue(bonemealDuplicatesABlossom());
  }

  @Test
  void registerWhenRegistrationFailsStaysDisabled() {
    BonemealableSporeBlossomsModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
