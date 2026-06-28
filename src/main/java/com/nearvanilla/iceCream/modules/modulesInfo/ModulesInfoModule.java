package com.nearvanilla.iceCream.modules.modulesInfo;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.modulesInfo.commands.ModulesInfoCommand;

/**
 * ModulesInfoModule registers the {@code /modules} command that displays all modules and their
 * enabled/disabled status.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-06-28
 * @see Module
 * @see ModulesInfoCommand
 */
public class ModulesInfoModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.modulesinfo.enabled", true);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new ModulesInfoCommand());
  }

  @Override
  public void registerEvents() {
    // No events to register for the ModulesInfo module
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register ModulesInfo module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("ModulesInfo module has been enabled.");
    } else {
      IceCream.logger.info("ModulesInfo module is disabled.");
    }
  }
}