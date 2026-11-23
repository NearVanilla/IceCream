package com.nearvanilla.iceCream.modules.mapTools.events;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A map tools command or form failed, and the sender was told why in chat.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-22
 */
public class MapToolsErrorEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final CommandSender sender;
  private final Component message;

  public MapToolsErrorEvent(CommandSender sender, Component message) {
    this.sender = sender;
    this.message = message;
  }

  public CommandSender getSender() {
    return sender;
  }

  /** The message shown in chat. */
  public Component getMessage() {
    return message;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
