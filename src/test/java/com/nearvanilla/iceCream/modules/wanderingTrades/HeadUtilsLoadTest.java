package com.nearvanilla.iceCream.modules.wanderingTrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HeadUtilsLoadTest {
  private static final String BUNDLED_TRADES = "data/wandering_trades.json";

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

  private List<HeadTradeData> load(HeadTradeData... trades) throws Exception {
    return HeadUtils.loadHeadTrades(WanderingTradesTestUtils.json(trades));
  }

  private HeadTradeData trade(String name, String cost) {
    return WanderingTradesTestUtils.trade(name, cost, 8, 1);
  }

  @Test
  void returnsTheParsedTradesInOrder() throws Exception {
    List<HeadTradeData> trades =
        load(
            WanderingTradesTestUtils.trade("\"Mini Chest\"", "minecraft:chest", 8, 1),
            WanderingTradesTestUtils.trade("\"Mini Furnace\"", "minecraft:furnace", 4, 3));

    assertEquals(2, trades.size());
    assertEquals("\"Mini Chest\"", trades.get(0).name);
    assertEquals("minecraft:chest", trades.get(0).secondaryCost);
    assertEquals(8, trades.get(0).headCount);
    assertEquals(1, trades.get(0).maxUses);
    assertEquals(WanderingTradesTestUtils.texture("\"Mini Chest\""), trades.get(0).texture);
    assertEquals("\"Mini Furnace\"", trades.get(1).name);
    assertEquals(4, trades.get(1).headCount);
    assertEquals(3, trades.get(1).maxUses);
  }

  @Test
  void addsOneRecipeToThePoolPerTrade() throws Exception {
    load(
        trade("\"Mini Chest\"", "minecraft:chest"), trade("\"Mini Furnace\"", "minecraft:furnace"));

    assertEquals(2, HeadUtils.getHeadTradePool().size());
  }

  @Test
  void theRecipeSellsTheHeadsForAnEmeraldAndTheSecondaryBlock() throws Exception {
    load(WanderingTradesTestUtils.trade("\"Mini Chest\"", "minecraft:chest", 8, 1));

    MerchantRecipe recipe = HeadUtils.getHeadTradePool().getFirst();

    assertEquals(
        List.of(new ItemStack(Material.EMERALD, 1), new ItemStack(Material.CHEST, 1)),
        recipe.getIngredients());
  }

  @Test
  void theRecipeResultIsTheCustomHead() throws Exception {
    load(WanderingTradesTestUtils.trade("\"Mini Chest\"", "minecraft:chest", 8, 1));

    ItemStack result = HeadUtils.getHeadTradePool().getFirst().getResult();

    assertEquals(Material.PLAYER_HEAD, result.getType());
    assertEquals(8, result.getAmount());
    assertEquals(
        "Mini Chest", MessageTestUtils.plainText(((SkullMeta) result.getItemMeta()).customName()));
    assertEquals(
        HeadUtils.createCustomHead(
            "\"Mini Chest\"", WanderingTradesTestUtils.texture("\"Mini Chest\""), 8),
        result);
  }

  @Test
  void theRecipeCanBeUsedTheConfiguredNumberOfTimes() throws Exception {
    load(WanderingTradesTestUtils.trade("\"Mini Chest\"", "minecraft:chest", 8, 5));

    MerchantRecipe recipe = HeadUtils.getHeadTradePool().getFirst();

    assertEquals(5, recipe.getMaxUses());
    assertEquals(0, recipe.getUses());
  }

  @Test
  void acceptsTheBlockNameWithoutANamespace() throws Exception {
    load(trade("\"Mini Chest\"", "chest"));

    assertEquals(
        new ItemStack(Material.CHEST, 1),
        HeadUtils.getHeadTradePool().getFirst().getIngredients().get(1));
  }

  @Test
  void acceptsTheBlockNameInUpperCase() throws Exception {
    load(trade("\"Mini Chest\"", "CHEST"));

    assertEquals(
        new ItemStack(Material.CHEST, 1),
        HeadUtils.getHeadTradePool().getFirst().getIngredients().get(1));
  }

  @Test
  void asksOnlyForAnEmeraldWhenTheSecondaryBlockIsUnknown() throws Exception {
    List<HeadTradeData> trades = load(trade("\"Mini Mystery\"", "minecraft:not_a_block"));

    assertEquals(1, trades.size());
    assertEquals(
        List.of(new ItemStack(Material.EMERALD, 1)),
        HeadUtils.getHeadTradePool().getFirst().getIngredients());
  }

  @Test
  void anEmptyListLoadsNoTrades() throws Exception {
    assertEquals(List.of(), HeadUtils.loadHeadTrades(WanderingTradesTestUtils.stream("[]")));

    assertTrue(HeadUtils.getHeadTradePool().isEmpty());
  }

  @Test
  void loadingAgainReplacesTheEarlierTrades() throws Exception {
    load(
        trade("\"Mini Chest\"", "minecraft:chest"), trade("\"Mini Furnace\"", "minecraft:furnace"));

    load(trade("\"Mini Anvil\"", "minecraft:anvil"));

    List<MerchantRecipe> pool = HeadUtils.getHeadTradePool();
    assertEquals(1, pool.size());
    assertEquals(
        "Mini Anvil",
        MessageTestUtils.plainText(
            ((SkullMeta) pool.getFirst().getResult().getItemMeta()).customName()));
  }

  @Test
  void loadingAnEmptyListClearsTheEarlierTrades() throws Exception {
    load(trade("\"Mini Chest\"", "minecraft:chest"));

    HeadUtils.loadHeadTrades(WanderingTradesTestUtils.stream("[]"));

    assertTrue(HeadUtils.getHeadTradePool().isEmpty());
  }

  @Test
  void malformedJsonThrowsAndKeepsTheEarlierTrades() throws Exception {
    load(trade("\"Mini Chest\"", "minecraft:chest"));

    assertThrows(
        JacksonException.class,
        () -> HeadUtils.loadHeadTrades(WanderingTradesTestUtils.stream("[{\"name\":")));

    assertEquals(1, HeadUtils.getHeadTradePool().size());
  }

  @Test
  void jsonThatIsNotAListThrowsAndKeepsTheEarlierTrades() throws Exception {
    load(trade("\"Mini Chest\"", "minecraft:chest"));

    assertThrows(
        JacksonException.class,
        () -> HeadUtils.loadHeadTrades(WanderingTradesTestUtils.stream("{\"name\":\"x\"}")));

    assertEquals(1, HeadUtils.getHeadTradePool().size());
  }

  @Test
  void emptyInputThrows() {
    assertThrows(
        JacksonException.class,
        () -> HeadUtils.loadHeadTrades(WanderingTradesTestUtils.stream("")));
  }

  // The bundled data file

  private List<HeadTradeData> loadBundled() throws Exception {
    try (InputStream in = IceCream.class.getClassLoader().getResourceAsStream(BUNDLED_TRADES)) {
      assertNotNull(in, BUNDLED_TRADES);
      return HeadUtils.loadHeadTrades(in);
    }
  }

  @Test
  void theBundledDataFileLoadsEveryTradeIntoThePool() throws Exception {
    List<HeadTradeData> trades = loadBundled();

    assertFalse(trades.isEmpty());
    assertEquals(trades.size(), HeadUtils.getHeadTradePool().size());
  }

  @Test
  void everyBundledTradeAsksForAKnownBlockAndAnEmerald() throws Exception {
    List<HeadTradeData> trades = loadBundled();

    for (int i = 0; i < trades.size(); i++) {
      HeadTradeData data = trades.get(i);
      assertNotNull(
          Material.matchMaterial(data.secondaryCost), data.name + ": " + data.secondaryCost);
      List<ItemStack> ingredients = HeadUtils.getHeadTradePool().get(i).getIngredients();
      assertEquals(2, ingredients.size(), data.name);
      assertEquals(Material.EMERALD, ingredients.get(0).getType(), data.name);
    }
  }

  @Test
  void everyBundledTradeHasPositiveCountsAndAName() throws Exception {
    for (HeadTradeData data : loadBundled()) {
      assertNotNull(data.name);
      assertFalse(data.name.isBlank(), "a trade has no name");
      assertTrue(data.headCount > 0, data.name + " headCount " + data.headCount);
      assertTrue(data.headCount <= 64, data.name + " headCount " + data.headCount);
      assertTrue(data.maxUses > 0, data.name + " maxUses " + data.maxUses);
    }
  }

  @Test
  void everyBundledTextureDecodesToASkinUrl() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    for (HeadTradeData data : loadBundled()) {
      assertNotNull(data.texture, data.name);
      byte[] decoded = Base64.getDecoder().decode(data.texture);
      JsonNode url = mapper.readTree(decoded).path("textures").path("SKIN").path("url");
      assertTrue(url.isString(), data.name + " has no skin url");
      assertTrue(url.asString().startsWith("http"), data.name + ": " + url.asString());
    }
  }

  @Test
  void everyBundledTradeNameIsUniqueSoNoTwoTradesCollide() throws Exception {
    Set<String> seen = new HashSet<>();
    for (HeadTradeData data : loadBundled()) {
      String name = data.name.replace("\"", "").toLowerCase(Locale.ROOT);
      assertTrue(seen.add(name), "duplicate trade name " + data.name);
    }
  }
}
