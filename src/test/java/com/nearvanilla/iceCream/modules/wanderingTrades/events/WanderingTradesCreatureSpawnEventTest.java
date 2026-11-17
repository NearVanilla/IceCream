package com.nearvanilla.iceCream.modules.wanderingTrades.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.wanderingTrades.WanderingTradesModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class WanderingTradesCreatureSpawnEventTest {
  // Enough spawns that a range of at most six values shows every value, with a chance of missing
  // one that is below one in 10^20.
  private static final int SAMPLES = 300;

  private final WanderingTradesCreatureSpawnEvent listener =
      new WanderingTradesCreatureSpawnEvent();

  private ServerMock server;
  private World world;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    WanderingTradesTestUtils.reset();
    world = server.addSimpleWorld("world");
  }

  @AfterEach
  void tearDown() {
    WanderingTradesTestUtils.reset();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String minHeads, String maxHeads) {
    StringBuilder yaml = new StringBuilder("modules:\n  wanderingtrades:\n    enabled: true\n");
    if (minHeads != null) {
      yaml.append("    min-heads: ").append(minHeads).append('\n');
    }
    if (maxHeads != null) {
      yaml.append("    max-heads: ").append(maxHeads).append('\n');
    }
    IceCreamFixture.install(server, yaml.toString());
  }

  // Fills the pool with head trades whose results hold 1, 2, 3... heads, so a trade can be told
  // apart from the others by the amount of its result.
  private void fillPool(int size) {
    for (int amount = 1; amount <= size; amount++) {
      WanderingTradesModule.headTradePool.add(
          new MerchantRecipe(new ItemStack(Material.PLAYER_HEAD, amount), 1));
    }
  }

  // A trader with two trades of its own. The listener is not registered with the server, so
  // spawning it does not add heads until the test hands the spawn to the listener.
  private WanderingTrader trader() {
    WanderingTrader trader = world.spawn(new Location(world, 0, 64, 0), WanderingTrader.class);
    trader.setRecipes(
        List.of(
            new MerchantRecipe(new ItemStack(Material.DIAMOND, 1), 1),
            new MerchantRecipe(new ItemStack(Material.DIAMOND, 2), 1)));
    return trader;
  }

  private void spawn(AbstractVillager entity) {
    listener.onCreatureSpawn(new CreatureSpawnEvent(entity, SpawnReason.NATURAL));
  }

  // The amounts of the head trades a trader offers, in the order it offers them.
  private List<Integer> headTrades(AbstractVillager trader) {
    return trader.getRecipes().stream()
        .map(MerchantRecipe::getResult)
        .filter(result -> result.getType() == Material.PLAYER_HEAD)
        .map(ItemStack::getAmount)
        .toList();
  }

  private List<Integer> ownTrades(AbstractVillager trader) {
    return trader.getRecipes().stream()
        .map(MerchantRecipe::getResult)
        .filter(result -> result.getType() == Material.DIAMOND)
        .map(ItemStack::getAmount)
        .toList();
  }

  // Spawns traders until every possible head count has had a chance to show, and returns the
  // counts that were seen.
  private Set<Integer> headCountsSeen() {
    Set<Integer> counts = new HashSet<>();
    for (int i = 0; i < SAMPLES; i++) {
      WanderingTrader trader = trader();
      spawn(trader);
      counts.add(headTrades(trader).size());
    }
    return counts;
  }

  @Test
  void addsTheConfiguredNumberOfHeadTrades() {
    config("3", "3");
    fillPool(10);
    WanderingTrader trader = trader();

    spawn(trader);

    assertEquals(3, headTrades(trader).size());
  }

  @Test
  void putsTheHeadTradesBeforeTheTradersOwnTrades() {
    config("3", "3");
    fillPool(10);
    WanderingTrader trader = trader();

    spawn(trader);

    List<MerchantRecipe> recipes = trader.getRecipes();
    assertEquals(5, recipes.size());
    for (int i = 0; i < 3; i++) {
      assertEquals(Material.PLAYER_HEAD, recipes.get(i).getResult().getType());
    }
    assertEquals(Material.DIAMOND, recipes.get(3).getResult().getType());
    assertEquals(1, recipes.get(3).getResult().getAmount());
    assertEquals(Material.DIAMOND, recipes.get(4).getResult().getType());
    assertEquals(2, recipes.get(4).getResult().getAmount());
  }

  @Test
  void keepsTheTradersOwnTrades() {
    config("2", "2");
    fillPool(10);
    WanderingTrader trader = trader();

    spawn(trader);

    assertEquals(List.of(1, 2), ownTrades(trader));
  }

  @Test
  void offersEachHeadTradeAtMostOnce() {
    config("10", "10");
    fillPool(10);
    WanderingTrader trader = trader();

    spawn(trader);

    List<Integer> heads = headTrades(trader);
    assertEquals(10, heads.size());
    assertEquals(10, new HashSet<>(heads).size());
  }

  @Test
  void offersOnlyTradesFromThePool() {
    config("4", "4");
    fillPool(6);
    WanderingTrader trader = trader();

    spawn(trader);

    assertTrue(
        Set.of(1, 2, 3, 4, 5, 6).containsAll(headTrades(trader)), headTrades(trader).toString());
  }

  @Test
  void offersTheWholePoolWhenItIsSmallerThanTheNumberToAdd() {
    config("5", "5");
    fillPool(2);
    WanderingTrader trader = trader();

    spawn(trader);

    assertEquals(Set.of(1, 2), new HashSet<>(headTrades(trader)));
    assertEquals(2, headTrades(trader).size());
  }

  @Test
  void picksDifferentHeadTradesOnDifferentSpawns() {
    config("3", "3");
    fillPool(20);
    Set<Integer> offered = new HashSet<>();

    for (int i = 0; i < 200; i++) {
      WanderingTrader trader = trader();
      spawn(trader);
      offered.addAll(headTrades(trader));
    }

    assertEquals(20, offered.size());
  }

  @Test
  void leavesThePoolUntouched() {
    config("4", "4");
    fillPool(6);
    List<MerchantRecipe> before = new ArrayList<>(WanderingTradesModule.headTradePool);

    spawn(trader());
    spawn(trader());

    assertEquals(before, WanderingTradesModule.headTradePool);
  }

  @Test
  void addsNoHeadsWhenTheMaximumIsZero() {
    config("0", "0");
    fillPool(6);
    WanderingTrader trader = trader();

    spawn(trader);

    assertEquals(List.of(), headTrades(trader));
    assertEquals(List.of(1, 2), ownTrades(trader));
  }

  @Test
  void theNumberOfHeadTradesIsBetweenTheMinimumAndTheMaximumInclusive() {
    config("2", "4");
    fillPool(10);

    assertEquals(Set.of(2, 3, 4), headCountsSeen());
  }

  @Test
  void defaultsToBetweenFiveAndTenHeadTrades() {
    config(null, null);
    fillPool(20);

    assertEquals(Set.of(5, 6, 7, 8, 9, 10), headCountsSeen());
  }

  @Test
  void aConfiguredMinimumKeepsTheDefaultMaximumOfTen() {
    config("8", null);
    fillPool(20);

    assertEquals(Set.of(8, 9, 10), headCountsSeen());
  }

  @Test
  void aConfiguredMaximumKeepsTheDefaultMinimumOfFive() {
    config(null, "6");
    fillPool(20);

    assertEquals(Set.of(5, 6), headCountsSeen());
  }

  @Test
  void warnsAndChangesNothingWhenTheMinimumIsAboveTheMaximum() {
    config("6", "3");
    fillPool(10);
    WanderingTrader trader = trader();

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      spawn(trader);

      assertEquals(
          List.of(
              "The minimum number of heads is greater than the maximum number of heads. Please"
                  + " check your configuration."),
          log.messages(Level.WARNING));
    }

    assertEquals(List.of(), headTrades(trader));
    assertEquals(List.of(1, 2), ownTrades(trader));
  }

  @Test
  void warnsAndChangesNothingWhenThePoolIsEmpty() {
    config("3", "3");
    WanderingTrader trader = trader();

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      spawn(trader);

      assertEquals(
          List.of(
              "No head trades available in the pool. Please ensure you have loaded head"
                  + " trades."),
          log.messages(Level.WARNING));
    }

    assertEquals(List.of(), headTrades(trader));
    assertEquals(List.of(1, 2), ownTrades(trader));
  }

  @Test
  void ignoresACreatureThatIsNotAWanderingTrader() {
    config("3", "3");
    fillPool(10);
    Villager villager = world.spawn(new Location(world, 0, 64, 0), Villager.class);
    List<MerchantRecipe> before = villager.getRecipes();

    spawn(villager);

    assertEquals(before, villager.getRecipes());
    assertEquals(List.of(), headTrades(villager));
  }

  @Test
  void eachTraderIsHandledOnItsOwn() {
    config("3", "3");
    fillPool(10);
    WanderingTrader first = trader();
    WanderingTrader second = trader();

    spawn(first);

    assertEquals(3, headTrades(first).size());
    assertEquals(List.of(), headTrades(second));
  }
}
