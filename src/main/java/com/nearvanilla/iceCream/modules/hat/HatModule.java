package com.nearvanilla.iceCream.modules.hat;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.hat.commands.HatCommand;
import com.nearvanilla.iceCream.modules.hat.events.HatInventoryListener;

/**
 * Enables players to wear held items as hats through a command or the helmet inventory slot.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-09-14
 * @see Module
 * @see HatCommand
 * @see HatInventoryListener
 */
public class HatModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.hat.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new HatCommand());
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
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception exception) {
        IceCream.logger.severe("Failed to register Hat module: " + exception.getMessage());
        return;
      }
      IceCream.logger.info("Hat module has been enabled.");
    } else {
      IceCream.logger.info("Hat module is disabled.");
    }
  }
}
