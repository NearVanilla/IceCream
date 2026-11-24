package com.nearvanilla.iceCream.modules.commandTracker.commands;

import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandEntry;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogPage;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandPosition;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandUse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Turns a page of the command log into chat lines. */
public final class CommandLogFormatter {
  private static final MiniMessage MM = MiniMessage.miniMessage();

  private static final String HEADER =
      "<gold>Command log</gold> <gray>page <page> of <pages>, <total> found</gray>";
  private static final String FILTERS = "<dark_gray>Filters: <filters></dark_gray>";
  private static final String NONE_FOUND = "<gray>No commands found.</gray>";
  private static final String ENTRY =
      "<details><gray><time></gray> <white><name></white> <aqua><line></aqua><cancelled></details>";
  private static final String CANCELLED = " <red>(cancelled)</red>";
  private static final String FOOTER = "<previous> <dark_gray>|</dark_gray> <next>";
  private static final String PAGE_LINK = "<link><yellow><text></yellow></link>";
  private static final String NO_PAGE_LINK = "<dark_gray><text></dark_gray>";

  private final DateTimeFormatter dateFormat;
  private final ZoneId zone;
  private final Function<String, String> worldKeys;

  /**
   * Creates the formatter.
   *
   * @param dateFormat how times are shown
   * @param zone the zone times are shown in
   * @param worldKeys finds a loaded world's key, such as {@code minecraft:overworld}, by world
   *     name, returning null when the world is not loaded
   */
  public CommandLogFormatter(
      DateTimeFormatter dateFormat, ZoneId zone, Function<String, String> worldKeys) {
    this.dateFormat = dateFormat;
    this.zone = zone;
    this.worldKeys = worldKeys;
  }

  /**
   * Renders a page.
   *
   * @param page the page to show
   * @param filters the filters used, repeated in the page links
   * @param now the current time, for the "ago" in each entry's details
   * @return the chat lines, top to bottom
   */
  public List<Component> format(CommandLogPage page, CommandLogFilters filters, Instant now) {
    List<Component> lines = new ArrayList<>();
    if (page.totalEntries() == 0) {
      lines.add(MM.deserialize(NONE_FOUND));
      return lines;
    }
    lines.add(
        MM.deserialize(
            HEADER,
            Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
            Placeholder.unparsed("pages", String.valueOf(page.pageCount())),
            Placeholder.unparsed("total", String.valueOf(page.totalEntries()))));
    if (!filters.isEmpty()) {
      lines.add(MM.deserialize(FILTERS, Placeholder.unparsed("filters", filters.toFlags())));
    }
    for (CommandEntry entry : page.entries()) {
      lines.add(entry(entry.use(), now));
    }
    if (page.pageCount() > 1) {
      lines.add(
          MM.deserialize(
              FOOTER,
              Placeholder.component(
                  "previous", pageLink("[Previous]", page.page() > 0, page.page(), filters)),
              Placeholder.component(
                  "next",
                  pageLink(
                      "[Next]", page.page() + 1 < page.pageCount(), page.page() + 2, filters))));
    }
    return lines;
  }

  private Component entry(CommandUse use, Instant now) {
    Instant time = Instant.ofEpochMilli(use.executedAtMillis());
    String teleport = teleportCommand(use.position());
    HoverEvent<Component> hover = HoverEvent.showText(details(use, time, now, teleport != null));
    Tag details =
        teleport == null
            ? Tag.styling(hover)
            : Tag.styling(hover, ClickEvent.suggestCommand(teleport));
    return MM.deserialize(
        ENTRY,
        TagResolver.resolver("details", details),
        Placeholder.unparsed("time", dateFormat.format(time.atZone(zone))),
        Placeholder.unparsed("name", use.name()),
        Placeholder.unparsed("line", use.commandLine()),
        Placeholder.parsed("cancelled", use.cancelled() ? CANCELLED : ""));
  }

  private Component details(CommandUse use, Instant time, Instant now, boolean canTeleport) {
    List<String> lines = new ArrayList<>();
    lines.add("<gray>Ran by <white><name></white> (<source>)</gray>");
    if (use.playerId() != null) {
      lines.add("<gray>UUID <white><uuid></white></gray>");
    }
    lines.add("<gray>At <white><time></white> (<ago> ago)</gray>");
    if (use.position() != null) {
      lines.add("<gray>In <white><world></white> at <white><position></white></gray>");
    }
    if (use.cancelled()) {
      lines.add("<red>Cancelled by a plugin, so it may not have run</red>");
    }
    if (canTeleport) {
      lines.add("<yellow>Click to teleport there</yellow>");
    }
    CommandPosition position = use.position();
    return MM.deserialize(
        String.join("<newline>", lines),
        Placeholder.unparsed("name", use.name()),
        Placeholder.unparsed("source", use.source().name().toLowerCase(Locale.ROOT)),
        Placeholder.unparsed("uuid", String.valueOf(use.playerId())),
        Placeholder.unparsed(
            "time", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.atZone(zone).withNano(0))),
        Placeholder.unparsed("ago", ago(Duration.between(time, now))),
        Placeholder.unparsed("world", position == null ? "" : position.world()),
        Placeholder.unparsed(
            "position",
            position == null
                ? ""
                : String.format(
                    Locale.ROOT, "%.1f, %.1f, %.1f", position.x(), position.y(), position.z())));
  }

  /** A command that teleports the viewer to where a command ran, or null if it has no place. */
  private String teleportCommand(CommandPosition position) {
    if (position == null) {
      return null;
    }
    String worldKey = worldKeys.apply(position.world());
    if (worldKey == null) {
      return null;
    }
    return String.format(
        Locale.ROOT,
        "/execute in %s run tp @s %.2f %.2f %.2f",
        worldKey,
        position.x(),
        position.y(),
        position.z());
  }

  private static Component pageLink(
      String text, boolean available, int targetPage, CommandLogFilters filters) {
    if (!available) {
      return MM.deserialize(NO_PAGE_LINK, Placeholder.unparsed("text", text));
    }
    String flags = filters.toFlags();
    String command = "/commandlog --page " + targetPage + (flags.isEmpty() ? "" : " " + flags);
    return MM.deserialize(
        PAGE_LINK,
        TagResolver.resolver("link", Tag.styling(ClickEvent.runCommand(command))),
        Placeholder.unparsed("text", text));
  }

  /** A short duration such as {@code 3d 4h}, {@code 12m} or {@code 45s}. */
  static String ago(Duration duration) {
    long seconds = Math.max(0, duration.toSeconds());
    long days = seconds / 86_400;
    long hours = seconds % 86_400 / 3_600;
    long minutes = seconds % 3_600 / 60;
    if (days > 0) {
      return hours > 0 ? days + "d " + hours + "h" : days + "d";
    }
    if (hours > 0) {
      return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
    }
    if (minutes > 0) {
      return minutes + "m";
    }
    return seconds + "s";
  }
}
