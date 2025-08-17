package com.nearvanilla.iceCream.modules.wanderingTrades;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.wanderingTrades.events.WanderingTradesCreatureSpawnEvent;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.MerchantRecipe;

/**
 * WanderingTradesModule is a module that adds "Mini Blocks" to wandering traders upon spawning. It
 * registers the event "WanderingTradesCreatureSpawnEvent" to handle the spawning of wandering
 * traders and populates their trades with custom heads.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-17
 * @see WanderingTradesModule
 * @see WanderingTradesCreatureSpawnEvent
 * @see HeadUtils
 * @see HeadTradeData
 */
public class WanderingTradesModule implements Module {
  protected boolean isEnabled = false;
  public static final List<MerchantRecipe> headTradePool = new ArrayList<>();

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.wanderingtrades.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    // No commands to register for this module.
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new WanderingTradesCreatureSpawnEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();

        // Load head trades from the JSON file in the JAR.
        try (InputStream in = IceCream.instance.getResource("data/wandering_trades.json")) {
          if (in == null)
            throw new FileNotFoundException("Failed to copy Wandering Trades config from JAR.");
          HeadUtils.loadHeadTrades(in);
        }

        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Wandering Trades module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Wandering Trades module has been enabled.");
    } else {
      IceCream.logger.info("Wandering Trades module is disabled.");
    }
  }
}
