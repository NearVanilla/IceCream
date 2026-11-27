package com.nearvanilla.iceCream.modules.growthFreeze;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.growthFreeze.events.GrowthFreezeListener;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * GrowthFreezeModule lets players freeze crops, saplings, bamboo, frogspawn and sniffer eggs at
 * their current stage with a golden dandelion, the way vanilla already does for baby animals, and
 * unfreeze them with another.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 * @see Module
 * @see GrowthFreezeListener
 */
public class GrowthFreezeModule implements Module {
  private boolean enabled;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.growthfreeze.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    // No commands to register for this module.
  }

  @Override
  public void registerEvents() {
    MiniMessage miniMessage = MiniMessage.miniMessage();
    GrowthFreezeListener listener =
        new GrowthFreezeListener(
            new FrozenBlockStore(IceCream.instance.namespace()),
            miniMessage.deserialize(
                IceCream.config.getString(
                    "modules.growthfreeze.messages.frozen",
                    "<gray>Growth <aqua>frozen</aqua></gray>")),
            miniMessage.deserialize(
                IceCream.config.getString(
                    "modules.growthfreeze.messages.resumed",
                    "<gray>Growth <green>resumed</green></gray>")));
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Growth Freeze module is disabled.");
      return;
    }
    try {
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Growth Freeze module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Growth Freeze module.", exception);
    }
  }
}
