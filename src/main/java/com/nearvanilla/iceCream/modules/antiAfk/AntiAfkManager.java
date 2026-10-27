package com.nearvanilla.iceCream.modules.antiAfk;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs the anti-AFK check: tracks activity, shows captchas when players go idle, handles answers
 * typed in chat and kicks players whose time runs out. A heartbeat checks every online player once
 * a second.
 *
 * <p>Unless stated otherwise, methods must be called on the main thread.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 * @see AntiAfkModule
 */
public final class AntiAfkManager {
  private static final long REMIND_AT_SECONDS = 10;

  private final LongSupplier clock = () -> System.nanoTime() / 1_000_000L;
  private final AfkTracker tracker = new AfkTracker(clock);
  private final InteractionDeduper interactions = new InteractionDeduper();
  private final CaptchaGenerator generator = new CaptchaGenerator(new SecureRandom());
  private final Set<UUID> reminded = new HashSet<>();
  private final AntiAfkConfig config;
  private final AntiAfkPresenter presenter;
  private final ViewTracker views;
  private final ChatGate chatGate;
  private BukkitTask heartbeat;
  // Read from the async chat thread.
  private volatile boolean running;

  public AntiAfkManager(AntiAfkConfig config) {
    this.config = config;
    this.presenter = new AntiAfkPresenter(config.sound());
    this.views = new ViewTracker(config.minRotationDegrees());
    this.chatGate =
        new ChatGate(
            tracker,
            clock,
            new ChatGate.Listener() {
              @Override
              public void onCorrect(UUID playerId) {
                onMainThread(playerId, AntiAfkManager.this::answeredCorrectly);
              }

              @Override
              public void onWrong(UUID playerId) {
                onMainThread(playerId, AntiAfkManager.this::answeredWrongly);
              }
            });
  }

  /** Starts tracking everyone online and starts the heartbeat. */
  public void start() {
    if (running) return;
    for (Player player : Bukkit.getOnlinePlayers()) startTracking(player);
    heartbeat = Bukkit.getScheduler().runTaskTimer(IceCream.instance, this::tick, 20L, 20L);
    running = true;
  }

  /** Stops the heartbeat, removes any captcha on screen and forgets all players. */
  public void stop() {
    running = false;
    if (heartbeat != null) {
      heartbeat.cancel();
      heartbeat = null;
    }
    for (UUID id : tracker.withOpenChallenge()) {
      Player player = Bukkit.getPlayer(id);
      if (player != null) presenter.clear(player);
    }
    tracker.clear();
    interactions.clear();
    views.clear();
    reminded.clear();
    chatGate.clear();
  }

  public void startTracking(Player player) {
    UUID id = player.getUniqueId();
    forget(id);
    tracker.start(id);
  }

  public void stopTracking(Player player) {
    forget(player.getUniqueId());
  }

  private void forget(UUID id) {
    tracker.remove(id);
    views.reset(id);
    reminded.remove(id);
    interactions.forget(id);
    chatGate.forget(id);
  }

  public void markActive(Player player) {
    if (running) tracker.markActive(player.getUniqueId());
  }

  /** Counts an interaction as activity only when it differs from the player's previous one. */
  public void markInteraction(Player player, String fingerprint) {
    if (running && interactions.isNew(player.getUniqueId(), fingerprint)) markActive(player);
  }

  /**
   * Forgets where the player was looking, for when the server rather than the player turned them.
   */
  public void resetView(Player player) {
    views.reset(player.getUniqueId());
  }

  /**
   * Marks the player as AFK straight away, so they are shown a captcha now instead of after the
   * timeout. A player on the death screen gets the captcha's time to respawn before being kicked.
   *
   * @return false when the player is exempt or already has a captcha open
   */
  public boolean setAfk(Player player) {
    if (!running || isExempt(player)) return false;
    UUID id = player.getUniqueId();
    if (!tracker.isTracked(id)) startTracking(player);
    if (!tracker.markIdle(id, config.timeoutMs())) return false;
    if (!player.isDead()) issueCaptcha(player);
    return true;
  }

  /**
   * Decides whether a chat message should be hidden because it answers a captcha. Safe to call from
   * the async chat thread.
   */
  public boolean shouldHideChat(Player player, Component message) {
    return shouldHideChat(
        player.getUniqueId(), PlainTextComponentSerializer.plainText().serialize(message));
  }

  /**
   * Decides whether a chat message should be hidden because it answers a captcha. Safe to call from
   * the async chat thread.
   */
  public boolean shouldHideChat(UUID playerId, String text) {
    return running && chatGate.onChat(playerId, text) != ChatGate.Verdict.PASS;
  }

  private void onMainThread(UUID playerId, Consumer<Player> action) {
    if (!running) return;
    Bukkit.getScheduler()
        .runTask(
            IceCream.instance,
            () -> {
              Player player = Bukkit.getPlayer(playerId);
              if (running && player != null) action.accept(player);
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

  private static boolean isExempt(Player player) {
    return player.hasPermission(AntiAfkModule.EXEMPT_PERMISSION)
        || (SpectatorModule.SPECTATOR_TOGGLE_KEY != null
            && SpectatorUtils.isInSpectatorMode(player));
  }

  private long secondsLeft(AfkTracker.Challenge challenge) {
    return Math.max(0, (challenge.deadlineMs() - clock.getAsLong() + 999) / 1_000);
  }

  private void issueCaptcha(Player player) {
    AfkTracker.Challenge challenge =
        tracker.issue(
            player.getUniqueId(), generator.generate(config.codeLength()), config.answerMs());
    if (challenge != null) presenter.issue(player, challenge.code(), secondsLeft(challenge));
  }

  private void tick() {
    if (!running) return;
    List<Player> toKick = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (shouldKick(player)) toKick.add(player);
    }
    for (Player player : toKick) kick(player);
  }

  /** Runs one heartbeat check for a player and returns true when they should be kicked. */
  private boolean shouldKick(Player player) {
    UUID id = player.getUniqueId();
    if (!tracker.isTracked(id)) startTracking(player);
    if (isExempt(player)) {
      if (tracker.cancelChallenge(id)) presenter.clear(player);
      reminded.remove(id);
      return false;
    }
    if (views.turned(player)) tracker.markActive(id);
    if (tracker.challenge(id) != null) {
      if (tracker.tryExpire(id)) return true;
      // The answer may have arrived on the chat thread since the check above.
      AfkTracker.Challenge challenge = tracker.challenge(id);
      if (challenge == null) return false;
      long seconds = secondsLeft(challenge);
      presenter.countdown(player, challenge.code(), seconds);
      if (seconds <= REMIND_AT_SECONDS && reminded.add(id)) presenter.remind(player);
      return false;
    }
    if (!tracker.isDue(id, config.timeoutMs())) return false;
    if (player.isDead()) {
      // The death screen hides the captcha, so wait for a respawn, but not forever.
      return tracker.idleMs(id) >= config.timeoutMs() + config.answerMs();
    }
    issueCaptcha(player);
    return false;
  }

  private void kick(Player player) {
    stopTracking(player);
    player.kick(presenter.kickReason(), PlayerKickEvent.Cause.IDLING);
    if (config.announceKick()) Bukkit.broadcast(presenter.kickAnnouncement(player.getName()));
  }
}
