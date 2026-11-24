package com.nearvanilla.iceCream.modules.commandTracker.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * The filters typed in {@code /commandlog}, kept as typed so the page links can repeat them. Every
 * value is optional (null).
 *
 * @param player the player name, or the console's name
 * @param command the command name or alias
 * @param since the start time, as typed
 * @param until the end time, as typed
 * @param search the text the command line must contain
 */
public record CommandLogFilters(
    String player, String command, String since, String until, String search) {

  /** Whether no filter is set. */
  public boolean isEmpty() {
    return player == null && command == null && since == null && until == null && search == null;
  }

  /**
   * The filters as {@code /commandlog} flags, such as {@code --player Bob --since 7d}, or an empty
   * string when none are set.
   */
  public String toFlags() {
    List<String> flags = new ArrayList<>();
    add(flags, "player", player);
    add(flags, "command", command);
    add(flags, "since", since);
    add(flags, "until", until);
    add(flags, "search", search);
    return String.join(" ", flags);
  }

  private static void add(List<String> flags, String name, String value) {
    if (value != null) {
      flags.add("--" + name + " " + quote(value));
    }
  }

  /** Quotes a value that contains spaces or quotes, so the command reads it as one word. */
  static String quote(String value) {
    if (!value.isEmpty() && value.chars().noneMatch(c -> c == ' ' || c == '"' || c == '\'')) {
      return value;
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
