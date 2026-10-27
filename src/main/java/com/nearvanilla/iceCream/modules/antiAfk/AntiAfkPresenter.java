package com.nearvanilla.iceCream.modules.antiAfk;

import com.nearvanilla.iceCream.IceCream;
import java.time.Duration;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * Shows the captcha and its results to players. All text comes from MiniMessage templates in the
 * module config, with {@code <code>}, {@code <seconds>} and {@code <player>} placeholders.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class AntiAfkPresenter {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final String MESSAGES = "modules.antiafk.messages.";

  private final String title = message("title", "<red>Are you still there?</red>");
  private final String subtitle =
      message("subtitle", "<gray>Type <white><code></white> in chat</gray>");
  private final String prompt =
      message(
          "prompt",
          "<gold>AFK check:</gold> <gray>type <white><code></white> in chat within <seconds>"
              + " seconds. Only you can see it.</gray>");
  private final String actionBar =
      message(
          "actionbar",
          "<gray>Type <white><code></white> in chat <dark_gray>(<seconds>s)</dark_gray></gray>");
  private final String wrong =
      message("wrong", "<red>That is not the code. Type <white><code></white></red>");
  private final String success = message("success", "<green>Thanks, enjoy your game!</green>");
  private final String kickReason =
      message("kick-reason", "<red>You were kicked for being AFK.</red>");
  private final String kickAnnouncement =
      message("kick-announcement", "<gray><player> was kicked for being AFK.</gray>");
  private final Sound sound;

  /**
   * @param sound the sound played when a captcha appears, or null for none
   */
  public AntiAfkPresenter(Sound sound) {
    this.sound = sound;
  }

  private static String message(String key, String fallback) {
    return IceCream.config.getString(MESSAGES + key, fallback);
  }

  private static Component render(String template, String code, long seconds) {
    return MM.deserialize(
        template,
        Placeholder.unparsed("code", code),
        Placeholder.unparsed("seconds", Long.toString(seconds)));
  }

  /** Shows a new captcha: closes any open menu, then shows a title, a chat prompt and a sound. */
  public void issue(Player player, String code, long seconds) {
    player.closeInventory();
    player.showTitle(
        Title.title(
            render(title, code, seconds),
            render(subtitle, code, seconds),
            Title.Times.times(
                Duration.ofMillis(250), Duration.ofSeconds(seconds), Duration.ofMillis(500))));
    player.sendMessage(render(prompt, code, seconds));
    playSound(player);
    countdown(player, code, seconds);
  }

  /** Refreshes the action bar with the code and the seconds left. */
  public void countdown(Player player, String code, long seconds) {
    player.sendActionBar(render(actionBar, code, seconds));
  }

  public void remind(Player player) {
    playSound(player);
  }

  public void wrong(Player player, String code) {
    player.sendMessage(render(wrong, code, 0));
  }

  public void success(Player player) {
    clear(player);
    player.sendMessage(MM.deserialize(success));
  }

  /** Removes the title and action bar of a captcha. */
  public void clear(Player player) {
    player.clearTitle();
    player.sendActionBar(Component.empty());
  }

  public Component kickReason() {
    return MM.deserialize(kickReason);
  }

  public Component kickAnnouncement(String playerName) {
    return MM.deserialize(kickAnnouncement, Placeholder.unparsed("player", playerName));
  }

  private void playSound(Player player) {
    if (sound != null) player.playSound(sound, Sound.Emitter.self());
  }
}
