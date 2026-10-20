package com.nearvanilla.iceCream.modules.deathDb.storage;

/**
 * A recorded death together with its serialized inventory snapshot.
 *
 * @param summary the death metadata
 * @param inventory bytes produced by {@code InventorySnapshot.serialize}; treat as read-only
 */
public record DeathRecord(DeathSummary summary, byte[] inventory) {}
