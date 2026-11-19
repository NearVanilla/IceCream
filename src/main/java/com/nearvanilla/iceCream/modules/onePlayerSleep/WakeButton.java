package com.nearvanilla.iceCream.modules.onePlayerSleep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.Configuration;

/**
 * Whether sleep announcements offer the [Wake up] button, and the reason players see while it is
 * turned off. The reason is a MiniMessage template.
 *
 * @param enabled whether players can click [Wake up] to cancel a sleep
 * @param disabledReason the text shown in place of the button while it is off, or blank for none
 */
public record WakeButton(boolean enabled, String disabledReason) {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final String CONFIG = "modules.oneplayersleep.wake-button.";
  private static final String GONE_TO_SLEEP = "<yellow><player> has gone to sleep</yellow>";
  private static final String WITH_BUTTON =
      GONE_TO_SLEEP
          + " <wake><hover:show_text:'Wake all sleepers and keep the night.'>"
          + "<red>[Wake up]</red></hover></wake>";
  private static final String WITH_REASON = GONE_TO_SLEEP + " <gray><reason></gray>";
  private static final String NO_REASON = "The wake up button is turned off.";

  /** Reads {@code modules.oneplayersleep.wake-button}; the button is on unless turned off. */
  public static WakeButton fromConfig(Configuration config) {
    return new WakeButton(
        config.getBoolean(CONFIG + "enabled", true),
        config.getString(CONFIG + "disabled-reason", ""));
  }

  /** The broadcast for a player getting into bed, with the button for their sleep token. */
  public Component sleepAnnouncement(String playerName, String token) {
    if (enabled) {
      return MM.deserialize(
          WITH_BUTTON,
          Placeholder.unparsed("player", playerName),
          Placeholder.styling("wake", ClickEvent.runCommand("/oneplayersleep wake " + token)));
    }
    if (!hasReason()) {
      return MM.deserialize(GONE_TO_SLEEP, Placeholder.unparsed("player", playerName));
    }
    return MM.deserialize(
        WITH_REASON,
        Placeholder.unparsed("player", playerName),
        Placeholder.component("reason", MM.deserialize(disabledReason)));
  }

  /** The reply to a wake request while the button is off. */
  public Component disabledMessage() {
    return MM.deserialize(hasReason() ? disabledReason : NO_REASON)
        .colorIfAbsent(NamedTextColor.GRAY);
  }

  private boolean hasReason() {
    return disabledReason != null && !disabledReason.isBlank();
  }
}
