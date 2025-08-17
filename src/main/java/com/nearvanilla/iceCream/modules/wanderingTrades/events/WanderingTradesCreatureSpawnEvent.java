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

public class WanderingTradesCreatureSpawnEvent implements Listener {

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
