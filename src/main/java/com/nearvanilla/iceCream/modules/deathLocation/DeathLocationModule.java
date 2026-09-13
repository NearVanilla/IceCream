package com.nearvanilla.iceCream.modules.deathLocation;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.deathLocation.events.DeathLocationListener;

/** Sends players their death coordinates so they can find their dropped items. */
public class DeathLocationModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.deathlocation.enabled", false);
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
        .registerEvents(new DeathLocationListener(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Death Location module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Death Location module has been enabled.");
    } else {
      IceCream.logger.info("Death Location module is disabled.");
    }
  }
}
