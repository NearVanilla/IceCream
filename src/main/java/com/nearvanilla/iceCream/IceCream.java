package com.nearvanilla.iceCream;

import com.nearvanilla.iceCream.modules.example.ExampleModule;
import com.nearvanilla.iceCream.modules.isSlimeChunk.isSlimeChunkModule;
import com.nearvanilla.iceCream.modules.lightning.LightningModule;
import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.modules.wanderingTrades.WanderingTradesModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
 * @since 2025-08-08
 * @see JavaPlugin
 * @see PaperCommandManager
 * @see AnnotationParser
 * @see Logger
 * @see FileConfiguration
 */
public final class IceCream extends JavaPlugin {
  // General plugin instance, config, logger and command management.
  public static IceCream instance;
  public static FileConfiguration config;
  public static Logger logger;
  public static PaperCommandManager<CommandSourceStack> commandManager;
  public static AnnotationParser<CommandSourceStack> annotationParser;
  // Modules
  private final ExampleModule exampleModule = new ExampleModule();
  private final LightningModule lightningModule = new LightningModule();
  private final MuteDeathsModule muteDeathsModule = new MuteDeathsModule();
  private final isSlimeChunkModule isSlimeChunkModule = new isSlimeChunkModule();
  private final WanderingTradesModule wanderingTradesModule = new WanderingTradesModule();
  private final StaffModeModule staffModeModule = new StaffModeModule();

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
    // Register modules
    exampleModule.register();
    lightningModule.register();
    muteDeathsModule.register();
    isSlimeChunkModule.register();
    wanderingTradesModule.register();
    staffModeModule.register();
  }

  @Override
  public void onDisable() {
    // Plugin shutdown logic
  }
}
