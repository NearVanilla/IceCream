package com.nearvanilla.iceCream.modules.wanderful;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.wanderful.commands.WanderfulCommand;
import com.nearvanilla.iceCream.modules.wanderful.events.WanderfulEntityDamageEvent;
import com.nearvanilla.iceCream.modules.wanderful.events.WanderfulPlayerInteractEntityEvent;

/**
 * WanderfulModule adds wands that allow players to interact with item frames and armor stands (via
 * the ArmorStandEditor Plugin)
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 * @see Module
 * @see WanderfulCommand
 * @see WanderfulEntityDamageEvent
 * @see WanderfulPlayerInteractEntityEvent
 */
public class WanderfulModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.wanderful.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new WanderfulCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new WanderfulEntityDamageEvent(), IceCream.instance);

    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new WanderfulPlayerInteractEntityEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        ArmorStandEditorWrapper.configure();

        // Add Recipes
        WanderfulItems.addRecipes();

        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Wanderful module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Wanderful module has been enabled.");
    } else {
      IceCream.logger.info("Wanderful module is disabled.");
    }
  }
}
