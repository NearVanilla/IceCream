package com.nearvanilla.iceCream;

import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.desertMobs.DesertMobsModule;
import com.nearvanilla.iceCream.modules.example.ExampleModule;
import com.nearvanilla.iceCream.modules.isSlimeChunk.isSlimeChunkModule;
import com.nearvanilla.iceCream.modules.lightning.LightningModule;
import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import com.nearvanilla.iceCream.modules.playerHeadDrops.PlayerHeadDropsModule;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.modules.wanderful.WanderfulModule;
import com.nearvanilla.iceCream.modules.wanderingTrades.WanderingTradesModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * The main class for the IceCream plugin. It sets up important components for use across the
 * plugin, including the instance, configuration, logger, and command management. Modules are also
 * registered here.
 *
 * @author 105hua
 * @version 1.0
 * @since 2024-09-15
 * @see JavaPlugin
 * @see PaperCommandManager
 * @see AnnotationParser
 * @see Logger
 * @see FileConfiguration
 */
public class IceCream extends JavaPlugin {
  // General plugin instance, config, logger and command management.
  public static IceCream instance;
  public static FileConfiguration config;
  public static Logger logger;
  public static PaperCommandManager<CommandSourceStack> commandManager;
  public static AnnotationParser<CommandSourceStack> annotationParser;

  /**
   * Returns the list of modules to register. Override in subclasses to provide a different module
   * set (e.g. Mochi).
   *
   * @return the modules for this plugin variant
   */
  protected List<Module> getModules() {
    return List.of(
        new DesertMobsModule(),
        new ExampleModule(),
        new isSlimeChunkModule(),
        new LightningModule(),
        new MuteDeathsModule(),
        new PlayerHeadDropsModule(),
        new ReadOnlyLecternModule(),
        new SpectatorModule(),
        new StaffModeModule(),
        new WanderfulModule(),
        new WanderingTradesModule());
  }

  @Override
  public void onEnable() {
    // Setup logic
    instance = this;
    logger = getLogger();
    commandManager =
        PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(this);
    annotationParser = new AnnotationParser<>(commandManager, CommandSourceStack.class);
    this.saveDefaultConfig();
    config = this.getConfig();
    if (config.contains("modules.vanish")) {
      logger.warning(
          "Detected deprecated 'modules.vanish' config section. This module has been"
              + " removed. Please remove this section from your config.");
    }
    // Register modules
    for (Module module : getModules()) {
      module.register();
    }
  }

  @Override
  public void onDisable() {
    for (Module module : getModules()) {
      if (module instanceof SpectatorModule spectator) {
        spectator.unregister();
      }
    }
  }
}
