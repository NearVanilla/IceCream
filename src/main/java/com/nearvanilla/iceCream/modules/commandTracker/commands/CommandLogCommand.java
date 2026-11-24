package com.nearvanilla.iceCream.modules.commandTracker.commands;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.commandTracker.CommandNames;
import com.nearvanilla.iceCream.modules.commandTracker.CommandTrackerModule;
import com.nearvanilla.iceCream.modules.commandTracker.TimeArguments;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogQuery;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

/** Lets staff look up the commands players have run, filtered and a page at a time. */
public class CommandLogCommand {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final Component LOAD_FAILED =
      MM.deserialize("<red>Could not load the command log. See the server log for details.</red>");
  private static final String BAD_TIME =
      "<red>Could not read --<flag> <value>. Use <formats>.</red>";
  private static final List<String> TIME_SUGGESTIONS =
      List.of("30m", "1h", "6h", "1d", "7d", "30d");

  private final CommandLogRepository repository;
  private final CommandLogFormatter formatter;
  private final int pageSize;
  private final Executor mainThread;
  private final Function<String, org.bukkit.command.Command> commandLookup;
  private final Clock clock;

  /**
   * Creates the command.
   *
   * @param repository where commands are stored
   * @param formatter renders the results
   * @param pageSize commands shown per page
   * @param mainThread runs tasks on the server thread
   * @param commandLookup finds a registered command by label, returning null when there is none
   * @param clock the current time, which durations count back from
   */
  public CommandLogCommand(
      CommandLogRepository repository,
      CommandLogFormatter formatter,
      int pageSize,
      Executor mainThread,
      Function<String, org.bukkit.command.Command> commandLookup,
      Clock clock) {
    this.repository = repository;
    this.formatter = formatter;
    this.pageSize = pageSize;
    this.mainThread = mainThread;
    this.commandLookup = commandLookup;
    this.clock = clock;
  }

  @Suggestions("commandlog_players")
  public List<String> suggestPlayers(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    List<String> names = new ArrayList<>();
    for (Player online : Bukkit.getOnlinePlayers()) {
      names.add(online.getName());
    }
    names.add(Bukkit.getConsoleSender().getName());
    return names;
  }

  @Suggestions("commandlog_times")
  public List<String> suggestTimes(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return TIME_SUGGESTIONS;
  }

  /**
   * Shows a page of the commands matching the given filters, newest first.
   *
   * @param commandSourceStack the command source
   * @param page the page to show, starting at 1
   * @param player only commands run by this player, or by the console's name
   * @param command only this command, by name or alias
   * @param since only commands run at or after this time
   * @param until only commands run before this time
   * @param search only commands containing this text
   */
  @Command("commandlog|cmdlog")
  @CommandDescription("Look up the commands players have run.")
  @Permission(CommandTrackerModule.LOOKUP_PERMISSION)
  @SuppressWarnings("unused")
  public void commandLog(
      CommandSourceStack commandSourceStack,
      @Flag(value = "page", aliases = "n", description = "The page to show, starting at 1")
          Integer page,
      @Flag(
              value = "player",
              aliases = "p",
              suggestions = "commandlog_players",
              description = "Only commands run by this player, or by the console")
          String player,
      @Flag(value = "command", aliases = "c", description = "Only this command, by name or alias")
          String command,
      @Flag(
              value = "since",
              aliases = "s",
              suggestions = "commandlog_times",
              description = "Only commands run since then, such as 2h, 7d or 2026-11-20")
          String since,
      @Flag(
              value = "until",
              aliases = "u",
              suggestions = "commandlog_times",
              description = "Only commands run before then, such as 1h or 2026-11-21T18:00")
          String until,
      @Flag(
              value = "search",
              aliases = "f",
              description = "Only commands containing this text; quote it to include spaces")
          @Quoted
          String search) {
    lookup(
        commandSourceStack.getSender(),
        page == null ? 1 : Math.max(1, page),
        new CommandLogFilters(player, command, since, until, search));
  }

  /**
   * Looks up a page and sends it to the sender once it has loaded.
   *
   * @param sender who to send the results to
   * @param page the page to show, starting at 1
   * @param filters the filters as typed
   */
  public void lookup(CommandSender sender, int page, CommandLogFilters filters) {
    Instant now = clock.instant();
    CommandLogQuery query = CommandLogQuery.all();
    if (filters.player() != null) {
      OfflinePlayer known = findPlayer(filters.player());
      query =
          known == null
              ? query.withName(filters.player())
              : query.withPlayerId(known.getUniqueId());
    }
    if (filters.command() != null) {
      String label =
          filters.command().startsWith("/") ? filters.command().substring(1) : filters.command();
      query = query.withCommand(CommandNames.resolve(label, commandLookup).primary());
    }
    if (filters.since() != null) {
      Long since = parseTime(sender, "since", filters.since(), now);
      if (since == null) return;
      query = query.withSince(since);
    }
    if (filters.until() != null) {
      Long until = parseTime(sender, "until", filters.until(), now);
      if (until == null) return;
      query = query.withUntil(until);
    }
    if (filters.search() != null && !filters.search().isEmpty()) {
      query = query.withSearch(filters.search());
    }

    repository
        .findPage(query, page - 1, pageSize)
        .whenComplete(
            (result, error) ->
                mainThread.execute(
                    () -> {
                      if (error != null) {
                        IceCream.logger.log(Level.SEVERE, "Failed to load the command log", error);
                        sender.sendMessage(LOAD_FAILED);
                        return;
                      }
                      for (Component line : formatter.format(result, filters, now)) {
                        sender.sendMessage(line);
                      }
                    }));
  }

  /** Finds a player by exact name, online first, then among players the server has seen. */
  private static OfflinePlayer findPlayer(String name) {
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      return online;
    }
    return Bukkit.getOfflinePlayerIfCached(name);
  }

  /** Reads a time flag, or tells the sender why it cannot be read and returns null. */
  private Long parseTime(CommandSender sender, String flag, String value, Instant now) {
    try {
      return TimeArguments.parse(value, now, clock.getZone()).toEpochMilli();
    } catch (IllegalArgumentException | ArithmeticException e) {
      sender.sendMessage(
          MM.deserialize(
              BAD_TIME,
              Placeholder.unparsed("flag", flag),
              Placeholder.unparsed("value", value),
              Placeholder.unparsed("formats", TimeArguments.FORMATS)));
      return null;
    }
  }
}
