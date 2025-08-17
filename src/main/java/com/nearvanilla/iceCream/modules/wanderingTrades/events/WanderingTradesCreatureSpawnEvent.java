package com.nearvanilla.iceCream.modules.wanderingTrades.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.wanderingTrades.HeadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.MerchantRecipe;

/**
 * WanderingTradesCreatureSpawnEvent is an event listener that listens for "CreatureSpawnEvent" and
 * checks if it's a wandering trader. If the spawned entity is a wandering trader, it adds a random
 * number of head trades to the trader's trade pool.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-17
 */
public class WanderingTradesCreatureSpawnEvent implements Listener {

  /**
   * Handles the CreatureSpawnEvent. If the spawned entity is a WanderingTrader, it adds a random
   * number of head trades to the trader's trade pool.
   *
   * @param event The CreatureSpawnEvent that is triggered when a creature spawns.
   */
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (!(event.getEntity() instanceof WanderingTrader trader)) return;

    if (IceCream.config.getInt("modules.wanderingtrades.min-heads", 5)
        > IceCream.config.getInt("modules.wanderingtrades.max-heads", 10)) {
      IceCream.logger.warning(
          "The minimum number of heads is greater than the maximum number of heads. Please check your configuration.");
      return;
    }

    final int headTrades =
        ThreadLocalRandom.current()
            .nextInt(
                IceCream.config.getInt("modules.wanderingtrades.min-heads", 5),
                IceCream.config.getInt("modules.wanderingtrades.max-heads", 10) + 1);
    final List<MerchantRecipe> trades = new ArrayList<>(trader.getRecipes());
    List<MerchantRecipe> pool = HeadUtils.getHeadTradePool();

    if (pool.isEmpty()) {
      IceCream.logger.warning(
          "No head trades available in the pool. Please ensure you have loaded head trades.");
      return;
    }

    for (int i = 0; i < headTrades; i++) {
      int randomIndex = ThreadLocalRandom.current().nextInt(pool.size());
      MerchantRecipe randomHeadTrade = pool.get(randomIndex);
      trades.addFirst(randomHeadTrade);
    }
    trader.setRecipes(trades);
  }
}
