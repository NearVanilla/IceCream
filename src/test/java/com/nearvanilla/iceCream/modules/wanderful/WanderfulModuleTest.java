package com.nearvanilla.iceCream.modules.wanderful;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.wanderful.commands.WanderfulCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WanderfulTestUtils;
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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

// The tests use WandType, which builds its persistent keys from IceCream.instance when the class
// loads, so the fixture is installed before any test touches the enum.
class WanderfulModuleTest {
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

  private WanderfulModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new WanderfulModule();
  }

  private boolean recipeIsRegistered(String id) {
    return Bukkit.getRecipe(new NamespacedKey(IceCream.instance, id)) != null;
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(WanderfulTestUtils.CONFIG).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  wanderful:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void notEnabledBeforeItIsRegistered() {
    assertFalse(module(WanderfulTestUtils.CONFIG).isEnabled());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    WanderfulModule module = module("");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("Wanderful module is disabled."), log.messages(Level.INFO));
    }

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertFalse(recipeIsRegistered("item_frame_recipe"));
    assertFalse(recipeIsRegistered("armor_stand_recipe"));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    WanderfulModule module = module(WanderfulTestUtils.CONFIG);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(List.of("Wanderful module has been enabled."), log.messages(Level.INFO));
    }

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(WanderfulTestUtils.CONFIG).register();

    verify(IceCream.annotationParser).parse(any(WanderfulCommand.class));
  }

  @Test
  void registerWhenEnabledAddsBothWandRecipes() {
    module(WanderfulTestUtils.CONFIG).register();

    assertTrue(recipeIsRegistered("item_frame_recipe"));
    assertTrue(recipeIsRegistered("armor_stand_recipe"));
  }

  @Test
  void registerWhenEnabledRegistersTheDamageListener() {
    module(WanderfulTestUtils.CONFIG).register();
    World world = server.addSimpleWorld("world");
    ItemFrame frame = world.spawn(new Location(world, 0, 64, 0), ItemFrame.class);
    frame.setVisible(false);

    damage(frame);

    assertTrue(frame.isVisible());
  }

  @Test
  void registerWhenEnabledRegistersTheInteractListener() {
    module(WanderfulTestUtils.CONFIG).register();
    World world = server.addSimpleWorld("world");
    ItemFrame frame = world.spawn(new Location(world, 0, 64, 0), ItemFrame.class);
    frame.setItem(new ItemStack(Material.DIAMOND));
    frame.setVisible(false);
    PlayerMock player = server.addPlayer();
    player.getInventory().setItemInMainHand(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event =
        new PlayerInteractEntityEvent(player, frame, EquipmentSlot.HAND);
    server.getPluginManager().callEvent(event);

    assertTrue(frame.isVisible());
    assertTrue(event.isCancelled());
  }

  @Test
  void registerWhenDisabledRegistersNoListeners() {
    module("").register();
    World world = server.addSimpleWorld("world");
    ItemFrame frame = world.spawn(new Location(world, 0, 64, 0), ItemFrame.class);
    frame.setVisible(false);

    damage(frame);

    assertFalse(frame.isVisible());
  }

  @Test
  void shippedDefaultsEnableTheModuleWithBothRecipes() throws IOException {
    String shipped;
    try (InputStream in = IceCream.class.getClassLoader().getResourceAsStream("config.yml")) {
      assertNotNull(in);
      shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    WanderfulModule module = module(shipped);

    module.register();

    assertTrue(module.isEnabled());
    assertTrue(recipeIsRegistered("item_frame_recipe"));
    assertTrue(recipeIsRegistered("armor_stand_recipe"));
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    WanderfulModule module = module(WanderfulTestUtils.CONFIG);
    doThrow(new IllegalStateException("no parser"))
        .when(IceCream.annotationParser)
        .parse(any(Object.class));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register Wanderful module: no parser"), log.messages(Level.SEVERE));
      assertEquals(List.of(), log.messages(Level.INFO));
    }

    assertFalse(module.isEnabled());
    assertFalse(recipeIsRegistered("item_frame_recipe"));
  }

  @Test
  void registerWhenListenerRegistrationFailsStaysDisabled() {
    WanderfulModule module = module(WanderfulTestUtils.CONFIG);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      assertEquals(
          List.of("Failed to register Wanderful module: no server"), log.messages(Level.SEVERE));
    }

    assertFalse(module.isEnabled());
  }

  @Test
  void registerWhenAWandMaterialIsInvalidStaysDisabled() {
    WanderfulModule module =
        module(
            """
            modules:
              wanderful:
                enabled: true
                item_frame:
                  type: NOT_A_MATERIAL
            """);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      List<String> severe = log.messages(Level.SEVERE);
      assertEquals(1, severe.size());
      assertTrue(
          severe.getFirst().startsWith("Failed to register Wanderful module: "), severe.getFirst());
    }

    assertFalse(module.isEnabled());
  }

  @Test
  void registerWhenARecipeShapeIsMissingStaysDisabled() {
    WanderfulModule module =
        module(
            """
            modules:
              wanderful:
                enabled: true
                item_frame:
                  type: WOODEN_SWORD
            """);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      module.register();

      List<String> severe = log.messages(Level.SEVERE);
      assertEquals(1, severe.size());
      assertTrue(
          severe.getFirst().startsWith("Failed to register Wanderful module: "), severe.getFirst());
    }

    assertFalse(module.isEnabled());
    assertNull(Bukkit.getRecipe(new NamespacedKey(IceCream.instance, "item_frame_recipe")));
  }

  @SuppressWarnings("removal")
  private void damage(ItemFrame frame) {
    server
        .getPluginManager()
        .callEvent(
            new EntityDamageEvent(
                frame,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.GENERIC).build(),
                1.0));
  }
}
