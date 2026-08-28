package com.nearvanilla.iceCream.modules.firstJoinMessage;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.firstJoinMessage.events.FirstJoinMessagePlayerJoinEvent;

/**
 * FirstJoinMessageModule greets players joining the server for the first time. It broadcasts an
 * announcement to everyone and sends the new player a configurable welcome message, both written in
 * MiniMessage.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-27
 * @see Module
 * @see FirstJoinMessagePlayerJoinEvent
 */
public class FirstJoinMessageModule implements Module {

  protected boolean isEnabled = false;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.firstjoinmessage.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    // No commands
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new FirstJoinMessagePlayerJoinEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register FirstJoinMessage module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("FirstJoinMessage module has been enabled.");
    } else {
      IceCream.logger.info("FirstJoinMessage module is disabled.");
    }
  }
}
