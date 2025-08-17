package com.nearvanilla.iceCream.modules.isSlimeChunk;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.isSlimeChunk.commands.isSlimeChunkCommand;

/**
 * ExampleModule is a demonstration of how to create a module in the IceCream plugin. It registers a
 * simple command and an event listener that welcomes players when they join the server.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-09
 * @see Module
 * @see isSlimeChunkCommand
 */
public class isSlimeChunkModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.isslimechunk.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new isSlimeChunkCommand());
  }

  @Override
  public void registerEvents() {
    // No events
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register isSlimeChunk module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("isSlimeChunk module has been enabled.");
    } else {
      IceCream.logger.info("isSlimeChunk module is disabled.");
    }
  }
}
