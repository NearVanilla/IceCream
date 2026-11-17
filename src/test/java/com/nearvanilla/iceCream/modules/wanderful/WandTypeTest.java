package com.nearvanilla.iceCream.modules.wanderful;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WanderfulTestUtils;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

// WandType builds its persistent keys from IceCream.instance when the class loads, so the fixture
// is installed before any test touches the enum, and no annotation here may name it.
class WandTypeTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WanderfulTestUtils.CONFIG);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  @Test
  void declaresTheItemFrameWandBeforeTheArmorStandWand() {
    assertArrayEquals(
        new WandType[] {WandType.ITEM_FRAME, WandType.ARMOR_STAND}, WandType.values());
  }

  @Test
  void idIsTheLowercaseConstantName() {
    assertEquals("item_frame", WandType.ITEM_FRAME.getId());
    assertEquals("armor_stand", WandType.ARMOR_STAND.getId());
  }

  @Test
  void nameReplacesUnderscoresWithSpaces() {
    assertEquals("item frame", WandType.ITEM_FRAME.getName());
    assertEquals("armor stand", WandType.ARMOR_STAND.getName());
  }

  @Test
  void configKeyPrefixEndsWithADotSoAKeyCanBeAppended() {
    assertEquals("modules.wanderful.item_frame.", WandType.ITEM_FRAME.getConfigKeyPrefix());
    assertEquals("modules.wanderful.armor_stand.", WandType.ARMOR_STAND.getConfigKeyPrefix());
  }

  @Test
  void persistentKeyIsInThePluginNamespace() {
    assertEquals("icecream", WandType.ITEM_FRAME.getMainPersistentKey().getNamespace());
    assertEquals("item_frame_wand", WandType.ITEM_FRAME.getMainPersistentKey().getKey());
    assertEquals("icecream", WandType.ARMOR_STAND.getMainPersistentKey().getNamespace());
    assertEquals("armor_stand_wand", WandType.ARMOR_STAND.getMainPersistentKey().getKey());
  }

  @Test
  void eachWandHasItsOwnPersistentKey() {
    assertNotEquals(
        WandType.ITEM_FRAME.getMainPersistentKey(), WandType.ARMOR_STAND.getMainPersistentKey());
  }

  @Test
  void createItemBuildsTheConfiguredWand() {
    ItemStack itemFrameWand = WandType.ITEM_FRAME.createItem();
    ItemStack armorStandWand = WandType.ARMOR_STAND.createItem();

    assertNotNull(itemFrameWand);
    assertEquals(Material.WOODEN_SWORD, itemFrameWand.getType());
    assertTrue(
        itemFrameWand
            .getItemMeta()
            .getPersistentDataContainer()
            .has(WandType.ITEM_FRAME.getMainPersistentKey()));
    assertNotNull(armorStandWand);
    assertEquals(Material.WOODEN_HOE, armorStandWand.getType());
    assertTrue(
        armorStandWand
            .getItemMeta()
            .getPersistentDataContainer()
            .has(WandType.ARMOR_STAND.getMainPersistentKey()));
  }

  @Test
  void createItemIsNullAndWarnsWhenTheWandIsNotConfigured() {
    IceCreamFixture.install(server, "");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      assertNull(WandType.ARMOR_STAND.createItem());

      assertEquals(
          List.of("Wand material type not found for modules.wanderful.armor_stand."),
          log.messages(Level.WARNING));
    }
  }
}
