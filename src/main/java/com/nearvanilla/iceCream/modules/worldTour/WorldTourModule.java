package com.nearvanilla.iceCream.modules.worldTour;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.worldTour.commands.WorldTourCommand;
import com.nearvanilla.iceCream.modules.worldTour.events.WorldTourListener;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * WorldTourModule provides commands and features for hosting World Tours, including join/leave
 * opt-in, host designation, teleportation, weather/time control across loaded worlds with
 * original-state preservation, and a failsafe that cleans up disconnected players after a
 * configurable timeout.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-06-26
 * @see Module
 * @see WorldTourCommand
 * @see WorldTourListener
 */
public class WorldTourModule implements Module {

  /** PDC key marking a player as opted into the World Tour. */
  public static NamespacedKey JOINED_KEY;

  // World PDC keys holding each world's environment from before the tour, so a tour the server
  // stopped during can be undone on the next start. On Folia that is the only way, since the
  // environment cannot change while the server shuts down.
  private static NamespacedKey ORIGINAL_STORM_KEY;
  private static NamespacedKey ORIGINAL_THUNDERING_KEY;
  private static NamespacedKey ORIGINAL_WEATHER_DURATION_KEY;
  private static NamespacedKey ORIGINAL_THUNDER_DURATION_KEY;
  private static NamespacedKey ORIGINAL_FULL_TIME_KEY;
  private static NamespacedKey ORIGINAL_KEEP_INVENTORY_KEY;

  private boolean isEnabled = false;

  /** The current World Tour host, or {@code null} if no host is set. */
  volatile Player currentHost;

  /** The player currently bearing the Glowing effect, or {@code null}. */
  volatile Player currentGlowing;

  // The original environment maps are only used on the global region.

  /** Original weather values per world, saved before the first tour weather change. */
  private final Map<String, WeatherSnapshot> originalWeather = new HashMap<>();

  /** Original full time values per world, saved before the first tour time change. */
  private final Map<String, Long> originalTimes = new HashMap<>();

  /** Original keepInventory gamerule values per world, saved before the tour started. */
  private final Map<String, Boolean> originalKeepInventory = new HashMap<>();

  /** Active failsafe tasks keyed by player UUID. */
  final Map<UUID, ScheduledTask> disconnectTasks = new ConcurrentHashMap<>();

  /** Configurable timeout in minutes for the disconnect failsafe. */
  private int failsafeTimeoutMinutes;

  /** Configurable start time in ticks for the World Tour. */
  private long startTimeTicks;

  /** Configurable time command aliases in ticks. */
  private final Map<String, Long> configuredTimes = new HashMap<>();

  /** Pending takeover requests, keyed by current host UUID, value is requester UUID. */
  final Map<UUID, UUID> pendingTakeovers = new ConcurrentHashMap<>();

  private static final class WeatherSnapshot {
    private final boolean storm;
    private final boolean thundering;
    private final int weatherDuration;
    private final int thunderDuration;

    private WeatherSnapshot(World world) {
      this.storm = world.hasStorm();
      this.thundering = world.isThundering();
      this.weatherDuration = world.getWeatherDuration();
      this.thunderDuration = world.getThunderDuration();
    }
  }

  private static void initKeys() {
    if (JOINED_KEY != null) return;
    JOINED_KEY = new NamespacedKey(IceCream.instance, "worldtour.joined");
    ORIGINAL_STORM_KEY = new NamespacedKey(IceCream.instance, "worldtour.original.storm");
    ORIGINAL_THUNDERING_KEY = new NamespacedKey(IceCream.instance, "worldtour.original.thundering");
    ORIGINAL_WEATHER_DURATION_KEY =
        new NamespacedKey(IceCream.instance, "worldtour.original.weather_duration");
    ORIGINAL_THUNDER_DURATION_KEY =
        new NamespacedKey(IceCream.instance, "worldtour.original.thunder_duration");
    ORIGINAL_FULL_TIME_KEY = new NamespacedKey(IceCream.instance, "worldtour.original.full_time");
    ORIGINAL_KEEP_INVENTORY_KEY =
        new NamespacedKey(IceCream.instance, "worldtour.original.keep_inventory");
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.worldtour.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new WorldTourCommand(this));
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new WorldTourListener(this), IceCream.instance);
  }

  @Override
  public void register() {
    initKeys();
    // Even with the module now disabled, undo what an unfinished tour left behind.
    SchedulerUtils.runGlobal(this::restoreLeftoverEnvironment);
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();
        failsafeTimeoutMinutes =
            Math.max(1, IceCream.config.getInt("modules.worldtour.failsafe-timeout-minutes", 5));

        configuredTimes.clear();
        configuredTimes.put("day", getConfiguredTimeTicks("day", 1000L));
        configuredTimes.put("noon", getConfiguredTimeTicks("noon", 6000L));
        configuredTimes.put("night", getConfiguredTimeTicks("night", 13000L));
        configuredTimes.put("midnight", getConfiguredTimeTicks("midnight", 18000L));

        startTimeTicks =
            Math.floorMod(
                IceCream.config.getLong(
                    "modules.worldtour.start-time-ticks", configuredTimes.get("day")),
                24000L);
        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register World Tour module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("World Tour module has been enabled.");
    } else {
      IceCream.logger.info("World Tour module is disabled.");
    }
  }

  private long getConfiguredTimeTicks(String name, long defaultTicks) {
    return Math.floorMod(
        IceCream.config.getLong("modules.worldtour.times." + name, defaultTicks), 24000L);
  }

  /**
   * Cleans up the World Tour module. Cancels all pending failsafe tasks, clears the host, and
   * restores weather/time across loaded worlds if they were altered.
   */
  public void unregister() {
    cancelAllDisconnectTasks();
    if (SchedulerUtils.isFolia()) {
      // Folia stops the region threads before plugins, so the environment can no longer change
      // here. It stays saved in each world and is restored when the server next starts.
      Player glowing = currentGlowing;
      currentGlowing = null;
      if (glowing != null) {
        try {
          glowing.removePotionEffect(PotionEffectType.GLOWING);
        } catch (RuntimeException e) {
          IceCream.logger.log(
              Level.WARNING, "Failed to remove the World Tour glow from " + glowing.getName(), e);
        }
      }
      currentHost = null;
    } else {
      clearHost(true);
    }
    isEnabled = false;
  }

  /**
   * @return the current host, or {@code null} if no host is set
   */
  public Player getCurrentHost() {
    return currentHost;
  }

  /**
   * @return the player currently bearing the Glowing effect, or {@code null}
   */
  public Player getCurrentGlowing() {
    return currentGlowing;
  }

  /**
   * @return the configured disconnect failsafe timeout in minutes
   */
  public int getFailsafeTimeoutMinutes() {
    return failsafeTimeoutMinutes;
  }

  /**
   * @return the configured World Tour start time in ticks
   */
  public long getStartTimeTicks() {
    return startTimeTicks;
  }

  /**
   * Gets a configured time alias in ticks.
   *
   * @param type the configured time type, such as day, noon, night, or midnight
   * @return the configured time in ticks
   */
  public long getTimeTicks(String type) {
    Long ticks = configuredTimes.get(type);
    if (ticks == null) {
      throw new IllegalArgumentException("Unknown time type: " + type);
    }
    return ticks;
  }

  /**
   * Stores a pending takeover request from a requester targeting a host.
   *
   * @param hostId the current host's UUID
   * @param requesterId the requesting player's UUID
   */
  public void setPendingTakeover(UUID hostId, UUID requesterId) {
    pendingTakeovers.put(hostId, requesterId);
  }

  /**
   * Removes and returns the pending takeover requester for a host.
   *
   * @param hostId the host's UUID
   * @return the requester's UUID, or {@code null} if none
   */
  public UUID removePendingTakeover(UUID hostId) {
    return pendingTakeovers.remove(hostId);
  }

  /**
   * Makes the player host, unless a tour is already running. Atomic, so two players starting a tour
   * at once in different regions cannot both become host.
   *
   * @param player the player starting the tour
   * @return true if the player is now host, false if a tour is already running
   */
  public synchronized boolean tryStart(Player player) {
    if (currentHost != null) return false;
    currentHost = player;
    return true;
  }

  /**
   * Sets the current host. If a different host is already set, notifies the previous host of the
   * takeover.
   *
   * @param player the player to set as host
   */
  public synchronized void setCurrentHost(Player player) {
    if (currentHost != null && !currentHost.equals(player)) {
      currentHost.sendMessage(
          net.kyori.adventure.text.Component.text(
              player.getName() + " has taken over as World Tour host."));
    }
    currentHost = player;
  }

  /**
   * Clears the current host and, if {@code restoreEnvironment} is true, restores original weather
   * and time across loaded worlds.
   *
   * @param restoreEnvironment whether to restore weather and time to pre-tour values
   */
  public synchronized void clearHost(boolean restoreEnvironment) {
    if (currentHost == null) return;
    pendingTakeovers.remove(currentHost.getUniqueId());
    if (restoreEnvironment) {
      SchedulerUtils.runGlobal(
          () -> {
            restoreWeather();
            restoreTime();
            restoreKeepInventory();
          });
    }
    clearGlowing();
    currentHost = null;
  }

  /**
   * Ends the tour: clears the host, restores environment, removes all participants, and notifies
   * them.
   */
  public void endTour() {
    clearHost(true);
    SchedulerUtils.runForEach(
        IceCream.instance.getServer().getOnlinePlayers(),
        online -> {
          if (Boolean.TRUE.equals(
              online.getPersistentDataContainer().get(JOINED_KEY, PersistentDataType.BOOLEAN))) {
            online.getPersistentDataContainer().set(JOINED_KEY, PersistentDataType.BOOLEAN, false);
            online.sendMessage(
                MiniMessage.miniMessage()
                    .deserialize(
                        "<gold>✨ The World Tour has concluded. Thank you for participating!</gold>"));
          }
        });
  }

  /** Removes the Glowing effect from whoever currently has it and clears the tracking reference. */
  public synchronized void clearGlowing() {
    Player glowing = currentGlowing;
    currentGlowing = null;
    if (glowing != null) {
      SchedulerUtils.runFor(glowing, () -> glowing.removePotionEffect(PotionEffectType.GLOWING));
    }
  }

  /**
   * Transfers the Glowing effect to a new player, removing it from the previous holder.
   *
   * @param player the player to apply Glowing to
   */
  public synchronized void setGlowing(Player player) {
    clearGlowing();
    currentGlowing = player;
    // The player may be in another region; effects can only change on their own thread.
    SchedulerUtils.runFor(
        player,
        () ->
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false)));
  }

  /** Saves the current weather for every loaded world before modification. */
  public void saveWeather() {
    if (!originalWeather.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      WeatherSnapshot snapshot = new WeatherSnapshot(world);
      originalWeather.put(world.getName(), snapshot);
      PersistentDataContainer data = world.getPersistentDataContainer();
      data.set(ORIGINAL_STORM_KEY, PersistentDataType.BOOLEAN, snapshot.storm);
      data.set(ORIGINAL_THUNDERING_KEY, PersistentDataType.BOOLEAN, snapshot.thundering);
      data.set(ORIGINAL_WEATHER_DURATION_KEY, PersistentDataType.INTEGER, snapshot.weatherDuration);
      data.set(ORIGINAL_THUNDER_DURATION_KEY, PersistentDataType.INTEGER, snapshot.thunderDuration);
    }
  }

  /** Applies clear weather across every loaded world. */
  public void setClearWeather() {
    setWeather(false, false);
  }

  /** Applies the requested weather state across every loaded world. */
  public void setWeather(String type) {
    switch (type) {
      case "clear" -> setWeather(false, false);
      case "rain" -> setWeather(true, false);
      case "thunder" -> setWeather(true, true);
      default -> throw new IllegalArgumentException("Unknown weather type: " + type);
    }
  }

  private void setWeather(boolean storm, boolean thundering) {
    saveWeather();
    for (World world : IceCream.instance.getServer().getWorlds()) {
      world.setStorm(storm);
      world.setThundering(thundering);
    }
  }

  /** Restores weather in every loaded world to the values saved before the tour changed it. */
  public void restoreWeather() {
    if (originalWeather.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      WeatherSnapshot snapshot = originalWeather.get(world.getName());
      if (snapshot != null) {
        world.setStorm(snapshot.storm);
        world.setThundering(snapshot.thundering);
        world.setWeatherDuration(snapshot.weatherDuration);
        world.setThunderDuration(snapshot.thunderDuration);
        removeKeys(
            world,
            ORIGINAL_STORM_KEY,
            ORIGINAL_THUNDERING_KEY,
            ORIGINAL_WEATHER_DURATION_KEY,
            ORIGINAL_THUNDER_DURATION_KEY);
      }
    }
    originalWeather.clear();
  }

  /** Saves the current full time for every loaded world before modification. */
  public void saveTime() {
    if (!originalTimes.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      originalTimes.put(world.getName(), world.getFullTime());
      world
          .getPersistentDataContainer()
          .set(ORIGINAL_FULL_TIME_KEY, PersistentDataType.LONG, world.getFullTime());
    }
  }

  /** Applies the requested time of day across every loaded world. */
  public void setTime(long ticks) {
    saveTime();
    for (World world : IceCream.instance.getServer().getWorlds()) {
      world.setTime(ticks);
    }
  }

  /** Restores full time in every loaded world to the values saved before the tour changed it. */
  public void restoreTime() {
    if (originalTimes.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      Long originalTime = originalTimes.get(world.getName());
      if (originalTime != null) {
        world.setFullTime(originalTime);
        removeKeys(world, ORIGINAL_FULL_TIME_KEY);
      }
    }
    originalTimes.clear();
  }

  /** Applies the default World Tour environment across every loaded world. */
  public void applyTourEnvironment() {
    saveWeather();
    saveTime();
    saveKeepInventory();
    enableKeepInventory();
    setClearWeather();
    setTime(startTimeTicks);
  }

  /** Saves the current keepInventory gamerule for every loaded world. */
  public void saveKeepInventory() {
    if (!originalKeepInventory.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      Boolean keepInventory = world.getGameRuleValue(GameRules.KEEP_INVENTORY);
      originalKeepInventory.put(world.getName(), keepInventory);
      if (keepInventory != null) {
        world
            .getPersistentDataContainer()
            .set(ORIGINAL_KEEP_INVENTORY_KEY, PersistentDataType.BOOLEAN, keepInventory);
      }
    }
  }

  /** Enables keepInventory in every loaded world. */
  public void enableKeepInventory() {
    for (World world : IceCream.instance.getServer().getWorlds()) {
      world.setGameRule(GameRules.KEEP_INVENTORY, true);
    }
  }

  /** Restores the original keepInventory gamerule values for every saved world. */
  public void restoreKeepInventory() {
    if (originalKeepInventory.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      Boolean original = originalKeepInventory.get(world.getName());
      if (original != null) {
        world.setGameRule(GameRules.KEEP_INVENTORY, original);
      }
      removeKeys(world, ORIGINAL_KEEP_INVENTORY_KEY);
    }
    originalKeepInventory.clear();
  }

  /**
   * Restores the weather, time and keepInventory that a tour left changed because the server
   * stopped or crashed before it ended. Runs on the global region at startup.
   */
  private void restoreLeftoverEnvironment() {
    for (World world : IceCream.instance.getServer().getWorlds()) {
      PersistentDataContainer data = world.getPersistentDataContainer();
      Boolean storm = data.get(ORIGINAL_STORM_KEY, PersistentDataType.BOOLEAN);
      Boolean thundering = data.get(ORIGINAL_THUNDERING_KEY, PersistentDataType.BOOLEAN);
      Integer weatherDuration = data.get(ORIGINAL_WEATHER_DURATION_KEY, PersistentDataType.INTEGER);
      Integer thunderDuration = data.get(ORIGINAL_THUNDER_DURATION_KEY, PersistentDataType.INTEGER);
      Long fullTime = data.get(ORIGINAL_FULL_TIME_KEY, PersistentDataType.LONG);
      Boolean keepInventory = data.get(ORIGINAL_KEEP_INVENTORY_KEY, PersistentDataType.BOOLEAN);
      if (storm == null
          && thundering == null
          && weatherDuration == null
          && thunderDuration == null
          && fullTime == null
          && keepInventory == null) {
        continue;
      }
      // Durations last: changing the storm or thunder resets them.
      if (storm != null) world.setStorm(storm);
      if (thundering != null) world.setThundering(thundering);
      if (weatherDuration != null) world.setWeatherDuration(weatherDuration);
      if (thunderDuration != null) world.setThunderDuration(thunderDuration);
      if (fullTime != null) world.setFullTime(fullTime);
      if (keepInventory != null) world.setGameRule(GameRules.KEEP_INVENTORY, keepInventory);
      removeKeys(
          world,
          ORIGINAL_STORM_KEY,
          ORIGINAL_THUNDERING_KEY,
          ORIGINAL_WEATHER_DURATION_KEY,
          ORIGINAL_THUNDER_DURATION_KEY,
          ORIGINAL_FULL_TIME_KEY,
          ORIGINAL_KEEP_INVENTORY_KEY);
      IceCream.logger.info(
          "Restored the environment of world "
              + world.getName()
              + " left changed by a World Tour that did not end.");
    }
  }

  private static void removeKeys(World world, NamespacedKey... keys) {
    PersistentDataContainer data = world.getPersistentDataContainer();
    for (NamespacedKey key : keys) data.remove(key);
  }

  /** Cancels every active disconnect failsafe task. */
  public void cancelAllDisconnectTasks() {
    for (ScheduledTask task : disconnectTasks.values()) {
      if (task != null && !task.isCancelled()) {
        task.cancel();
      }
    }
    disconnectTasks.clear();
  }

  /**
   * Cancels the disconnect failsafe task for a specific player.
   *
   * @param playerId the UUID of the player
   */
  public void cancelDisconnectTask(UUID playerId) {
    ScheduledTask task = disconnectTasks.remove(playerId);
    if (task != null && !task.isCancelled()) {
      task.cancel();
    }
  }

  /**
   * Schedules a disconnect failsafe task for a player.
   *
   * @param playerId the UUID of the player
   * @param task the scheduled task
   */
  public void scheduleDisconnectTask(UUID playerId, ScheduledTask task) {
    ScheduledTask existing = disconnectTasks.put(playerId, task);
    if (existing != null && !existing.isCancelled()) {
      existing.cancel();
    }
  }
}
