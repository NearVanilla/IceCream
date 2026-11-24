package com.nearvanilla.iceCream.modules.commandTracker.storage;

import java.util.UUID;

/**
 * One command that was run.
 *
 * @param executedAtMillis when it ran, in epoch milliseconds
 * @param source who ran it
 * @param playerId the player's UUID, or null for the console and RCON
 * @param name the player's name at the time, or the console or RCON name
 * @param command the command name, lowercase and without the slash or a namespace
 * @param commandLine the full command line with a leading slash, with sensitive arguments redacted
 * @param position where the player stood, or null for the console and RCON
 * @param cancelled whether another plugin cancelled the command
 */
public record CommandUse(
    long executedAtMillis,
    CommandSource source,
    UUID playerId,
    String name,
    String command,
    String commandLine,
    CommandPosition position,
    boolean cancelled) {}
