package com.nearvanilla.iceCream.modules.playerHeadDrops;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.playerHeadDrops.events.PlayerDeathHeadDropEvent;

/**
 * PlayerHeadDropsModule adds a configurable chance of a player dropping their own head on death,
 * with an optional requirement that the killer be another player.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-06-12
 * @see Module
 * @see PlayerDeathHeadDropEvent
 */
public class PlayerHeadDropsModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.playerheaddrops.enabled", false);
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
        .registerEvents(new PlayerDeathHeadDropEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Player Head Drops module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Player Head Drops module has been enabled.");
    } else {
      IceCream.logger.info("Player Head Drops module is disabled.");
    }
  }
}
