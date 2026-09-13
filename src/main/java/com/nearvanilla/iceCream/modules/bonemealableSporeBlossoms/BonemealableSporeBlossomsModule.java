package com.nearvanilla.iceCream.modules.bonemealableSporeBlossoms;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.bonemealableSporeBlossoms.events.SporeBlossomBonemealEvent;

/**
 * BonemealableSporeBlossomsModule enables spore blossoms to be duplicated with bone meal, matching
 * the behavior of two-block-tall flowers.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-09-12
 * @see Module
 * @see SporeBlossomBonemealEvent
 */
public class BonemealableSporeBlossomsModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.bonemealablesporeblossoms.enabled", false);
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
        .registerEvents(new SporeBlossomBonemealEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe(
            "Failed to register Bonemealable Spore Blossoms module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Bonemealable Spore Blossoms module has been enabled.");
    } else {
      IceCream.logger.info("Bonemealable Spore Blossoms module is disabled.");
    }
  }
}
