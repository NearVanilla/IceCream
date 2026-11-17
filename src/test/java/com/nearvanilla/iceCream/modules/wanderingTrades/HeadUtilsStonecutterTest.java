package com.nearvanilla.iceCream.modules.wanderingTrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HeadUtilsStonecutterTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WanderingTradesTestUtils.CONFIG);
    WanderingTradesTestUtils.reset();
  }

  @AfterEach
  void tearDown() {
    WanderingTradesTestUtils.reset();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private HeadTradeData trade(String name, String cost) {
    return WanderingTradesTestUtils.trade(name, cost, 8, 1);
  }

  private Recipe recipe(String id) {
    return Bukkit.getRecipe(new NamespacedKey(IceCream.instance, id));
  }

  @Test
  void registersARecipeForEachTrade() {
    HeadUtils.registerStonecutterRecipes(
        List.of(trade("\"Mini Chest\"", "minecraft:chest"), trade("\"Mini Furnace\"", "furnace")));

    assertNotNull(recipe("miniblock/mini_chest"));
    assertNotNull(recipe("miniblock/mini_furnace"));
  }

  @Test
  void theRecipeTurnsTheSecondaryBlockIntoTheHeads() {
    HeadTradeData data = WanderingTradesTestUtils.trade("\"Mini Chest\"", "minecraft:chest", 8, 1);

    HeadUtils.registerStonecutterRecipes(List.of(data));

    StonecuttingRecipe recipe =
        assertInstanceOf(StonecuttingRecipe.class, recipe("miniblock/mini_chest"));
    assertEquals(new RecipeChoice.MaterialChoice(Material.CHEST), recipe.getInputChoice());
    ItemStack result = recipe.getResult();
    assertEquals(Material.PLAYER_HEAD, result.getType());
    assertEquals(8, result.getAmount());
    assertEquals(
        "Mini Chest", MessageTestUtils.plainText(((SkullMeta) result.getItemMeta()).customName()));
    assertEquals(HeadUtils.createCustomHead(data.name, data.texture, data.headCount), result);
  }

  @Test
  void theRecipeResultMatchesWhatTheTraderSells() throws Exception {
    List<HeadTradeData> trades =
        HeadUtils.loadHeadTrades(
            WanderingTradesTestUtils.json(trade("\"Mini Chest\"", "minecraft:chest")));

    HeadUtils.registerStonecutterRecipes(trades);

    assertEquals(
        HeadUtils.getHeadTradePool().getFirst().getResult(),
        recipe("miniblock/mini_chest").getResult());
  }

  @Test
  void keysTheRecipeByTheNameWithoutQuotesInLowerCase() {
    HeadUtils.registerStonecutterRecipes(List.of(trade("\"MINI Chest\"", "minecraft:chest")));

    assertNotNull(recipe("miniblock/mini_chest"));
  }

  @Test
  void collapsesEveryRunOfOtherCharactersIntoOneUnderscore() {
    HeadUtils.registerStonecutterRecipes(List.of(trade("\"Mini  Chest -- 2\"", "minecraft:chest")));

    assertNotNull(recipe("miniblock/mini_chest_2"));
  }

  @Test
  void keepsATrailingUnderscoreWhenTheNameEndsWithPunctuation() {
    HeadUtils.registerStonecutterRecipes(
        List.of(trade("\"Mini Copper Bulb (Lit)\"", "minecraft:copper_bulb")));

    assertNotNull(recipe("miniblock/mini_copper_bulb_lit_"));
  }

  @Test
  void keysTheRecipeInThePluginNamespace() {
    HeadUtils.registerStonecutterRecipes(List.of(trade("\"Mini Chest\"", "minecraft:chest")));

    assertEquals(
        new NamespacedKey("icecream", "miniblock/mini_chest"),
        ((StonecuttingRecipe) recipe("miniblock/mini_chest")).getKey());
  }

  @Test
  void lowercasesTheNameTheSameWayInEveryLocale() {
    // MockBukkit loads its recipe data on first use and chokes on a Turkish locale, so touch the
    // recipes before switching.
    assertNull(recipe("warm_up"));
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try {
      HeadUtils.registerStonecutterRecipes(List.of(trade("\"MINI ICE\"", "minecraft:ice")));
    } finally {
      Locale.setDefault(original);
    }

    assertNotNull(recipe("miniblock/mini_ice"));
  }

  @Test
  void skipsATradeWithAnUnknownBlockAndWarns() {
    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      HeadUtils.registerStonecutterRecipes(
          List.of(
              trade("\"Mini Mystery\"", "minecraft:not_a_block"),
              trade("\"Mini Chest\"", "minecraft:chest")));

      assertEquals(
          List.of(
              "Skipping stonecutter recipe for \"Mini Mystery\": unknown block"
                  + " minecraft:not_a_block"),
          log.messages(Level.WARNING));
    }

    assertNull(recipe("miniblock/mini_mystery"));
    assertNotNull(recipe("miniblock/mini_chest"));
  }

  @Test
  void anEmptyListRegistersNothing() {
    HeadUtils.registerStonecutterRecipes(List.of());

    assertNull(recipe("miniblock/mini_chest"));
  }

  @Test
  void registeringAgainReplacesTheEarlierRecipes() {
    HeadUtils.registerStonecutterRecipes(List.of(trade("\"Mini Chest\"", "minecraft:chest")));

    HeadUtils.registerStonecutterRecipes(List.of(trade("\"Mini Furnace\"", "minecraft:furnace")));

    assertNull(recipe("miniblock/mini_chest"));
    assertNotNull(recipe("miniblock/mini_furnace"));
  }

  @Test
  void registeringTheSameTradesAgainKeepsThemRegistered() {
    List<HeadTradeData> trades = List.of(trade("\"Mini Chest\"", "minecraft:chest"));
    HeadUtils.registerStonecutterRecipes(trades);

    HeadUtils.registerStonecutterRecipes(trades);

    assertNotNull(recipe("miniblock/mini_chest"));
    HeadUtils.unregisterStonecutterRecipes();
    assertNull(recipe("miniblock/mini_chest"));
  }

  @Test
  void unregisterRemovesEveryRecipeTheModuleRegistered() {
    HeadUtils.registerStonecutterRecipes(
        List.of(trade("\"Mini Chest\"", "minecraft:chest"), trade("\"Mini Furnace\"", "furnace")));

    HeadUtils.unregisterStonecutterRecipes();

    assertNull(recipe("miniblock/mini_chest"));
    assertNull(recipe("miniblock/mini_furnace"));
  }

  @Test
  void unregisterLeavesOtherRecipesAlone() {
    NamespacedKey other = new NamespacedKey(IceCream.instance, "other_recipe");
    Bukkit.addRecipe(new StonecuttingRecipe(other, new ItemStack(Material.DIRT), Material.STONE));
    HeadUtils.registerStonecutterRecipes(List.of(trade("\"Mini Chest\"", "minecraft:chest")));

    HeadUtils.unregisterStonecutterRecipes();

    assertNotNull(Bukkit.getRecipe(other));
  }

  @Test
  void unregisterWithNothingRegisteredRemovesNothing() {
    NamespacedKey other = new NamespacedKey(IceCream.instance, "other_recipe");
    Bukkit.addRecipe(new StonecuttingRecipe(other, new ItemStack(Material.DIRT), Material.STONE));

    HeadUtils.unregisterStonecutterRecipes();
    HeadUtils.unregisterStonecutterRecipes();

    assertNotNull(Bukkit.getRecipe(other));
  }
}
