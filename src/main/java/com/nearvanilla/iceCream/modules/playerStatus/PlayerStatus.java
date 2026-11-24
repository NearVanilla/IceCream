package com.nearvanilla.iceCream.modules.playerStatus;

/**
 * A status shown next to a player's name in the tab list.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-23
 */
public enum PlayerStatus {
  DO_NOT_DISTURB("do_not_disturb", "do not disturb"),
  STREAMING("streaming", "streaming"),
  AFK("afk", "AFK");

  private final String id;
  private final String displayName;

  PlayerStatus(String id, String displayName) {
    this.id = id;
    this.displayName = displayName;
  }

  /** Returns the id sent to the proxy and used as the key in bat's {@code status-codes}. */
  public String id() {
    return id;
  }

  /** Returns the name shown to players in messages. */
  public String displayName() {
    return displayName;
  }
}
