package com.nearvanilla.iceCream.modules.muteDeaths;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.muteDeaths.commands.MuteDeathsCommand;
import com.nearvanilla.iceCream.modules.muteDeaths.events.MuteDeathsPlayerDeathEvent;
import com.nearvanilla.iceCream.modules.muteDeaths.events.MuteDeathsPlayerJoinEvent;
import org.bukkit.NamespacedKey;

/**
 * MuteDeathsModule adds a command to mute the users death messages. a PlayerDeathEvent that listens
 * for player deaths and mutes the death message if the player has enabled it. and a PlayerJoinEvent
 * to remind the user that their deaths are muted.
 *
 * @author 105hua
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-11
 * @see Module
 * @see MuteDeathsCommand
 * @see MuteDeathsPlayerDeathEvent
 * @see MuteDeathsPlayerJoinEvent
 */
public class MuteDeathsModule implements Module {

  protected boolean isEnabled = false;
  public static final NamespacedKey MUTE_KEY = new NamespacedKey("mutedeaths", "toggle");

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.mutedeaths.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new MuteDeathsCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new MuteDeathsPlayerDeathEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new MuteDeathsPlayerJoinEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register MuteDeaths module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("MuteDeaths module has been enabled.");
    } else {
      IceCream.logger.info("MuteDeaths module is disabled.");
    }
  }
}
