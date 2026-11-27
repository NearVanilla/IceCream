package com.nearvanilla.iceCream.modules.playerStatus;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.playerStatus.commands.PlayerStatusCommand;
import com.nearvanilla.iceCream.modules.playerStatus.events.PlayerStatusListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * Shows a status such as do not disturb, streaming or AFK next to player names in the tab list. The
 * status is sent to the proxy, where bat displays it.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-23
 * @see PlayerStatusManager
 */
public class PlayerStatusModule implements Module {

  private PlayerStatusManager manager;
  private PlayerStatusListener listener;
  private boolean enabled;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.playerstatus.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new PlayerStatusCommand(manager));
  }

  @Override
  public void registerEvents() {
    listener = new PlayerStatusListener(manager);
    Bukkit.getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("PlayerStatus module is disabled.");
      return;
    }
    try {
      manager = new PlayerStatusManager();
      manager.start();
      registerEvents();
      registerCommands();
      enabled = true;
      IceCream.logger.info("PlayerStatus module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register PlayerStatus module.", exception);
      unregister();
    }
  }

  public void unregister() {
    enabled = false;
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    if (manager != null) {
      manager.stop();
      manager = null;
    }
  }
}
