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
 * ExampleModule is a demonstration of how to create a module in the IceCream plugin. It registers a
 * simple command and an event listener that welcomes players when they join the server.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-09
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
        try (InputStream in = IceCream.instance.getResource("data/wandering_trades.json")) {
          if (in == null)
            throw new FileNotFoundException(
                "Could not find data/wandering_trades.json in the jar!");
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
