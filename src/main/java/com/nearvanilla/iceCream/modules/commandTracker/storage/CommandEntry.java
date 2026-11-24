package com.nearvanilla.iceCream.modules.commandTracker.storage;

/**
 * A stored command.
 *
 * @param id the database id, increasing in the order commands were stored
 * @param use the command
 */
public record CommandEntry(long id, CommandUse use) {}
