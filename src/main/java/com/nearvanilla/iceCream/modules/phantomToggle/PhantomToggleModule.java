package com.nearvanilla.iceCream.modules.phantomToggle;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.phantomToggle.commands.PhantomToggleCommand;
import com.nearvanilla.iceCream.modules.phantomToggle.events.PhantomTogglePreSpawnEvent;
import com.nearvanilla.iceCream.modules.phantomToggle.events.PhantomToggleTargetEvent;
import org.bukkit.NamespacedKey;

/**
 * PhantomToggleModule allows players to opt out of phantoms. Players who have opted out will not
 * have phantoms spawned for them, and existing phantoms will not target them.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-18
 * @see Module
 * @see PhantomToggleUtils
 * @see PhantomToggleCommand
 * @see PhantomTogglePreSpawnEvent
 * @see PhantomToggleTargetEvent
 */
public class PhantomToggleModule implements Module {

  protected boolean isEnabled = false;
  public static NamespacedKey PHANTOM_DISABLED_KEY;

  private static void initKeys() {
    if (PHANTOM_DISABLED_KEY != null) {
      return;
    }
    PHANTOM_DISABLED_KEY = new NamespacedKey(IceCream.instance, "phantomtoggle.disabled");
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.phantomtoggle.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new PhantomToggleCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new PhantomTogglePreSpawnEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new PhantomToggleTargetEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    initKeys();
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register PhantomToggle module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("PhantomToggle module has been enabled.");
    } else {
      IceCream.logger.info("PhantomToggle module is disabled.");
    }
  }
}
