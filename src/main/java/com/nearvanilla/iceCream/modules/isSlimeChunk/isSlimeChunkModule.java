package com.nearvanilla.iceCream.modules.isSlimeChunk;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.isSlimeChunk.commands.isSlimeChunkCommand;

/**
 * isSlimeChunkModule adds a command to check if the chunk the player is currently standing in is a
 * slime chunk or not.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-17
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
