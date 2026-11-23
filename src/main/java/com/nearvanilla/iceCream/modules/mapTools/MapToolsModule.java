package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.integrations.SquaremapIntegration;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand;
import com.nearvanilla.iceCream.modules.mapTools.events.MapToolsChannel;
import com.nearvanilla.iceCream.modules.mapTools.events.MapToolsListener;
import com.nearvanilla.iceCream.modules.mapTools.events.MapToolsReplies;
import java.io.File;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import xyz.jpenilla.squaremap.api.Squaremap;

/**
 * Lets players with the manage permission put named areas (claims) and markers on squaremap, from
 * typed coordinates or forms. Areas are stored in {@code map-areas.json} and drawn on one layer per
 * world. They are visual only and do not protect blocks. The NearVanilla Tools mod draws claims on
 * its own map and sends the commands over {@link MapToolsChannel}.
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

  private boolean enabled;

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
    Bukkit.getMessenger()
        .registerIncomingPluginChannel(
            IceCream.instance, MapToolsChannel.CHANNEL, new MapToolsChannel());
    Bukkit.getMessenger().registerOutgoingPluginChannel(IceCream.instance, MapToolsChannel.CHANNEL);
    // Everything the mod is told goes through this listener; map tools itself only fires events.
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new MapToolsReplies(), IceCream.instance);
    MapToolsReplies.helloAll();
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
      enabled = true;
    } catch (Exception e) {
      IceCream.logger.log(Level.SEVERE, "Failed to register MapTools module", e);
      unregister();
      return;
    }
    IceCream.logger.info("MapTools module has been enabled.");
  }

  /**
   * Removes the module's layers from squaremap and stops talking to the mod. Safe to call when the
   * module was never enabled, or failed halfway through enabling.
   */
  public void unregister() {
    Bukkit.getMessenger()
        .unregisterIncomingPluginChannel(IceCream.instance, MapToolsChannel.CHANNEL);
    Bukkit.getMessenger()
        .unregisterOutgoingPluginChannel(IceCream.instance, MapToolsChannel.CHANNEL);
    MapToolsUtils.shutdown();
    enabled = false;
  }
}
