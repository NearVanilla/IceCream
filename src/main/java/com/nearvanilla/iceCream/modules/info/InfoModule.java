package com.nearvanilla.iceCream.modules.info;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.info.commands.InfoCommand;

/**
 * InfoModule adds a command that shows which build of the plugin is running, including its version,
 * build number and source commit.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-12
 * @see Module
 * @see InfoCommand
 * @see BuildInfo
 */
public class InfoModule implements Module {
  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.info.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new InfoCommand(BuildInfo.load()));
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
        IceCream.logger.severe("Failed to register Info module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Info module has been enabled.");
    } else {
      IceCream.logger.info("Info module is disabled.");
    }
  }
}
