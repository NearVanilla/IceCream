package com.nearvanilla.iceCream.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends action bar messages for modules and remembers when each player last got one, so features
 * that keep the action bar filled, such as the Coordinates HUD, can step aside while a message
 * shows.
 *
 * <p>Modules showing a message in the action bar call {@link #send}. Repeating messages, like a
 * countdown, call it on every refresh, which keeps the claim fresh. A module that is done with the
 * action bar calls {@link #clear}, which empties it and gives it back straight away. Messages the
 * game shows by itself are recorded with {@link #claim}.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class ActionBarUtils {
  private static final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();
  private static LongSupplier clock = () -> System.nanoTime() / 1_000_000L;

  private ActionBarUtils() {}

  /**
   * Shows a message in a player's action bar and records that the action bar is in use.
   *
   * @param player the player
   * @param message the message
   */
  public static void send(Player player, Component message) {
    player.sendActionBar(message);
    claim(player);
  }

  /**
   * Records that a player's action bar is in use by a message that was not sent with {@link #send},
   * such as one the game shows by itself, so features waiting on the action bar step aside for it
   * too.
   *
   * @param player the player
   */
  public static void claim(Player player) {
    lastSent.put(player.getUniqueId(), clock.getAsLong());
  }

  /**
   * Empties a player's action bar and gives it back, so features waiting on it resume at once.
   *
   * @param player the player
   */
  public static void clear(Player player) {
    player.sendActionBar(Component.empty());
    release(player);
  }

  /**
   * Gives a player's action bar back without changing what it shows.
   *
   * @param player the player
   */
  public static void release(Player player) {
    lastSent.remove(player.getUniqueId());
  }

  /**
   * Tells whether a module sent a player an action bar message within the given time.
   *
   * @param player the player
   * @param millis how far back to look, in milliseconds
   * @return whether a message was sent that recently and not cleared since
   */
  public static boolean sentWithin(Player player, long millis) {
    Long sentAt = lastSent.get(player.getUniqueId());
    if (sentAt == null) return false;
    if (clock.getAsLong() - sentAt < millis) return true;
    lastSent.remove(player.getUniqueId(), sentAt);
    return false;
  }

  /**
   * Replaces the clock, in milliseconds, for tests. Also forgets every recorded message.
   *
   * @param millis the clock to use
   */
  public static void useClock(LongSupplier millis) {
    clock = millis;
    lastSent.clear();
  }

  /** Restores the system clock and forgets every recorded message. */
  public static void resetClock() {
    useClock(() -> System.nanoTime() / 1_000_000L);
  }
}
