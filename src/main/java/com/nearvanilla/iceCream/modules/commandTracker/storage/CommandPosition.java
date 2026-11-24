package com.nearvanilla.iceCream.modules.commandTracker.storage;

/**
 * Where a player stood when they ran a command.
 *
 * @param world the world name
 * @param x the x coordinate
 * @param y the y coordinate
 * @param z the z coordinate
 */
public record CommandPosition(String world, double x, double y, double z) {}
