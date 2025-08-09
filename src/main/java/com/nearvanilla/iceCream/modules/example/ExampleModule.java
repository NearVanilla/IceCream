package com.nearvanilla.iceCream.modules.example;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.example.commands.ExampleCommand;
import com.nearvanilla.iceCream.modules.example.events.ExampleEvent;

/**
 * ExampleModule is a demonstration of how to create a module in the IceCream plugin.
 * It registers a simple command and an event listener that welcomes players when they join the server.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-09
 */
public class ExampleModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.example.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new ExampleCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance.getServer().getPluginManager().registerEvents(new ExampleEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try{
        registerCommands();
        registerEvents();
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Example module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Example module has been enabled.");
    } else {
      IceCream.logger.info("Example module is disabled.");
    }
  }
}
