package com.nearvanilla.iceCream.modules.persistentHeads;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.persistentHeads.events.PersistentHeadsListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * Keeps the lore of player heads when they are placed and broken again, such as the "Killed by"
 * line Player Head Drops adds. Replaces PaperTweaks' Persistent Heads module.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 * @see PersistentHeadsListener
 */
public class PersistentHeadsModule implements Module {
  private boolean enabled;
  private PersistentHeadsListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.persistentheads.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {}

  @Override
  public void registerEvents() {
    listener =
        new PersistentHeadsListener(
            new HeadStore(IceCream.instance.namespace()),
            task -> Bukkit.getScheduler().runTask(IceCream.instance, task));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Persistent Heads module is disabled.");
      return;
    }
    try {
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Persistent Heads module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Persistent Heads module.", exception);
      unregister();
    }
  }

  /** Unregisters the listener and marks the module as disabled. */
  public void unregister() {
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    enabled = false;
  }
}
