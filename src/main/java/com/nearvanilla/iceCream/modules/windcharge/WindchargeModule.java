package com.nearvanilla.iceCream.modules.windcharge;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.windcharge.events.WindchargeExplodeEvent;

public class WindchargeModule implements Module {

  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.windcharge.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {}

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new WindchargeExplodeEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Windcharge module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Windcharge module has been enabled.");
    } else {
      IceCream.logger.info("Windcharge module is disabled.");
    }
  }
}
