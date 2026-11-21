package com.nearvanilla.iceCream.modules.onePlayerSleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WakeButtonTest {

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  /** The first click event in the component tree, or null when nothing is clickable. */
  private static ClickEvent findClick(Component component) {
    if (component.clickEvent() != null) return component.clickEvent();
    for (Component child : component.children()) {
      ClickEvent click = findClick(child);
      if (click != null) return click;
    }
    return null;
  }

  /** The hover text of the first hover event in the component tree, or null when there is none. */
  private static Component findHover(Component component) {
    if (component.hoverEvent() != null) return (Component) component.hoverEvent().value();
    for (Component child : component.children()) {
      Component hover = findHover(child);
      if (hover != null) return hover;
    }
    return null;
  }

  /** The text component with exactly this content, with the style it inherits from its parents. */
  private static Component findText(Component component, String content) {
    return findText(component, content, Style.empty());
  }

  private static Component findText(Component component, String content, Style inherited) {
    Style style = inherited.merge(component.style());
    if (component instanceof TextComponent text && text.content().equals(content)) {
      return text.style(style);
    }
    for (Component child : component.children()) {
      Component found = findText(child, content, style);
      if (found != null) return found;
    }
    return null;
  }

  private static WakeButton fromYaml(String yaml) throws InvalidConfigurationException {
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(yaml);
    return WakeButton.fromConfig(config);
  }

  @Test
  void enabledAnnouncementOffersButtonForTheToken() {
    Component message = new WakeButton(true, "").sleepAnnouncement("Steve", "abc-123");

    assertEquals("Steve has gone to sleep [Wake up]", plain(message));
    ClickEvent click = findClick(message);
    assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
    assertEquals(
        "/oneplayersleep wake abc-123", ((ClickEvent.Payload.Text) click.payload()).value());
  }

  @Test
  void announcementDoesNotParsePlayerNameAsMiniMessage() {
    Component message = new WakeButton(true, "").sleepAnnouncement("<red>Steve", "abc-123");

    assertEquals("<red>Steve has gone to sleep [Wake up]", plain(message));
  }

  @Test
  void enabledButtonIsRedWithTheWakeHint() {
    Component message = new WakeButton(true, "").sleepAnnouncement("Steve", "abc-123");

    Component button = findText(message, "[Wake up]");
    assertEquals(NamedTextColor.RED, button.color());
    assertFalse(button.hasDecoration(TextDecoration.STRIKETHROUGH));
    assertEquals("Wake all sleepers and keep the night.", plain(findHover(message)));
  }

  @Test
  void disabledAnnouncementStrikesThroughTheButton() {
    Component message =
        new WakeButton(false, "Some features are disabled as Season 8 has just begun.")
            .sleepAnnouncement("Steve", "abc-123");

    assertEquals("Steve has gone to sleep [Wake up]", plain(message));
    Component button = findText(message, "[Wake up]");
    assertEquals(NamedTextColor.GRAY, button.color());
    assertTrue(button.hasDecoration(TextDecoration.STRIKETHROUGH));
  }

  @Test
  void disabledButtonShowsTheReasonOnHover() {
    Component message =
        new WakeButton(false, "Some features are disabled as Season 8 has just begun.")
            .sleepAnnouncement("Steve", "abc-123");

    assertEquals(
        "Some features are disabled as Season 8 has just begun.", plain(findHover(message)));
  }

  @Test
  void disabledButtonHoverFallsBackWhenNoReasonIsSet() {
    Component message = new WakeButton(false, "").sleepAnnouncement("Steve", "abc-123");

    assertEquals("The wake up button is turned off.", plain(findHover(message)));
  }

  @Test
  void disabledButtonStillRunsTheWakeCommandForTheDisabledReply() {
    Component message = new WakeButton(false, "").sleepAnnouncement("Steve", "abc-123");

    ClickEvent click = findClick(message);
    assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
    assertEquals(
        "/oneplayersleep wake abc-123", ((ClickEvent.Payload.Text) click.payload()).value());
  }

  @Test
  void reasonSupportsMiniMessageFormatting() {
    Component message = new WakeButton(false, "<red>Not yet</red>").disabledMessage();

    assertEquals("Not yet", plain(message));
  }

  @Test
  void disabledMessageIsTheReasonInGray() {
    Component message = new WakeButton(false, "Season 8 has just begun.").disabledMessage();

    assertEquals("Season 8 has just begun.", plain(message));
    assertEquals(NamedTextColor.GRAY, message.color());
  }

  @Test
  void disabledMessageFallsBackWhenNoReasonIsSet() {
    Component message = new WakeButton(false, "  ").disabledMessage();

    assertEquals("The wake up button is turned off.", plain(message));
  }

  @Test
  void readsSettingsFromConfig() throws InvalidConfigurationException {
    WakeButton button =
        fromYaml(
            """
            modules:
              oneplayersleep:
                wake-button:
                  enabled: false
                  disabled-reason: 'Season 8 has just begun.'
            """);

    assertFalse(button.enabled());
    assertEquals("Season 8 has just begun.", plain(button.disabledMessage()));
  }

  @Test
  void buttonIsEnabledWhenConfigLeavesItOut() throws InvalidConfigurationException {
    WakeButton button = fromYaml("modules:\n  oneplayersleep:\n    enabled: true\n");

    assertTrue(button.enabled());
  }
}
