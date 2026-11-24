package com.nearvanilla.iceCream.modules.silenceMobs;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.silenceMobs.events.SilenceMobsListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * Lets players silence a mob by naming it with a name tag called "silence me". Replaces
 * PaperTweaks' Silence Mobs module.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see SilenceMobsListener
 */
public class SilenceMobsModule implements Module {
  /** Needed to silence mobs. Everyone has it by default. */
  public static final String PERMISSION = "icecream.modules.silencemobs";

  private boolean enabled;
  private SilenceMobsListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.silencemobs.enabled", false);
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
        new SilenceMobsListener(
            SilenceMobsConfig.load(),
            task -> Bukkit.getScheduler().runTask(IceCream.instance, task));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Silence Mobs module is disabled.");
      return;
    }
    try {
      registerPermission();
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Silence Mobs module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Silence Mobs module.", exception);
      unregister();
    }
  }

  /**
   * Registers {@link #PERMISSION} so that every player has it by default, as in PaperTweaks. It is
   * registered in code because plugin.yml declares no permissions. Without this, Bukkit would only
   * grant the unknown permission to operators.
   */
  public static void registerPermission() {
    if (Bukkit.getPluginManager().getPermission(PERMISSION) != null) return;
    Bukkit.getPluginManager()
        .addPermission(
            new Permission(PERMISSION, "Silence mobs with a name tag.", PermissionDefault.TRUE));
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
