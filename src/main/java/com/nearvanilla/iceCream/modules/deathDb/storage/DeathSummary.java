package com.nearvanilla.iceCream.modules.deathDb.storage;

/** Metadata for one recorded death. The inventory blob is deliberately not included. */
public record DeathSummary(
    long id, long diedAtMillis, String world, double x, double y, double z) {}
