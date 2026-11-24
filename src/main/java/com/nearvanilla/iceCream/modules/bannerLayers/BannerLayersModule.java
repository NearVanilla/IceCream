package com.nearvanilla.iceCream.modules.bannerLayers;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.bannerLayers.events.BannerLayersListener;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Raises the number of layers the loom can put on a banner from the survival limit of 6 to the
 * creative limit of 16, or to the configured limit.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see BannerLayersListener
 */
public class BannerLayersModule implements Module {
  private boolean enabled;
  private BannerLayersListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.bannerlayers.enabled", false);
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
        new BannerLayersListener(
            new BannerLayerStore(
                IceCream.instance.namespace(),
                RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN)),
            BannerLayersConfig.load().maxLayers(),
            task -> Bukkit.getScheduler().runTask(IceCream.instance, task));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Banner Layers module is disabled.");
      return;
    }
    try {
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Banner Layers module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Banner Layers module.", exception);
      unregister();
    }
  }

  /**
   * Unregisters the listener and marks the module as disabled. Banners hidden in open looms are
   * revealed first, so none are left with missing layers.
   */
  public void unregister() {
    if (listener != null) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        listener.revealOpenLoom(player);
      }
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    enabled = false;
  }
}
