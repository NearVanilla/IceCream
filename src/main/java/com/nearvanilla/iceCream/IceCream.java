package com.nearvanilla.iceCream;

import com.nearvanilla.iceCream.modules.example.ExampleModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

import java.util.logging.Logger;

/**
 * The main class for the IceCream plugin. It sets up important components for itself and its modules, including
 * the instance, configuration, logger, and command management. All modules are also registered here.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-08
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
  }

  @Override
  public void onDisable() {
    // Plugin shutdown logic
  }
}
