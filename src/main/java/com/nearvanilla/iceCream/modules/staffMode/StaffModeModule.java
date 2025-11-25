package com.nearvanilla.iceCream.modules.staffMode;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.staffMode.commands.StaffModeCommand;
import com.nearvanilla.iceCream.modules.staffMode.events.StaffModePlayerJoinEvent;
import org.bukkit.NamespacedKey;

/**
 * StaffModeModule is a demonstration of how to create a module in the IceCream plugin. It registers
 * a simple command and an event listener that welcomes players when they join the server.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-23
 * @see Module
 * @see StaffModeCommand
 */
public class StaffModeModule implements Module {
  protected boolean isEnabled = false;
  public static NamespacedKey STAFF_MODE_TOGGLE_KEY = new NamespacedKey(IceCream.instance, "staff_mode.enabled");
  public static NamespacedKey STAFF_MODE_INVENTORY_KEY = new NamespacedKey(IceCream.instance, "staff_mode.inventory");
  public static NamespacedKey STAFF_MODE_LOCATION_WORLD = new NamespacedKey(IceCream.instance, "staff_mode.location.world");
  public static NamespacedKey STAFF_MODE_LOCATION_X = new NamespacedKey(IceCream.instance, "staff_mode.location.x");
  public static NamespacedKey STAFF_MODE_LOCATION_Y = new NamespacedKey(IceCream.instance, "staff_mode.location.y");
  public static NamespacedKey STAFF_MODE_LOCATION_Z = new NamespacedKey(IceCream.instance, "staff_mode.location.z");
  public static NamespacedKey STAFF_MODE_LOCATION_YAW = new NamespacedKey(IceCream.instance, "staff_mode.location.yaw");
  public static NamespacedKey STAFF_MODE_LOCATION_PITCH = new NamespacedKey(IceCream.instance, "staff_mode.location.pitch");
  public static NamespacedKey STAFF_MODE_CONFIRM_KEY = new NamespacedKey(IceCream.instance, "staff_mode.confirm_disable");

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.staffmode.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new StaffModeCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new StaffModePlayerJoinEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Staff Mode module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Staff Mode module has been enabled.");
    } else {
      IceCream.logger.info("Staff Mode module is disabled.");
    }
  }
}
