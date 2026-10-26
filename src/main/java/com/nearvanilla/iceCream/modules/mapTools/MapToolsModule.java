package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.integrations.SquaremapIntegration;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand;
import com.nearvanilla.iceCream.modules.mapTools.events.MapToolsListener;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import xyz.jpenilla.squaremap.api.Squaremap;

/**
 * Lets players with the manage permission draw named areas (claims) on squaremap. Areas are stored
 * in {@code map-areas.json} and drawn on one layer per world. They are visual only and do not
 * protect blocks.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 * @see Module
 * @see MapToolsCommand
 */
public class MapToolsModule implements Module {

  /** The stored areas. */
  public static MapAreasStore store;

  /** Edit mode sessions, keyed by player. A player is in edit mode while they have one. */
  public static final Map<UUID, EditSession> sessions = new ConcurrentHashMap<>();

  private boolean enabled;
  private ScheduledTask borderTask;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.maptools.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new MapToolsCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new MapToolsListener(), IceCream.instance);
  }

  @Override
  public void register() {
    if (!shouldEnable()) {
      IceCream.logger.info("MapTools module is disabled.");
      return;
    }
    Squaremap squaremap = SquaremapIntegration.lookup();
    if (squaremap == null) {
      IceCream.logger.warning("MapTools module requires squaremap, disabling.");
      return;
    }
    try {
      store = new MapAreasStore(new File(IceCream.instance.getDataFolder(), "map-areas.json"));
      store.load();
      MapToolsUtils.init(squaremap);
      registerCommands();
      registerEvents();
      // Every half second: redraw outlines when needed and update the action bar.
      borderTask = SchedulerUtils.runGlobalTimer(new AreaBorderTask(), 10, 10);
      enabled = true;
    } catch (Exception e) {
      IceCream.logger.log(Level.SEVERE, "Failed to register MapTools module", e);
      unregister();
      return;
    }
    IceCream.logger.info("MapTools module has been enabled.");
  }

  /**
   * Removes the module's layers from squaremap and its displays from the world. Safe to call when
   * the module was never enabled, or failed halfway through enabling.
   */
  public void unregister() {
    if (borderTask != null) borderTask.cancel();
    borderTask = null;
    if (SchedulerUtils.isFolia()) {
      // Folia stops the region threads first, so the displays cannot be removed here. They are
      // never saved with the world, so they are gone after the restart.
      AreaDisplays.forgetAll();
    } else {
      AreaDisplays.clearAll();
    }
    MapToolsUtils.shutdown();
    sessions.clear();
    enabled = false;
  }
}
