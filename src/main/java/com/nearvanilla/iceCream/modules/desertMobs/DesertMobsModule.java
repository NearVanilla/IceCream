package com.nearvanilla.iceCream.modules.desertMobs;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.desertMobs.events.DesertMobsSpawnEvent;

/**
 * DesertMobsModule adds custom behavior for desert mobs. It adds a configurable chance for zombies
 * and skeletons spawning in desert biomes to convert to Husks and Parched respectively.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-02-26
 * @see Module
 * @see DesertMobsSpawnEvent
 */
public class DesertMobsModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.desertmobs.enabled", false);
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
        .registerEvents(new DesertMobsSpawnEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Desert Mobs module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Desert Mobs module has been enabled.");
    } else {
      IceCream.logger.info("Desert Mobs module is disabled.");
    }
  }
}
