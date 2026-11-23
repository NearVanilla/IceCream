package com.nearvanilla.iceCream.modules.onePlayerSleep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.Configuration;

/**
 * Whether sleep announcements offer a working [Wake up] button, and the reason players see while it
 * is turned off. The reason is a MiniMessage template.
 *
 * @param enabled whether players can click [Wake up] to cancel a sleep
 * @param disabledReason the text shown while the button is off, or blank for the default
 */
public record WakeButton(boolean enabled, String disabledReason) {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final String CONFIG = "modules.oneplayersleep.wake-button.";
  private static final String ANNOUNCEMENT =
      "<yellow><player> has gone to sleep</yellow> <wake><button></wake>";
  private static final String BUTTON = "<red>[Wake up]</red>";
  private static final String STRUCK_BUTTON = "<gray><st>[Wake up]</st></gray>";
  private static final Component WAKE_HINT =
      Component.text("Wake all sleepers and keep the night.");
  private static final String NO_REASON = "The wake up button is turned off.";

  /** Reads {@code modules.oneplayersleep.wake-button}; the button is on unless turned off. */
  public static WakeButton fromConfig(Configuration config) {
    return new WakeButton(
        config.getBoolean(CONFIG + "enabled", true),
        config.getString(CONFIG + "disabled-reason", ""));
  }

  /**
   * The broadcast for a player getting into bed, with the button for their sleep token. While the
   * button is off it is struck through and its tooltip shows the reason. Clicking it still runs the
   * wake command, which replies with {@link #disabledMessage()} and wakes nobody.
   */
  public Component sleepAnnouncement(String playerName, String token) {
    return MM.deserialize(
        ANNOUNCEMENT,
        Placeholder.unparsed("player", playerName),
        Placeholder.parsed("button", enabled ? BUTTON : STRUCK_BUTTON),
        Placeholder.styling(
            "wake",
            ClickEvent.runCommand("/oneplayersleep wake " + token),
            HoverEvent.showText(enabled ? WAKE_HINT : disabledMessage())));
  }

  /** The reply to a wake request while the button is off, also used as the button's tooltip. */
  public Component disabledMessage() {
    return MM.deserialize(hasReason() ? disabledReason : NO_REASON)
        .colorIfAbsent(NamedTextColor.GRAY);
  }

  private boolean hasReason() {
    return disabledReason != null && !disabledReason.isBlank();
  }
}
