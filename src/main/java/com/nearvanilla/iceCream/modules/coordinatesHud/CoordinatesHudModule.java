package com.nearvanilla.iceCream.modules.coordinatesHud;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.coordinatesHud.commands.CoordinatesHudCommand;
import com.nearvanilla.iceCream.modules.coordinatesHud.events.CoordinatesHudListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shows players their coordinates, the direction they face and the time of day in their action bar,
 * toggled with {@code /togglehud}. Replaces PaperTweaks' Coordinates HUD module.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see CoordinatesHud
 * @see CoordinatesHudCommand
 * @see CoordinatesHudListener
 */
public class CoordinatesHudModule implements Module {
  private static final String PATH = "modules.coordinateshud.";

  private boolean enabled;
  private CoordinatesHud hud;
  private CoordinatesHudListener listener;
  private BukkitTask task;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean(PATH + "enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(
        new CoordinatesHudCommand(
            hud,
            new CoordinatesHudCommand.Messages(
                IceCream.config.getString(PATH + "messages.on", "<green>HUD toggled ON</green>"),
                IceCream.config.getString(PATH + "messages.off", "<green>HUD toggled OFF</green>"),
                IceCream.config.getString(
                    PATH + "messages.toggled-for",
                    "<gray><italic>HUD toggled for <count> players</italic></gray>"))));
  }

  @Override
  public void registerEvents() {
    listener = new CoordinatesHudListener(hud);
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Coordinates HUD module is disabled.");
      return;
    }
    try {
      hud =
          new CoordinatesHud(
              new HudPreference(IceCream.instance.namespace()),
              new HudLine(IceCream.config.getString(PATH + "hud", HudLine.DEFAULT_TEMPLATE)),
              IceCream.config.getBoolean(PATH + "enabled-by-default", false));
      registerCommands();
      registerEvents();
      Bukkit.getOnlinePlayers().forEach(hud::load);
      long interval = Math.max(1L, IceCream.config.getLong(PATH + "update-interval-ticks", 2L));
      task = Bukkit.getScheduler().runTaskTimer(IceCream.instance, hud::tick, 1L, interval);
      enabled = true;
      IceCream.logger.info("Coordinates HUD module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Coordinates HUD module.", exception);
      unregister();
    }
  }

  /** Stops refreshing the HUD, unregisters the listener and marks the module as disabled. */
  public void unregister() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    if (hud != null) {
      hud.clear();
    }
    enabled = false;
  }
}
