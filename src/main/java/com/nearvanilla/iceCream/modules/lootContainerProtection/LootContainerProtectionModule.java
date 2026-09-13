package com.nearvanilla.iceCream.modules.lootContainerProtection;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.lootContainerProtection.events.LootContainerProtectionListener;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;

public class LootContainerProtectionModule implements Module {
  private boolean enabled;
  private LootContainerProtectionListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.lootcontainerprotection.enabled", false);
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
        new LootContainerProtectionListener(
            new NamespacedKey(IceCream.instance, "lootcontainer.original_table"));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Loot Container Protection module is disabled.");
      return;
    }
    try {
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Loot Container Protection module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(
          Level.SEVERE, "Failed to register Loot Container Protection module.", exception);
      unregister();
    }
  }

  public void unregister() {
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    enabled = false;
  }
}
