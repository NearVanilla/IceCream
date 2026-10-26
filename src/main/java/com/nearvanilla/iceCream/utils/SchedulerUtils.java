package com.nearvanilla.iceCream.utils;

import com.nearvanilla.iceCream.IceCream;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Runs work on the thread allowed to do it, so the plugin works on both Paper and Folia.
 *
 * <p>Folia ticks each region of a world on its own thread. An entity may only be touched by the
 * thread of the region it is in, a block by the thread of its region, and world-wide state such as
 * time, weather and gamerules by the global region thread. Paper does all of this on the main
 * thread, which owns everything, so each method here runs its task straight away when called from
 * the main thread, just like calling it directly.
 *
 * @version 1.0
 * @since 2026-10-25
 */
public final class SchedulerUtils {
  private static final Key FOLIA_BRAND = Key.key("papermc", "folia");
  private static volatile Boolean folia;

  private SchedulerUtils() {
    // Utility class; prevent instantiation.
  }

  /**
   * @return true when the server runs Folia
   */
  public static boolean isFolia() {
    Boolean cached = folia;
    if (cached == null) {
      cached = ServerBuildInfo.buildInfo().isBrandCompatible(FOLIA_BRAND);
      folia = cached;
    }
    return cached;
  }

  /**
   * Runs a task on the global region, where world time, weather and gamerules may be changed. Runs
   * it now when already on the global region, otherwise on its next tick.
   *
   * @param task the task
   */
  public static void runGlobal(Runnable task) {
    if (Bukkit.isGlobalTickThread()) {
      task.run();
    } else {
      Bukkit.getGlobalRegionScheduler().execute(IceCream.instance, task);
    }
  }

  /**
   * Runs a task on the global region after a delay.
   *
   * @param task the task
   * @param delayTicks the delay in ticks; anything below 1 waits 1 tick
   * @return the scheduled task, to cancel it
   */
  public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
    return Bukkit.getGlobalRegionScheduler()
        .runDelayed(IceCream.instance, scheduled -> task.run(), Math.max(1, delayTicks));
  }

  /**
   * Runs a task on the global region repeatedly.
   *
   * @param task the task
   * @param delayTicks the delay before the first run in ticks; anything below 1 waits 1 tick
   * @param periodTicks the ticks between runs; anything below 1 runs every tick
   * @return the scheduled task, to cancel it
   */
  public static ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
    return Bukkit.getGlobalRegionScheduler()
        .runAtFixedRate(
            IceCream.instance,
            scheduled -> task.run(),
            Math.max(1, delayTicks),
            Math.max(1, periodTicks));
  }

  /**
   * Runs a task on the thread that owns an entity. Runs it now when the current thread owns the
   * entity, otherwise on the owner's next tick. The task is dropped if the entity is removed first.
   *
   * @param entity the entity the task touches
   * @param task the task
   */
  public static void runFor(Entity entity, Runnable task) {
    runFor(entity, task, null);
  }

  /**
   * Runs a task on the thread that owns an entity. Runs it now when the current thread owns the
   * entity, otherwise on the owner's next tick. If the entity is removed first, for example because
   * the player quit, {@code retired} runs instead.
   *
   * @param entity the entity the task touches
   * @param task the task
   * @param retired runs instead of the task once the entity is removed, or null
   */
  public static void runFor(Entity entity, Runnable task, Runnable retired) {
    if (Bukkit.isOwnedByCurrentRegion(entity)) {
      task.run();
      return;
    }
    boolean scheduled = entity.getScheduler().execute(IceCream.instance, task, retired, 1);
    // The entity was already removed, so its scheduler runs nothing, not even the retired task.
    if (!scheduled && retired != null) retired.run();
  }

  /**
   * Runs a task on the entity's next tick, on the thread that owns it, even when the current thread
   * owns it already. Used to act after the current event has finished. The task is dropped if the
   * entity is removed first.
   *
   * @param entity the entity the task touches
   * @param task the task
   */
  public static void runForNextTick(Entity entity, Runnable task) {
    entity.getScheduler().run(IceCream.instance, scheduled -> task.run(), null);
  }

  /**
   * Runs an action for each player on the thread that owns them. An action that throws is logged
   * and does not stop the others.
   *
   * <p>The returned future completes once every player has run the action or left. It may complete
   * on any of those threads, so work chained onto it must hop back with {@link #runFor} before
   * touching an entity.
   *
   * @param players the players
   * @param action the action, given each player
   * @return a future that completes once every player is done
   */
  public static CompletableFuture<Void> runForEach(
      Collection<? extends Player> players, Consumer<Player> action) {
    List<CompletableFuture<Void>> done = new ArrayList<>(players.size());
    for (Player player : players) {
      CompletableFuture<Void> ran = new CompletableFuture<>();
      done.add(ran);
      runFor(
          player,
          () -> {
            try {
              action.accept(player);
            } catch (RuntimeException e) {
              IceCream.logger.log(Level.WARNING, "Failed to update " + player.getName(), e);
            } finally {
              ran.complete(null);
            }
          },
          () -> ran.complete(null));
    }
    return CompletableFuture.allOf(done.toArray(CompletableFuture[]::new));
  }

  /**
   * Runs a task on the thread that owns a location, to read or change blocks there. Runs it now
   * when the current thread owns the location, otherwise on the owner's next tick.
   *
   * @param location the location the task touches
   * @param task the task
   */
  public static void runAt(Location location, Runnable task) {
    if (Bukkit.isOwnedByCurrentRegion(location)) {
      task.run();
    } else {
      Bukkit.getRegionScheduler().execute(IceCream.instance, location, task);
    }
  }

  /**
   * Removes an entity on the thread that owns it, which may be another region's after it moved.
   *
   * @param entity the entity to remove
   */
  public static void remove(Entity entity) {
    runFor(entity, entity::remove);
  }
}
