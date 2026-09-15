package com.nearvanilla.iceCream.modules.vineGrowth;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.vineGrowth.events.VineGrowthListener;

/**
 * VineGrowthModule lets players stop an individual vine from growing by using shears on it, and
 * start it growing again by shearing it a second time.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-09-15
 * @see Module
 * @see VineGrowthListener
 * @see VineGrowthUtils
 */
public class VineGrowthModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.vinegrowth.enabled", false);
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
        .registerEvents(new VineGrowthListener(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Vine Growth module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Vine Growth module has been enabled.");
    } else {
      IceCream.logger.info("Vine Growth module is disabled.");
    }
  }
}
