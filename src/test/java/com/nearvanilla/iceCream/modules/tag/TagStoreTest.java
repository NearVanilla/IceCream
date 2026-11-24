package com.nearvanilla.iceCream.modules.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TagStoreTest {
  private static final NamespacedKey LEGACY_IT = new NamespacedKey("vanillatweaks", "tag");
  private static final NamespacedKey LEGACY_COOLDOWN =
      new NamespacedKey("vanillatweaks", "cooldown");

  private final TagStore store = new TagStore("icecream");
  private PlayerMock player;
  private PersistentDataContainer pdc;

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    player = server.addPlayer();
    pdc = player.getPersistentDataContainer();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void playersAreNotItAndPlayingByDefault() {
    assertFalse(store.isIt(player));
    assertFalse(store.isOptedOut(player));
    assertEquals(Long.MIN_VALUE, store.cooldownUntil(player));
  }

  @Test
  void setItStoresItAndCooldownUnderThePluginNamespace() {
    store.setIt(player, 5_000);

    assertTrue(store.isIt(player));
    assertEquals(5_000, store.cooldownUntil(player));
    assertTrue(pdc.has(new NamespacedKey("icecream", "tag.it"), PersistentDataType.BOOLEAN));
    assertEquals(
        5_000L, pdc.get(new NamespacedKey("icecream", "tag.cooldown"), PersistentDataType.LONG));
  }

  @Test
  void clearItRemovesItAndCooldown() {
    store.setIt(player, 5_000);

    store.clearIt(player);

    assertFalse(store.isIt(player));
    assertEquals(Long.MIN_VALUE, store.cooldownUntil(player));
    assertTrue(pdc.getKeys().isEmpty());
  }

  @Test
  void optingOutAndBackInIsStored() {
    store.setOptedOut(player, true);
    assertTrue(store.isOptedOut(player));
    assertTrue(pdc.has(new NamespacedKey("icecream", "tag.opted_out"), PersistentDataType.BOOLEAN));

    store.setOptedOut(player, false);
    assertFalse(store.isOptedOut(player));
    assertTrue(pdc.getKeys().isEmpty());
  }

  @Test
  void takeLegacyItReportsAndRemovesPaperTweaksEntries() {
    pdc.set(LEGACY_IT, PersistentDataType.BOOLEAN, true);
    pdc.set(LEGACY_COOLDOWN, PersistentDataType.LONG, 123L);

    assertTrue(store.takeLegacyIt(player));

    assertFalse(pdc.has(LEGACY_IT));
    assertFalse(pdc.has(LEGACY_COOLDOWN));
    assertFalse(store.takeLegacyIt(player));
  }

  @Test
  void takeLegacyItIsFalseWithoutPaperTweaksEntries() {
    assertFalse(store.takeLegacyIt(player));
  }

  @Test
  void readsPaperTweaksAfkFlag() {
    assertFalse(store.isAfk(player));

    pdc.set(new NamespacedKey("vanillatweaks", "afk"), PersistentDataType.BOOLEAN, true);

    assertTrue(store.isAfk(player));
  }

  @Test
  void entriesOfAnotherTypeCountAsUnset() {
    pdc.set(new NamespacedKey("icecream", "tag.it"), PersistentDataType.STRING, "yes");

    assertFalse(store.isIt(player));
  }
}
