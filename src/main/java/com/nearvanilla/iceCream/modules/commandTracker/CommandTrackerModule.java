package com.nearvanilla.iceCream.modules.commandTracker;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.commandTracker.commands.CommandLogCommand;
import com.nearvanilla.iceCream.modules.commandTracker.commands.CommandLogFormatter;
import com.nearvanilla.iceCream.modules.commandTracker.events.CommandTrackerListener;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * Records the commands players run (who, what, where and when) to SQLite so staff can look them up
 * with {@code /commandlog} when moderating or looking into an incident.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see CommandTrackerListener
 * @see CommandLogCommand
 */
public class CommandTrackerModule implements Module {
  public static final String LOOKUP_PERMISSION = "icecream.modules.commandtracker.commandlog";
  private static final String CONFIG = "modules.commandtracker";
  private static final String DATABASE_FILE = "commandtracker.db";
  private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 50;
  private static final long CLEANUP_INTERVAL_TICKS = 20L * 60 * 60;

  private boolean enabled;
  private CommandLogRepository repository;
  private CommandTrackerListener listener;
  private BukkitTask cleanupTask;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean(CONFIG + ".enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    int pageSize =
        Math.clamp(
            IceCream.config.getInt(CONFIG + ".page-size", DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
    CommandLogFormatter formatter =
        new CommandLogFormatter(
            dateFormat(), ZoneId.systemDefault(), CommandTrackerModule::worldKey);
    IceCream.annotationParser.parse(
        new CommandLogCommand(
            repository,
            formatter,
            pageSize,
            task -> Bukkit.getScheduler().runTask(IceCream.instance, task),
            label -> Bukkit.getCommandMap().getCommand(label),
            Clock.systemDefaultZone()));
  }

  @Override
  public void registerEvents() {
    ConfigurationSection config = IceCream.config.getConfigurationSection(CONFIG);
    CommandRules rules =
        new CommandRules(
            config.getStringList("ignored-commands"),
            config.getStringList("tracked-commands"),
            config.getStringList("redact.all-arguments"),
            config.getStringList("redact.after-first-argument"));
    listener =
        new CommandTrackerListener(
            repository,
            rules,
            config.getBoolean("record-console", false),
            label -> Bukkit.getCommandMap().getCommand(label),
            System::currentTimeMillis);
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Command Tracker module is disabled.");
      return;
    }
    try {
      Path dataFolder = IceCream.instance.getDataFolder().toPath();
      Files.createDirectories(dataFolder);
      repository = new CommandLogRepository(dataFolder.resolve(DATABASE_FILE));
      registerCommands();
      registerEvents();
      scheduleCleanup();
      enabled = true;
    } catch (Exception e) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Command Tracker module.", e);
      unregister();
      return;
    }
    IceCream.logger.info("Command Tracker module has been enabled.");
  }

  /** Deletes commands older than the retention period now and then every hour. */
  private void scheduleCleanup() {
    int retentionDays = Math.max(0, IceCream.config.getInt(CONFIG + ".retention-days", 30));
    if (retentionDays == 0) {
      return;
    }
    Duration retention = Duration.ofDays(retentionDays);
    CommandLogRepository target = repository;
    cleanupTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                IceCream.instance,
                () ->
                    target
                        .deleteOlderThan(System.currentTimeMillis() - retention.toMillis())
                        .whenComplete(
                            (deleted, error) -> {
                              if (error != null) {
                                IceCream.logger.log(
                                    Level.SEVERE, "Failed to delete old tracked commands", error);
                              } else if (deleted > 0) {
                                IceCream.logger.info(
                                    "Command Tracker deleted "
                                        + deleted
                                        + " commands older than "
                                        + retentionDays
                                        + " days.");
                              }
                            }),
                20L,
                CLEANUP_INTERVAL_TICKS);
  }

  private static DateTimeFormatter dateFormat() {
    String pattern = IceCream.config.getString(CONFIG + ".date-format", DEFAULT_DATE_FORMAT);
    try {
      return DateTimeFormatter.ofPattern(pattern);
    } catch (IllegalArgumentException e) {
      IceCream.logger.warning(
          "Invalid " + CONFIG + ".date-format '" + pattern + "', using " + DEFAULT_DATE_FORMAT);
      return DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
    }
  }

  private static String worldKey(String worldName) {
    World world = Bukkit.getWorld(worldName);
    return world == null ? null : world.getKey().asString();
  }

  /** Stops recording, then flushes queued writes and closes the database. */
  public void unregister() {
    enabled = false;
    if (cleanupTask != null) {
      cleanupTask.cancel();
      cleanupTask = null;
    }
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    if (repository == null) {
      return;
    }
    try {
      repository.close();
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to close the Command Tracker database", e);
    }
    repository = null;
  }
}
