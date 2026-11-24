package com.nearvanilla.iceCream.modules.commandTracker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandEntry;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogPage;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandPosition;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandSource;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandUse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CommandLogFormatterTest {
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final CommandLogFilters NO_FILTERS =
      new CommandLogFilters(null, null, null, null, null);

  private final CommandLogFormatter formatter =
      new CommandLogFormatter(
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
          ZoneId.of("UTC"),
          Map.of("world", "minecraft:overworld")::get);

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static CommandEntry player(String world, String line, boolean cancelled) {
    return new CommandEntry(
        1,
        new CommandUse(
            NOW.minus(Duration.ofHours(3)).toEpochMilli(),
            CommandSource.PLAYER,
            ALICE,
            "Alice",
            "tp",
            line,
            new CommandPosition(world, 10.5, 64, -20.25),
            cancelled));
  }

  /** Finds the first click event in a component tree. */
  private static ClickEvent click(Component component) {
    if (component.clickEvent() != null) {
      return component.clickEvent();
    }
    for (Component child : component.children()) {
      ClickEvent found = click(child);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** Finds the hover text of the first hover event in a component tree. */
  private static Component hover(Component component) {
    if (component.hoverEvent() != null) {
      return (Component) component.hoverEvent().value();
    }
    for (Component child : component.children()) {
      Component found = hover(child);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  @Test
  void saysWhenNothingWasFound() {
    List<Component> lines =
        formatter.format(new CommandLogPage(List.of(), 0, 1, 0), NO_FILTERS, NOW);

    assertEquals(
        List.of("No commands found."), lines.stream().map(CommandLogFormatterTest::plain).toList());
  }

  @Test
  void showsAHeaderAndOneLinePerCommand() {
    CommandLogPage page =
        new CommandLogPage(List.of(player("world", "/tp <b>Bob", false)), 0, 1, 1);

    List<String> lines =
        formatter.format(page, NO_FILTERS, NOW).stream()
            .map(CommandLogFormatterTest::plain)
            .toList();

    assertEquals(
        List.of("Command log page 1 of 1, 1 found", "2026-11-24 09:00 Alice /tp <b>Bob"), lines);
  }

  @Test
  void showsTheFiltersUsed() {
    CommandLogPage page = new CommandLogPage(List.of(player("world", "/tp Bob", false)), 0, 1, 1);
    CommandLogFilters filters = new CommandLogFilters("Alice", null, "7d", null, null);

    List<Component> lines = formatter.format(page, filters, NOW);

    assertEquals("Filters: --player Alice --since 7d", plain(lines.get(1)));
  }

  @Test
  void marksCancelledCommands() {
    CommandLogPage page = new CommandLogPage(List.of(player("world", "/op Alice", true)), 0, 1, 1);

    Component line = formatter.format(page, NO_FILTERS, NOW).get(1);

    assertTrue(plain(line).endsWith("/op Alice (cancelled)"));
    assertTrue(plain(hover(line)).contains("Cancelled by a plugin"));
  }

  @Test
  void entryDetailsAndClickTeleportThere() {
    CommandLogPage page = new CommandLogPage(List.of(player("world", "/tp Bob", false)), 0, 1, 1);

    Component line = formatter.format(page, NO_FILTERS, NOW).get(1);

    String details = plain(hover(line));
    assertTrue(details.contains("Ran by Alice (player)"), details);
    assertTrue(details.contains("UUID " + ALICE), details);
    assertTrue(details.contains("At 2026-11-24T09:00:00Z (3h ago)"), details);
    assertTrue(details.contains("In world at 10.5, 64.0, -20.3"), details);
    assertTrue(details.contains("Click to teleport there"), details);
    ClickEvent click = click(line);
    assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
    assertEquals(
        "/execute in minecraft:overworld run tp @s 10.50 64.00 -20.25",
        ((ClickEvent.Payload.Text) click.payload()).value());
  }

  @Test
  void noTeleportForAnUnloadedWorldOrTheConsole() {
    CommandEntry console =
        new CommandEntry(
            2,
            new CommandUse(
                NOW.toEpochMilli(),
                CommandSource.CONSOLE,
                null,
                "CONSOLE",
                "say",
                "/say hi",
                null,
                false));
    CommandLogPage page =
        new CommandLogPage(List.of(player("gone", "/tp Bob", false), console), 0, 1, 2);

    List<Component> lines = formatter.format(page, NO_FILTERS, NOW);

    assertNull(click(lines.get(1)));
    assertNull(click(lines.get(2)));
    String consoleDetails = plain(hover(lines.get(2)));
    assertTrue(consoleDetails.contains("Ran by CONSOLE (console)"), consoleDetails);
    assertTrue(!consoleDetails.contains("UUID"), consoleDetails);
  }

  @Test
  void pageLinksRepeatTheFilters() {
    List<CommandEntry> entries = new ArrayList<>();
    entries.add(player("world", "/tp Bob", false));
    CommandLogFilters filters = new CommandLogFilters("Alice", null, null, null, "two words");

    List<Component> middle = formatter.format(new CommandLogPage(entries, 1, 3, 30), filters, NOW);
    Component footer = middle.get(middle.size() - 1);
    assertEquals("[Previous] | [Next]", plain(footer));

    List<String> commands = new ArrayList<>();
    collectClicks(footer, commands);
    assertEquals(
        List.of(
            "/commandlog --page 1 --player Alice --search \"two words\"",
            "/commandlog --page 3 --player Alice --search \"two words\""),
        commands);
  }

  @Test
  void firstAndLastPagesHaveNoLinkBeyondThem() {
    List<CommandEntry> entries = List.of(player("world", "/tp Bob", false));

    List<Component> first =
        formatter.format(new CommandLogPage(entries, 0, 2, 11), NO_FILTERS, NOW);
    List<String> firstLinks = new ArrayList<>();
    collectClicks(first.get(first.size() - 1), firstLinks);
    assertEquals(List.of("/commandlog --page 2"), firstLinks);

    List<Component> last = formatter.format(new CommandLogPage(entries, 1, 2, 11), NO_FILTERS, NOW);
    List<String> lastLinks = new ArrayList<>();
    collectClicks(last.get(last.size() - 1), lastLinks);
    assertEquals(List.of("/commandlog --page 1"), lastLinks);
  }

  @Test
  void singlePageHasNoFooter() {
    List<Component> lines =
        formatter.format(
            new CommandLogPage(List.of(player("world", "/tp Bob", false)), 0, 1, 1),
            NO_FILTERS,
            NOW);

    assertEquals(2, lines.size());
  }

  @Test
  void agoIsShortAndRounded() {
    assertEquals("0s", CommandLogFormatter.ago(Duration.ofSeconds(-5)));
    assertEquals("45s", CommandLogFormatter.ago(Duration.ofSeconds(45)));
    assertEquals("12m", CommandLogFormatter.ago(Duration.ofMinutes(12).plusSeconds(30)));
    assertEquals("2h", CommandLogFormatter.ago(Duration.ofHours(2)));
    assertEquals("2h 5m", CommandLogFormatter.ago(Duration.ofMinutes(125)));
    assertEquals("3d 4h", CommandLogFormatter.ago(Duration.ofHours(76)));
    assertEquals("3d", CommandLogFormatter.ago(Duration.ofDays(3)));
  }

  private static void collectClicks(Component component, List<String> commands) {
    if (component.clickEvent() != null) {
      assertNotNull(component.clickEvent().payload());
      commands.add(((ClickEvent.Payload.Text) component.clickEvent().payload()).value());
    }
    for (Component child : component.children()) {
      collectClicks(child, commands);
    }
  }
}
