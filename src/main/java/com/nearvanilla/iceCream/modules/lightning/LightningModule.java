package com.nearvanilla.iceCream.modules.lightning;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.lightning.commands.StrikeCommand;

/**
 * LightningModule adds a command to strike lightning at a player's location. However, there is a
 * 50% chance that the command will strike the player running the command instead of the target
 * player.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-11
 * @see Module
 * @see StrikeCommand
 */
public class LightningModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.lightning.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new StrikeCommand());
  }

  @Override
  public void registerEvents() {
    // No events to register for the Lightning module
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Lightning module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Lightning module has been enabled.");
    } else {
      IceCream.logger.info("Lightning module is disabled.");
    }
  }
}
