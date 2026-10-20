package com.nearvanilla.iceCream.modules.deathDb;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.deathDb.commands.RestoreDeathCommand;
import com.nearvanilla.iceCream.modules.deathDb.events.DeathGuiListener;
import com.nearvanilla.iceCream.modules.deathDb.events.DeathRecordListener;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.plugin.PluginManager;

/**
 * Logs player deaths (time, position and a full inventory snapshot) to SQLite and lets staff browse
 * and restore them through a chest GUI.
 */
public class DeathDbModule implements Module {
  public static final String RESTORE_PERMISSION = "icecream.modules.deathdb.restoredeath";
  private static final String DATABASE_FILE = "deathdb.db";

  protected boolean isEnabled = false;
  private DeathRepository repository;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.deathdb.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new RestoreDeathCommand(repository));
  }

  @Override
  public void registerEvents() {
    PluginManager pluginManager = IceCream.instance.getServer().getPluginManager();
    pluginManager.registerEvents(new DeathRecordListener(repository), IceCream.instance);
    pluginManager.registerEvents(new DeathGuiListener(), IceCream.instance);
  }

  @Override
  public void register() {
    if (!shouldEnable()) {
      IceCream.logger.info("Death DB module is disabled.");
      return;
    }
    try {
      Path dataFolder = IceCream.instance.getDataFolder().toPath();
      Files.createDirectories(dataFolder);
      int maxRecords =
          Math.max(0, IceCream.config.getInt("modules.deathdb.max-records-per-player", 100));
      repository = new DeathRepository(dataFolder.resolve(DATABASE_FILE), maxRecords);
      registerCommands();
      registerEvents();
      isEnabled = true;
    } catch (Exception e) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Death DB module", e);
      unregister();
      return;
    }
    IceCream.logger.info("Death DB module has been enabled.");
  }

  /** Flushes queued writes and closes the database. Safe to call when never enabled. */
  public void unregister() {
    isEnabled = false;
    if (repository == null) {
      return;
    }
    try {
      repository.close();
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to close the Death DB database", e);
    }
    repository = null;
  }
}
