package com.nearvanilla.iceCream.modules.commandTracker.storage;

/** Who ran a recorded command. */
public enum CommandSource {
  /** A player typed the command, or clicked something that ran it. */
  PLAYER,
  /** The command was typed in the server console. */
  CONSOLE,
  /** The command was sent over RCON. */
  RCON
}
