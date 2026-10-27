package com.nearvanilla.iceCream.modules.antiAfk;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.antiAfk.events.AntiAfkListener;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatIntegration;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * Kicks players who stay AFK. After a period without deliberate input the player is shown a code
 * and must type it in chat before the answer window runs out. The answer is hidden from other
 * players. Players with {@link #EXEMPT_PERMISSION}, and players in spectator mode, are never
 * checked. The permission is registered with a default of false, so operators are checked too
 * unless they are granted it.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 * @see AntiAfkManager
 */
public class AntiAfkModule implements Module {
  public static final String EXEMPT_PERMISSION = "icecream.modules.antiafk.exempt";

  private final CarbonChatIntegration carbonChat = new CarbonChatIntegration();
  private AntiAfkManager manager;
  private AntiAfkListener listener;
  private boolean enabled;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.antiafk.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /** Returns the running anti-AFK check, or null when the module is disabled. */
  public AntiAfkManager getManager() {
    return enabled ? manager : null;
  }

  @Override
  public void registerCommands() {}

  @Override
  public void registerEvents() {
    listener = new AntiAfkListener(manager);
    Bukkit.getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Anti-AFK module is disabled.");
      return;
    }
    AntiAfkConfig config = AntiAfkConfig.load();
    if (config == null) return;
    try {
      registerPermission();
      manager = new AntiAfkManager(config);
      registerCommands();
      registerEvents();
      carbonChat.initChatFilter(manager::shouldHideChat);
      manager.start();
      enabled = true;
      IceCream.logger.info("Anti-AFK module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Anti-AFK module.", exception);
      unregister();
    }
  }

  // Registered in code because plugin.yml declares no permissions. Without this, Bukkit would
  // treat the unknown permission as granted to operators.
  private static void registerPermission() {
    if (Bukkit.getPluginManager().getPermission(EXEMPT_PERMISSION) != null) return;
    Bukkit.getPluginManager()
        .addPermission(
            new Permission(
                EXEMPT_PERMISSION, "Skips the anti-AFK check.", PermissionDefault.FALSE));
  }

  public void unregister() {
    enabled = false;
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    carbonChat.cleanup();
    if (manager != null) {
      manager.stop();
      manager = null;
    }
  }
}
