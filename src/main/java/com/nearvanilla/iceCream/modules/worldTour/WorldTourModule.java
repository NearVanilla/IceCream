package com.nearvanilla.iceCream.modules.worldTour;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.worldTour.commands.WorldTourCommand;
import com.nearvanilla.iceCream.modules.worldTour.events.WorldTourListener;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

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

  private boolean isEnabled = false;

  /** The current World Tour host, or {@code null} if no host is set. */
  Player currentHost;

  /** The player currently bearing the Glowing effect, or {@code null}. */
  Player currentGlowing;

  /** Original weather values per world, saved before the first tour weather change. */
  private final Map<String, WeatherSnapshot> originalWeather = new HashMap<>();

  /** Original full time values per world, saved before the first tour time change. */
  private final Map<String, Long> originalTimes = new HashMap<>();

  /** Original keepInventory gamerule values per world, saved before the tour started. */
  private final Map<String, Boolean> originalKeepInventory = new HashMap<>();

  /** Active failsafe tasks keyed by player UUID. */
  final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();

  /** Configurable timeout in minutes for the disconnect failsafe. */
  private int failsafeTimeoutMinutes;

  /** Configurable start time in ticks for the World Tour. */
  private long startTimeTicks;

  /** Configurable time command aliases in ticks. */
  private final Map<String, Long> configuredTimes = new HashMap<>();

  /** Pending takeover requests, keyed by current host UUID, value is requester UUID. */
  final Map<UUID, UUID> pendingTakeovers = new HashMap<>();

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
    if (shouldEnable()) {
      try {
        initKeys();
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
    clearHost(true);
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
   * Sets the current host. If a different host is already set, notifies the previous host of the
   * takeover.
   *
   * @param player the player to set as host
   */
  public void setCurrentHost(Player player) {
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
  public void clearHost(boolean restoreEnvironment) {
    if (currentHost == null) return;
    pendingTakeovers.remove(currentHost.getUniqueId());
    if (restoreEnvironment) {
      restoreWeather();
      restoreTime();
      restoreKeepInventory();
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
    for (Player online : IceCream.instance.getServer().getOnlinePlayers()) {
      if (Boolean.TRUE.equals(
          online.getPersistentDataContainer().get(JOINED_KEY, PersistentDataType.BOOLEAN))) {
        online.getPersistentDataContainer().set(JOINED_KEY, PersistentDataType.BOOLEAN, false);
        online.sendMessage(
            MiniMessage.miniMessage()
                .deserialize(
                    "<gold>✨ The World Tour has concluded. Thank you for participating!</gold>"));
      }
    }
  }

  /** Removes the Glowing effect from whoever currently has it and clears the tracking reference. */
  public void clearGlowing() {
    if (currentGlowing != null) {
      currentGlowing.removePotionEffect(PotionEffectType.GLOWING);
    }
    currentGlowing = null;
  }

  /**
   * Transfers the Glowing effect to a new player, removing it from the previous holder.
   *
   * @param player the player to apply Glowing to
   */
  public void setGlowing(Player player) {
    clearGlowing();
    currentGlowing = player;
    player.addPotionEffect(
        new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
  }

  /** Saves the current weather for every loaded world before modification. */
  public void saveWeather() {
    if (!originalWeather.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      originalWeather.put(world.getName(), new WeatherSnapshot(world));
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
      }
    }
    originalWeather.clear();
  }

  /** Saves the current full time for every loaded world before modification. */
  public void saveTime() {
    if (!originalTimes.isEmpty()) return;
    for (World world : IceCream.instance.getServer().getWorlds()) {
      originalTimes.put(world.getName(), world.getFullTime());
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
      originalKeepInventory.put(world.getName(), world.getGameRuleValue(GameRules.KEEP_INVENTORY));
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
    }
    originalKeepInventory.clear();
  }

  /** Cancels every active disconnect failsafe task. */
  public void cancelAllDisconnectTasks() {
    for (BukkitTask task : disconnectTasks.values()) {
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
    BukkitTask task = disconnectTasks.remove(playerId);
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
  public void scheduleDisconnectTask(UUID playerId, BukkitTask task) {
    BukkitTask existing = disconnectTasks.put(playerId, task);
    if (existing != null && !existing.isCancelled()) {
      existing.cancel();
    }
  }
}
