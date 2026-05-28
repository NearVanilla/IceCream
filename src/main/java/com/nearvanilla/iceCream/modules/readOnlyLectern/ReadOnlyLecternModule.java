package com.nearvanilla.iceCream.modules.readOnlyLectern;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.readOnlyLectern.commands.ToggleLecternCommand;
import com.nearvanilla.iceCream.modules.readOnlyLectern.events.LecternBlockBreakEvent;
import com.nearvanilla.iceCream.modules.readOnlyLectern.events.LecternExplosionEvent;
import com.nearvanilla.iceCream.modules.readOnlyLectern.events.LecternPistonEvent;
import com.nearvanilla.iceCream.modules.readOnlyLectern.events.LecternTakeBookEvent;
import org.bukkit.NamespacedKey;

public class ReadOnlyLecternModule implements Module {
  protected boolean isEnabled = false;
  public static NamespacedKey READ_ONLY_KEY;

  private static void initKeys() {
    if (READ_ONLY_KEY != null) {
      return;
    }
    READ_ONLY_KEY = new NamespacedKey(IceCream.instance, "lectern.readonly");
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.readonlylectern.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new ToggleLecternCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new LecternTakeBookEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new LecternBlockBreakEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new LecternPistonEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new LecternExplosionEvent(), IceCream.instance);
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
        IceCream.logger.severe("Failed to register ReadOnlyLectern module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("ReadOnlyLectern module has been enabled.");
    } else {
      IceCream.logger.info("ReadOnlyLectern module is disabled.");
    }
  }
}
