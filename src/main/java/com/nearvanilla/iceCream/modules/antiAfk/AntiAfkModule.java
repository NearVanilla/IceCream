package com.nearvanilla.iceCream.modules.antiAfk;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.antiAfk.events.AntiAfkListener;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatInterceptor;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Kicks players who stay AFK. After a period without deliberate input the player is shown a code
 * and must type it in chat before the answer window runs out. The answer is hidden from other
 * players. Players with {@link #EXEMPT_PERMISSION}, and players in spectator mode, are never
 * checked.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public class AntiAfkModule implements Module {
  public static final String EXEMPT_PERMISSION = "icecream.modules.antiafk.exempt";
  private static final String CONFIG = "modules.antiafk.";
  private static final long REMIND_AT_SECONDS = 10;

  private record View(float yaw, float pitch) {}

  private final LongSupplier clock = () -> System.nanoTime() / 1_000_000L;
  private final AfkTracker tracker = new AfkTracker(clock);
  private final InteractionDeduper interactions = new InteractionDeduper();
  private final CaptchaGenerator generator = new CaptchaGenerator(new SecureRandom());
  private final CarbonChatInterceptor carbonChat = new CarbonChatInterceptor();
  // Main thread only.
  private final Map<UUID, View> viewAnchors = new HashMap<>();
  private final Set<UUID> reminded = new HashSet<>();
  // Read from the async chat thread.
  private volatile ChatGate chatGate;
  private AntiAfkPresenter presenter;
  private AntiAfkListener listener;
  private BukkitTask heartbeat;
  private volatile boolean enabled;
  private long timeoutMs;
  private long answerMs;
  private int codeLength;
  private double minRotationDegrees;
  private boolean announceKick;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.antiafk.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {}

  @Override
  public void registerEvents() {
    listener = new AntiAfkListener(this);
    Bukkit.getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Anti-AFK module is disabled.");
      return;
    }
    Double timeoutMinutes = readNumber("timeout-minutes", 300, 1, Double.MAX_VALUE);
    Double answerSeconds = readNumber("answer-seconds", 60, 15, 600);
    Double length = readNumber("code-length", 6, 4, 12);
    Double rotation = readNumber("min-rotation-degrees", 20, 1, 180);
    if (timeoutMinutes == null || answerSeconds == null || length == null || rotation == null) {
      return;
    }
    timeoutMs = (long) (timeoutMinutes * 60_000);
    answerMs = (long) (answerSeconds * 1_000);
    codeLength = length.intValue();
    minRotationDegrees = rotation;
    announceKick = IceCream.config.getBoolean(CONFIG + "announce-kick", true);
    try {
      presenter = new AntiAfkPresenter(readSound());
      chatGate =
          new ChatGate(
              tracker,
              clock,
              new ChatGate.Listener() {
                @Override
                public void onCorrect(UUID playerId) {
                  onMainThread(playerId, AntiAfkModule.this::answeredCorrectly);
                }

                @Override
                public void onWrong(UUID playerId) {
                  onMainThread(playerId, AntiAfkModule.this::answeredWrongly);
                }
              });
      registerCommands();
      registerEvents();
      carbonChat.init(this::shouldHideChat);
      for (Player player : Bukkit.getOnlinePlayers()) startTracking(player);
      heartbeat = Bukkit.getScheduler().runTaskTimer(IceCream.instance, this::tick, 20L, 20L);
      enabled = true;
      IceCream.logger.info("Anti-AFK module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Anti-AFK module.", exception);
      unregister();
    }
  }

  /** Reads a number from the module config, logging and returning null when it is invalid. */
  private static Double readNumber(String key, double fallback, double min, double max) {
    Object value = IceCream.config.get(CONFIG + key, fallback);
    if (!(value instanceof Number number)
        || !Double.isFinite(number.doubleValue())
        || number.doubleValue() < min
        || number.doubleValue() > max) {
      IceCream.logger.severe(
          "Invalid modules.antiafk."
              + key
              + ": expected a number from "
              + min
              + (max == Double.MAX_VALUE ? " upwards" : " to " + max)
              + "; Anti-AFK module is disabled.");
      return null;
    }
    return number.doubleValue();
  }

  private static Sound readSound() {
    String name = IceCream.config.getString(CONFIG + "sound", "");
    if (name == null || name.isBlank()) return null;
    try {
      return Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f);
    } catch (InvalidKeyException exception) {
      IceCream.logger.warning("Invalid modules.antiafk.sound '" + name + "'; no sound will play.");
      return null;
    }
  }

  public void startTracking(Player player) {
    UUID id = player.getUniqueId();
    tracker.start(id);
    viewAnchors.remove(id);
    reminded.remove(id);
    interactions.forget(id);
    if (chatGate != null) chatGate.forget(id);
  }

  public void stopTracking(Player player) {
    UUID id = player.getUniqueId();
    tracker.remove(id);
    viewAnchors.remove(id);
    reminded.remove(id);
    interactions.forget(id);
    if (chatGate != null) chatGate.forget(id);
  }

  public void markActive(Player player) {
    if (enabled) tracker.markActive(player.getUniqueId());
  }

  /** Counts an interaction as activity only when it differs from the player's previous one. */
  public void markInteraction(Player player, String fingerprint) {
    if (enabled && interactions.isNew(player.getUniqueId(), fingerprint)) markActive(player);
  }

  /**
   * Forgets where the player was looking, for when the server rather than the player turned them.
   * The next check takes the new view as its starting point without counting it as activity.
   */
  public void resetView(Player player) {
    viewAnchors.remove(player.getUniqueId());
  }

  /**
   * Decides whether a chat message should be hidden because it answers a captcha. Called from the
   * async chat thread.
   */
  public boolean shouldHideChat(Player player, Component message) {
    return shouldHideChat(
        player.getUniqueId(), PlainTextComponentSerializer.plainText().serialize(message));
  }

  private boolean shouldHideChat(UUID playerId, String text) {
    return enabled && chatGate.onChat(playerId, text) != ChatGate.Verdict.PASS;
  }

  private void onMainThread(UUID playerId, Consumer<Player> action) {
    if (!enabled) return;
    Bukkit.getScheduler()
        .runTask(
            IceCream.instance,
            () -> {
              Player player = Bukkit.getPlayer(playerId);
              if (enabled && player != null) action.accept(player);
            });
  }

  private void answeredCorrectly(Player player) {
    reminded.remove(player.getUniqueId());
    presenter.success(player);
  }

  private void answeredWrongly(Player player) {
    AfkTracker.Challenge challenge = tracker.challenge(player.getUniqueId());
    if (challenge != null) presenter.wrong(player, challenge.code());
  }

  private boolean isExempt(Player player) {
    return player.hasPermission(EXEMPT_PERMISSION)
        || (SpectatorModule.SPECTATOR_TOGGLE_KEY != null
            && SpectatorUtils.isInSpectatorMode(player));
  }

  private long secondsLeft(AfkTracker.Challenge challenge) {
    return Math.max(0, (challenge.deadlineMs() - clock.getAsLong() + 999) / 1_000);
  }

  private void tick() {
    if (!enabled) return;
    List<Player> toKick = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID id = player.getUniqueId();
      if (!tracker.isTracked(id)) startTracking(player);
      if (isExempt(player)) {
        if (tracker.cancelChallenge(id)) presenter.clear(player);
        reminded.remove(id);
        continue;
      }
      checkView(player);
      if (tracker.challenge(id) != null) {
        if (tracker.tryExpire(id)) {
          toKick.add(player);
          continue;
        }
        // The answer may have arrived on the chat thread since the check above.
        AfkTracker.Challenge challenge = tracker.challenge(id);
        if (challenge == null) continue;
        long seconds = secondsLeft(challenge);
        presenter.countdown(player, challenge.code(), seconds);
        if (seconds <= REMIND_AT_SECONDS && reminded.add(id)) presenter.remind(player);
        continue;
      }
      if (!tracker.isDue(id, timeoutMs)) continue;
      if (player.isDead()) {
        // The death screen hides the captcha, so wait for a respawn, but not forever.
        if (tracker.idleMs(id) >= timeoutMs + answerMs) toKick.add(player);
        continue;
      }
      AfkTracker.Challenge challenge = tracker.issue(id, generator.generate(codeLength), answerMs);
      if (challenge != null) presenter.issue(player, challenge.code(), secondsLeft(challenge));
    }
    for (Player player : toKick) kick(player);
  }

  /** Counts a large enough camera turn as activity. Small turns never move the anchor. */
  private void checkView(Player player) {
    // Vehicles turn their riders (boats, steered mounts) or not (minecarts on bends), so camera
    // turns can't be told apart from the ride. Steering still counts through PlayerInputEvent.
    if (player.getVehicle() != null) {
      viewAnchors.remove(player.getUniqueId());
      return;
    }
    Location location = player.getLocation();
    View view = new View(location.getYaw(), location.getPitch());
    View anchor = viewAnchors.putIfAbsent(player.getUniqueId(), view);
    if (anchor != null
        && RotationGate.exceeded(
            anchor.yaw(), anchor.pitch(), view.yaw(), view.pitch(), minRotationDegrees)) {
      viewAnchors.put(player.getUniqueId(), view);
      tracker.markActive(player.getUniqueId());
    }
  }

  private void kick(Player player) {
    stopTracking(player);
    player.kick(presenter.kickReason(), PlayerKickEvent.Cause.IDLING);
    if (announceKick) Bukkit.broadcast(presenter.kickAnnouncement(player.getName()));
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
    carbonChat.cleanup();
    if (presenter != null) {
      for (UUID id : tracker.withOpenChallenge()) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) presenter.clear(player);
      }
    }
    tracker.clear();
    interactions.clear();
    viewAnchors.clear();
    reminded.clear();
    if (chatGate != null) chatGate.clear();
  }
}
