package com.nearvanilla.iceCream.modules.playerSeen;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.playerSeen.commands.SeenCommand;

/**
 * PlayerSeenModule adds a command that reports when a player was last seen on the server. Online
 * players are reported as online, while offline players are reported with a colour-coded, human
 * readable duration and a hover showing the exact date and time.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-25
 * @see Module
 * @see PlayerSeenUtils
 * @see SeenCommand
 */
public class PlayerSeenModule implements Module {

  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.playerseen.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new SeenCommand());
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
        IceCream.logger.severe("Failed to register PlayerSeen module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("PlayerSeen module has been enabled.");
    } else {
      IceCream.logger.info("PlayerSeen module is disabled.");
    }
  }
}
