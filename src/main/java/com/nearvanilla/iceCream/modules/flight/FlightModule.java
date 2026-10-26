package com.nearvanilla.iceCream.modules.flight;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.flight.commands.FlyCommand;
import com.nearvanilla.iceCream.modules.flight.events.FlightListener;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/** Permission-gated flight with a preference saved across reconnects and respawns. */
public class FlightModule implements Module {
  private boolean isEnabled;
  public static NamespacedKey FLIGHT_ENABLED_KEY;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.flight.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new FlyCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new FlightListener(), IceCream.instance);
  }

  @Override
  public void register() {
    FLIGHT_ENABLED_KEY = new NamespacedKey(IceCream.instance, "flight.enabled");
    if (!shouldEnable()) {
      IceCream.logger.info("Flight module is disabled.");
      return;
    }
    registerCommands();
    registerEvents();
    isEnabled = true;
    for (Player player : IceCream.instance.getServer().getOnlinePlayers()) {
      SchedulerUtils.runFor(player, () -> FlightUtils.restoreFlight(player));
    }
    IceCream.logger.info("Flight module has been enabled.");
  }

  public void unregister() {
    if (!isEnabled) {
      return;
    }
    for (Player player : IceCream.instance.getServer().getOnlinePlayers()) {
      // Runs directly: on Folia this is the shutdown thread, which never runs scheduled tasks but
      // may still change a player's own abilities before players are saved.
      try {
        FlightUtils.suspendFlight(player);
      } catch (RuntimeException e) {
        IceCream.logger.log(
            Level.WARNING, "Failed to suspend the flight of " + player.getName(), e);
      }
    }
    isEnabled = false;
  }
}
