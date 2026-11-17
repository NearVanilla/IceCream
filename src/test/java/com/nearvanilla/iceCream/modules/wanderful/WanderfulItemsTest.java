package com.nearvanilla.iceCream.modules.wanderful;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WanderfulTestUtils;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

// The tests below read the persistent keys of WandType, which builds them from IceCream.instance
// when the class loads, so the fixture is installed before any test touches the enum.
class WanderfulItemsTest {
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

  private String serialized(Component component) {
    return MiniMessage.miniMessage().serialize(component);
  }

  @Test
  void usesTheConfiguredMaterial() {
    ItemStack itemFrameWand = WanderfulItems.createWand(WandType.ITEM_FRAME);
    ItemStack armorStandWand = WanderfulItems.createWand(WandType.ARMOR_STAND);

    assertNotNull(itemFrameWand);
    assertEquals(Material.WOODEN_SWORD, itemFrameWand.getType());
    assertEquals(1, itemFrameWand.getAmount());
    assertNotNull(armorStandWand);
    assertEquals(Material.WOODEN_HOE, armorStandWand.getType());
    assertEquals(1, armorStandWand.getAmount());
  }

  @Test
  void usesTheConfiguredDisplayName() {
    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    Component name = meta.displayName();

    assertNotNull(name);
    assertEquals("<gold>Item Frame Wand", serialized(name));
  }

  @Test
  void usesTheDisplayNameOfTheWandThatWasAskedFor() {
    ItemMeta meta = WanderfulItems.createWand(WandType.ARMOR_STAND).getItemMeta();

    assertEquals("<gold>Armor Stand Wand", serialized(meta.displayName()));
  }

  @Test
  void usesTheConfiguredLoreInOrder() {
    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    List<Component> lore = meta.lore();

    assertNotNull(lore);
    assertEquals(
        List.of("<dark_green>Now you see me,", "<dark_red>now you don't"),
        lore.stream().map(this::serialized).toList());
  }

  @Test
  void usesASingleLoreLine() {
    ItemMeta meta = WanderfulItems.createWand(WandType.ARMOR_STAND).getItemMeta();

    assertEquals(
        List.of("Controlling the moves!"), meta.lore().stream().map(this::serialized).toList());
  }

  @Test
  void hasNoDisplayNameWhenNoneIsConfigured() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
        """);

    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    assertFalse(meta.hasDisplayName());
  }

  @Test
  void hasNoLoreWhenNoneIsConfigured() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
        """);

    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    assertFalse(meta.hasLore());
  }

  @Test
  void hasNoLoreWhenTheLoreListIsEmpty() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
              lore: []
        """);

    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    assertFalse(meta.hasLore());
  }

  @Test
  void hidesAttributesAndEnchantments() {
    ItemMeta meta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();

    assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
    assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
  }

  @Test
  void marksTheWandWithItsOwnPersistentKey() {
    ItemMeta itemFrameMeta = WanderfulItems.createWand(WandType.ITEM_FRAME).getItemMeta();
    ItemMeta armorStandMeta = WanderfulItems.createWand(WandType.ARMOR_STAND).getItemMeta();

    assertEquals(
        (byte) 1,
        itemFrameMeta
            .getPersistentDataContainer()
            .get(WandType.ITEM_FRAME.getMainPersistentKey(), PersistentDataType.BYTE));
    assertFalse(
        itemFrameMeta
            .getPersistentDataContainer()
            .has(WandType.ARMOR_STAND.getMainPersistentKey()));
    assertEquals(
        (byte) 1,
        armorStandMeta
            .getPersistentDataContainer()
            .get(WandType.ARMOR_STAND.getMainPersistentKey(), PersistentDataType.BYTE));
    assertFalse(
        armorStandMeta
            .getPersistentDataContainer()
            .has(WandType.ITEM_FRAME.getMainPersistentKey()));
  }

  @Test
  void eachCallBuildsAnIndependentStack() {
    ItemStack first = WanderfulItems.createWand(WandType.ITEM_FRAME);
    ItemStack second = WanderfulItems.createWand(WandType.ITEM_FRAME);

    assertNotSame(first, second);
    assertEquals(first, second);
    first.setAmount(5);
    assertEquals(1, second.getAmount());
  }

  @Test
  void returnsNullAndWarnsWhenTheMaterialIsMissing() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              name: Nameless
        """);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      assertNull(WanderfulItems.createWand(WandType.ITEM_FRAME));

      assertEquals(
          List.of("Wand material type not found for modules.wanderful.item_frame."),
          log.messages(Level.WARNING));
    }
  }

  private RecipeChoice choice(Material material) {
    return new RecipeChoice.MaterialChoice(material);
  }

  private ShapedRecipe recipe(String id) {
    Recipe recipe = Bukkit.getRecipe(new NamespacedKey(IceCream.instance, id));
    return recipe == null ? null : assertInstanceOf(ShapedRecipe.class, recipe);
  }

  @Test
  void addRecipesRegistersTheItemFrameWandRecipe() {
    WanderfulItems.addRecipes();

    ShapedRecipe recipe = recipe("item_frame_recipe");

    assertNotNull(recipe);
    assertEquals(WandType.ITEM_FRAME.createItem(), recipe.getResult());
    assertArrayEquals(new String[] {"D", "I", "S"}, recipe.getShape());
    assertEquals(
        Map.of(
            'D', choice(Material.DIAMOND),
            'I', choice(Material.ITEM_FRAME),
            'S', choice(Material.STICK)),
        recipe.getChoiceMap());
  }

  @Test
  void addRecipesRegistersTheArmorStandWandRecipe() {
    WanderfulItems.addRecipes();

    ShapedRecipe recipe = recipe("armor_stand_recipe");

    assertNotNull(recipe);
    assertEquals(WandType.ARMOR_STAND.createItem(), recipe.getResult());
    assertArrayEquals(new String[] {"D", "A", "H"}, recipe.getShape());
    assertEquals(
        Map.of(
            'D', choice(Material.DIAMOND),
            'A', choice(Material.ARMOR_STAND),
            'H', choice(Material.STICK)),
        recipe.getChoiceMap());
  }

  @Test
  void addRecipesKeysTheRecipesByWandInThePluginNamespace() {
    WanderfulItems.addRecipes();

    assertEquals(
        new NamespacedKey("icecream", "item_frame_recipe"), recipe("item_frame_recipe").getKey());
    assertEquals(
        new NamespacedKey("icecream", "armor_stand_recipe"), recipe("armor_stand_recipe").getKey());
  }

  @Test
  void addRecipesIgnoresAnIngredientThatIsNotInTheShape() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
              crafting:
                shape:
                  - D
                  - S
                ingredients:
                  D: DIAMOND
                  S: STICK
                  X: STONE
        """);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      WanderfulItems.addRecipes();

      // The armor stand wand is not configured here, and creating it warns about that.
      assertEquals(1, log.messages(Level.WARNING).size());
    }

    ShapedRecipe recipe = recipe("item_frame_recipe");
    assertNotNull(recipe);
    assertEquals(
        Map.of('D', choice(Material.DIAMOND), 'S', choice(Material.STICK)), recipe.getChoiceMap());
  }

  @Test
  void addRecipesSkipsAWandThatIsNotConfiguredAndWarnsAboutIt() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
              crafting:
                shape:
                  - D
                  - S
                ingredients:
                  D: DIAMOND
                  S: STICK
        """);

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      WanderfulItems.addRecipes();

      assertEquals(
          List.of("Wand material type not found for modules.wanderful.armor_stand."),
          log.messages(Level.WARNING));
    }
    assertNotNull(recipe("item_frame_recipe"));
    assertNull(recipe("armor_stand_recipe"));
  }

  @Test
  void addRecipesRejectsAnIngredientThatIsNotAMaterial() {
    IceCreamFixture.install(
        server,
        """
        modules:
          wanderful:
            item_frame:
              type: WOODEN_SWORD
              crafting:
                shape:
                  - D
                ingredients:
                  D: NOT_A_MATERIAL
        """);

    assertThrows(IllegalArgumentException.class, WanderfulItems::addRecipes);
  }
}
