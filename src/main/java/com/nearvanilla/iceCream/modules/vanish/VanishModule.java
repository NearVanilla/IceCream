package com.nearvanilla.iceCream.modules.vanish;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.vanish.commands.VanishCommand;
import com.nearvanilla.iceCream.modules.vanish.events.VanishPlayerJoinEvent;
import com.nearvanilla.iceCream.modules.vanish.events.VanishPlayerQuitEvent;
import com.nearvanilla.iceCream.modules.vanish.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.vanish.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.vanish.integrations.DynmapIntegration;
import org.bukkit.NamespacedKey;

/**
 * VanishModule provides vanish functionality for staff members. When vanished, players are hidden
 * from other players, the tab list, Dynmap, and DiscordSRV. They can only chat in staff channels.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 * @see Module
 */
public class VanishModule implements Module {
  protected boolean isEnabled = false;
  public static NamespacedKey VANISH_TOGGLE_KEY;
  public static NamespacedKey VANISH_LOCATION_WORLD;
  public static NamespacedKey VANISH_LOCATION_X;
  public static NamespacedKey VANISH_LOCATION_Y;
  public static NamespacedKey VANISH_LOCATION_Z;
  public static NamespacedKey VANISH_LOCATION_YAW;
  public static NamespacedKey VANISH_LOCATION_PITCH;

  private static void initKeys() {
    if (VANISH_TOGGLE_KEY != null) return;

    VANISH_TOGGLE_KEY = new NamespacedKey(IceCream.instance, "vanish.enabled");
    VANISH_LOCATION_WORLD = new NamespacedKey(IceCream.instance, "vanish.location.world");
    VANISH_LOCATION_X = new NamespacedKey(IceCream.instance, "vanish.location.x");
    VANISH_LOCATION_Y = new NamespacedKey(IceCream.instance, "vanish.location.y");
    VANISH_LOCATION_Z = new NamespacedKey(IceCream.instance, "vanish.location.z");
    VANISH_LOCATION_YAW = new NamespacedKey(IceCream.instance, "vanish.location.yaw");
    VANISH_LOCATION_PITCH = new NamespacedKey(IceCream.instance, "vanish.location.pitch");
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.vanish.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new VanishCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new VanishPlayerJoinEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new VanishPlayerQuitEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    initKeys();
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();

        DynmapIntegration.init();
        DiscordSRVIntegration.init();
        CarbonChatIntegration.init();
        VanishUtils.init();

        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Vanish module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Vanish module has been enabled.");
    } else {
      IceCream.logger.info("Vanish module is disabled.");
    }
  }

  /** Cleans up the vanish module. Should be called during plugin shutdown. */
  public void unregister() {
    if (!isEnabled) return;

    VanishUtils.cleanup();
  }
}
