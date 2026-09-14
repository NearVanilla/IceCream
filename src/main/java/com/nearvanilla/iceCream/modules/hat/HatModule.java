package com.nearvanilla.iceCream.modules.hat;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.hat.commands.HatCommand;
import com.nearvanilla.iceCream.modules.hat.events.HatInventoryListener;

public final class HatModule implements Module {
  private static final String CONFIG_PREFIX = "modules.hat.";

  private boolean enabled;
  private HatCommand command;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean(CONFIG_PREFIX + "enabled", true);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(command);
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new HatInventoryListener(), IceCream.instance);
  }

  @Override
  public void register() {
    if (!shouldEnable()) {
      IceCream.logger.info("Hat module is disabled.");
      return;
    }

    command = new HatCommand();
    registerCommands();
    registerEvents();
    enabled = true;
    IceCream.logger.info("Hat module has been enabled.");
  }
}
