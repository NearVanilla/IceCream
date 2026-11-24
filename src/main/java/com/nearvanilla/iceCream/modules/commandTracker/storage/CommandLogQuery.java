package com.nearvanilla.iceCream.modules.commandTracker.storage;

import java.util.UUID;

/**
 * Filters for looking up stored commands. Every filter is optional (null) and they all have to
 * match.
 *
 * @param playerId only commands run by this player, under any name
 * @param name only commands run under this name, ignoring case. Ignored when {@code playerId} is
 *     set
 * @param command only this command name, as stored in {@link CommandUse#command()}
 * @param sinceMillis only commands run at or after this time
 * @param untilMillis only commands run before this time
 * @param search only command lines containing this text, ignoring case
 */
public record CommandLogQuery(
    UUID playerId, String name, String command, Long sinceMillis, Long untilMillis, String search) {

  /** A query that matches every stored command. */
  public static CommandLogQuery all() {
    return new CommandLogQuery(null, null, null, null, null, null);
  }

  public CommandLogQuery withPlayerId(UUID value) {
    return new CommandLogQuery(value, name, command, sinceMillis, untilMillis, search);
  }

  public CommandLogQuery withName(String value) {
    return new CommandLogQuery(playerId, value, command, sinceMillis, untilMillis, search);
  }

  public CommandLogQuery withCommand(String value) {
    return new CommandLogQuery(playerId, name, value, sinceMillis, untilMillis, search);
  }

  public CommandLogQuery withSince(Long value) {
    return new CommandLogQuery(playerId, name, command, value, untilMillis, search);
  }

  public CommandLogQuery withUntil(Long value) {
    return new CommandLogQuery(playerId, name, command, sinceMillis, value, search);
  }

  public CommandLogQuery withSearch(String value) {
    return new CommandLogQuery(playerId, name, command, sinceMillis, untilMillis, value);
  }
}
