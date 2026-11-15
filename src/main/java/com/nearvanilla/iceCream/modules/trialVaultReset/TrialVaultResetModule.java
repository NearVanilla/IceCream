package com.nearvanilla.iceCream.modules.trialVaultReset;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.trialVaultReset.events.TrialVaultResetListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * Lets a player reset a trial vault they already looted by using its key once the configured delay
 * has passed. Replaces the ForgetfulTrialVault plugin.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 * @see TrialVaultResetListener
 */
public class TrialVaultResetModule implements Module {
  private boolean enabled;
  private TrialVaultResetListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.trialvaultreset.enabled", false);
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
        new TrialVaultResetListener(
            TrialVaultResetConfig.load(),
            new LootTimes(IceCream.instance.namespace()),
            System::currentTimeMillis,
            task -> Bukkit.getScheduler().runTask(IceCream.instance, task));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Trial Vault Reset module is disabled.");
      return;
    }
    try {
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Trial Vault Reset module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Trial Vault Reset module.", exception);
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
