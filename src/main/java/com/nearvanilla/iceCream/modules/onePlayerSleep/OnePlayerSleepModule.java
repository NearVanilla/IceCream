package com.nearvanilla.iceCream.modules.onePlayerSleep;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.onePlayerSleep.commands.WakeUpCommand;
import com.nearvanilla.iceCream.modules.onePlayerSleep.events.OnePlayerSleepListener;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.ClockTimeSkipEvent;

/**
 * Accelerates occupied night clocks while keeping wake requests scoped to individual bed entries.
 *
 * <p>World time may only change on the global region, so the sessions and the heartbeat live there.
 * Bed events arrive on each player's own region and hand their work over; only the pending entries
 * and the cooldown are shared between threads.
 */
public class OnePlayerSleepModule implements Module {
  private static final long DAY_TICKS = 24000L;
  private static final long COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final Component INACTIVE_REQUEST =
      Component.text("That sleep request is no longer active.", NamedTextColor.GRAY);

  private final Map<UUID, UUID> pendingEntries = new ConcurrentHashMap<>();
  private final Map<UUID, WorldSession> sessions = new HashMap<>();
  private ScheduledTask heartbeat;
  private OnePlayerSleepListener listener;
  private volatile boolean enabled;
  private double multiplier;
  private boolean ownTimeWrite;

  /** When the keep-the-night cooldown ends; a time already passed means there is none. */
  private volatile long cooldownEndsNanos = System.nanoTime();

  private static final class WorldSession {
    private final Map<UUID, String> sleepers = new HashMap<>();
    private double fractionalExtra;
    private long baselineFullTime;
    private long sampledFullTime;
    private long targetFullTime;
    private boolean initialized;
    private boolean rebase;
    private boolean baselinedThisTick;
    private boolean ending;
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.oneplayersleep.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new WakeUpCommand(this));
  }

  @Override
  public void registerEvents() {
    listener = new OnePlayerSleepListener(this);
    Bukkit.getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("One Player Sleep module is disabled.");
      return;
    }
    Object configuredMultiplier = IceCream.config.get("modules.oneplayersleep.multiplier", 10.0);
    if (!(configuredMultiplier instanceof Number number)
        || !Double.isFinite(number.doubleValue())
        || number.doubleValue() < 1.0) {
      IceCream.logger.severe(
          "Invalid modules.oneplayersleep.multiplier: expected a finite number >= 1.0; One Player Sleep module is disabled.");
      return;
    }
    multiplier = number.doubleValue();
    try {
      registerCommands();
      registerEvents();
      heartbeat = SchedulerUtils.runGlobalTimer(this::tick, 1L, 1L);
      enabled = true;
      IceCream.logger.info("One Player Sleep module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register One Player Sleep module.", exception);
      unregister();
    }
  }

  public boolean isCoolingDown() {
    return System.nanoTime() - cooldownEndsNanos < 0;
  }

  public void queueSleepCheck(Player player) {
    if (enabled) pendingEntries.put(player.getUniqueId(), player.getWorld().getUID());
  }

  public void forgetPlayer(UUID playerId) {
    pendingEntries.remove(playerId);
    SchedulerUtils.runGlobal(
        () ->
            sessions
                .values()
                .removeIf(
                    session -> {
                      session.sleepers.remove(playerId);
                      return session.sleepers.isEmpty();
                    }));
  }

  public void forgetWorld(UUID worldId) {
    pendingEntries.values().removeIf(worldId::equals);
    SchedulerUtils.runGlobal(() -> sessions.remove(worldId));
  }

  public void observeTimeChange(ClockTimeSkipEvent event) {
    ClockTimeSkipEvent.SkipReason reason = event.getSkipReason();
    // Inline when the event fires on the global region, as time writes do, so the guard holds.
    SchedulerUtils.runGlobal(
        () -> {
          if (ownTimeWrite || !enabled) return;
          if (reason == ClockTimeSkipEvent.SkipReason.CUSTOM
              || reason == ClockTimeSkipEvent.SkipReason.COMMAND) {
            for (WorldSession session : sessions.values()) {
              session.rebase = true;
              session.fractionalExtra = 0;
            }
          }
        });
  }

  public boolean shouldBlockNightSkip(World world) {
    if (!enabled) return false;
    if (isCoolingDown()) return true;
    if (!isEligibleNight(world)) return false;
    for (var entry : pendingEntries.entrySet()) {
      if (!entry.getValue().equals(world.getUID())) continue;
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null
          && player.isOnline()
          && !player.isDead()
          && player.getWorld().getUID().equals(world.getUID())) return true;
    }
    // Vanilla can reach its threshold before the confirmation heartbeat runs.
    for (Player player : world.getPlayers()) {
      if (isValidSleeper(player, world.getUID())) return true;
    }
    return false;
  }

  private static boolean isEligibleNight(World world) {
    return world.getEnvironment() == World.Environment.NORMAL
        && Math.floorMod(world.getFullTime(), DAY_TICKS) >= 12000L
        && Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ADVANCE_TIME));
  }

  private static boolean isValidSleeper(Player player, UUID worldId) {
    return player != null
        && player.isOnline()
        && !player.isDead()
        && player.isSleeping()
        && player.getWorld().getUID().equals(worldId);
  }

  private void confirmEntries() {
    if (pendingEntries.isEmpty()) return;
    var entries = new ArrayList<>(pendingEntries.entrySet());
    pendingEntries.clear();
    for (var entry : entries) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (!isValidSleeper(player, entry.getValue())) continue;
      if (isCoolingDown()) {
        wakePlayer(player);
        continue;
      }
      if (!enabled || !isEligibleNight(player.getWorld())) continue;
      WorldSession session =
          sessions.computeIfAbsent(entry.getValue(), ignored -> new WorldSession());
      if (session.ending) {
        wakePlayer(player);
        continue;
      }
      if (session.sleepers.containsKey(entry.getKey())) continue;
      String token = UUID.randomUUID().toString();
      session.sleepers.put(entry.getKey(), token);
      Bukkit.broadcast(
          Component.text(player.getName() + " has gone to sleep", NamedTextColor.YELLOW)
              .append(Component.space())
              .append(
                  Component.text("[Wake up]", NamedTextColor.RED)
                      .clickEvent(ClickEvent.runCommand("/oneplayersleep wake " + token))
                      .hoverEvent(Component.text("Wake all sleepers and keep the night."))));
    }
  }

  private void tick() {
    if (!enabled) return;
    confirmEntries();
    if (sessions.isEmpty()) return;
    // Time and wake APIs fire synchronous events. A snapshot survives their state mutations.
    var active = new ArrayList<>(sessions.entrySet());
    for (var entry : active) {
      UUID worldId = entry.getKey();
      WorldSession session = entry.getValue();
      if (sessions.get(worldId) != session) continue;
      session.sleepers.keySet().removeIf(id -> !isValidSleeper(Bukkit.getPlayer(id), worldId));
      World world = Bukkit.getWorld(worldId);
      if (world == null || session.sleepers.isEmpty()) {
        sessions.remove(worldId);
        continue;
      }
      if (session.ending || !isEligibleNight(world)) {
        endSession(worldId, session);
        continue;
      }
      session.sampledFullTime = world.getFullTime();
      session.targetFullTime = session.sampledFullTime;
      long naturalDelta = session.sampledFullTime - session.baselineFullTime;
      session.baselinedThisTick = !session.initialized || session.rebase || naturalDelta < 0;
      if (session.baselinedThisTick) {
        session.initialized = true;
        session.rebase = false;
        session.fractionalExtra = 0;
        continue;
      }
      if (naturalDelta == 0) continue;
      long remaining = DAY_TICKS - Math.floorMod(session.sampledFullTime, DAY_TICKS);
      double budget =
          Math.min((double) remaining, session.fractionalExtra + (multiplier - 1.0) * naturalDelta);
      long extra = (long) Math.floor(budget);
      session.fractionalExtra = budget - extra;
      session.targetFullTime = session.sampledFullTime + extra;
    }

    // All targets use pre-write samples, so shared clocks receive extra time only once.
    for (var entry : active) {
      UUID worldId = entry.getKey();
      WorldSession session = entry.getValue();
      if (sessions.get(worldId) != session) continue;
      // A newly baselined shared clock must inherit the established rounding phase.
      // For independent clocks at the same time this only selects an initial fraction.
      if (session.baselinedThisTick) {
        for (var other : active) {
          WorldSession source = other.getValue();
          if (sessions.get(other.getKey()) == source
              && !source.baselinedThisTick
              && source.sampledFullTime == session.sampledFullTime) {
            session.fractionalExtra = source.fractionalExtra;
            break;
          }
        }
      }
      World world = Bukkit.getWorld(worldId);
      if (world == null) {
        sessions.remove(worldId);
        continue;
      }
      long before = world.getFullTime();
      if (session.targetFullTime <= before) continue;
      try {
        ownTimeWrite = true;
        world.setFullTime(session.targetFullTime);
      } catch (IllegalArgumentException exception) {
        IceCream.logger.log(
            Level.WARNING,
            "Sleep acceleration failed in world " + world.getName() + ".",
            exception);
        endSession(worldId, session);
        continue;
      } finally {
        ownTimeWrite = false;
      }
      if (world.getFullTime() <= before) {
        IceCream.logger.warning(
            "Sleep acceleration stopped in world "
                + world.getName()
                + ": time advancement was prevented.");
        endSession(worldId, session);
      }
    }

    for (var entry : active) {
      UUID worldId = entry.getKey();
      WorldSession session = entry.getValue();
      if (sessions.get(worldId) != session) continue;
      World world = Bukkit.getWorld(worldId);
      if (world == null) {
        sessions.remove(worldId);
      } else {
        session.baselineFullTime = world.getFullTime();
        if (!isEligibleNight(world)) endSession(worldId, session);
      }
    }
  }

  private void endSession(UUID worldId, WorldSession session) {
    if (!sessions.remove(worldId, session)) return;
    var sleepers = new ArrayList<>(session.sleepers.keySet());
    session.sleepers.clear();
    for (UUID id : sleepers) {
      Player player = Bukkit.getPlayer(id);
      if (isValidSleeper(player, worldId)) wakePlayer(player);
    }
  }

  public void requestWakeUp(Player clicker, String token) {
    SchedulerUtils.runGlobal(() -> wakeUpOnGlobalRegion(clicker, token));
  }

  private void wakeUpOnGlobalRegion(Player clicker, String token) {
    if (!enabled || isCoolingDown() || !clicker.isOnline()) {
      clicker.sendMessage(INACTIVE_REQUEST);
      return;
    }
    boolean valid = false;
    var iterator = sessions.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      World world = Bukkit.getWorld(entry.getKey());
      WorldSession session = entry.getValue();
      session
          .sleepers
          .keySet()
          .removeIf(id -> !isValidSleeper(Bukkit.getPlayer(id), entry.getKey()));
      if (world == null || session.sleepers.isEmpty()) {
        iterator.remove();
      } else if (!isEligibleNight(world)) {
        // Reject the token now, but retain ownership for heartbeat wake-up cleanup.
        session.ending = true;
      } else if (!session.ending && session.sleepers.containsValue(token)) {
        valid = true;
      }
    }
    if (!valid) {
      clicker.sendMessage(INACTIVE_REQUEST);
      return;
    }
    cooldownEndsNanos = System.nanoTime() + COOLDOWN_NANOS;
    sessions.clear();
    pendingEntries.clear();
    Bukkit.broadcast(
        Component.text(clicker.getName() + " wants to keep the night", NamedTextColor.YELLOW));
    for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) wakePlayer(player);
  }

  /** Wakes a player on their own region, as Folia's own night skip does. */
  private static void wakePlayer(Player player) {
    SchedulerUtils.runFor(
        player,
        () -> {
          if (!player.isSleeping()) return;
          player.wakeup(false);
          if (player.isSleeping()) {
            IceCream.logger.warning(
                "Could not wake " + player.getName() + ": another plugin prevented bed leaving.");
          }
        });
  }

  public void unregister() {
    enabled = false;
    if (heartbeat != null) {
      heartbeat.cancel();
      heartbeat = null;
    }
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    var sleepers = new HashSet<UUID>();
    for (WorldSession session : sessions.values()) sleepers.addAll(session.sleepers.keySet());
    sessions.clear();
    pendingEntries.clear();
    cooldownEndsNanos = System.nanoTime();
    // On Folia this runs after the region threads stopped, and players are disconnected next,
    // which wakes them anyway.
    if (SchedulerUtils.isFolia()) return;
    for (UUID id : sleepers) {
      Player player = Bukkit.getPlayer(id);
      if (player != null && player.isOnline()) wakePlayer(player);
    }
  }
}
