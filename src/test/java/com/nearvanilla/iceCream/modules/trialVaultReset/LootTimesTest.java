package com.nearvanilla.iceCream.modules.trialVaultReset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class LootTimesTest {
  private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

  private final LootTimes lootTimes = new LootTimes("icecream");
  private PersistentDataContainer pdc;

  @BeforeEach
  void setUp() {
    // A player's container is a real PersistentDataContainer, like the vault's.
    pdc = MockBukkit.mock().addPlayer().getPersistentDataContainer();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void getReturnsNullWhenMissing() {
    assertNull(lootTimes.get(pdc, A));
  }

  @Test
  void putThenGetRoundTrips() {
    lootTimes.put(pdc, A, 1234L);

    assertEquals(1234L, lootTimes.get(pdc, A));
    assertNull(lootTimes.get(pdc, B));
  }

  @Test
  void removeOnlyAffectsThatPlayer() {
    lootTimes.put(pdc, A, 1L);
    lootTimes.put(pdc, B, 2L);

    lootTimes.remove(pdc, A);

    assertNull(lootTimes.get(pdc, A));
    assertEquals(2L, lootTimes.get(pdc, B));
  }

  @Test
  void retainOnlyDropsPlayersNotListed() {
    lootTimes.put(pdc, A, 1L);
    lootTimes.put(pdc, B, 2L);
    lootTimes.put(pdc, C, 3L);

    lootTimes.retainOnly(pdc, List.of(A, C));

    assertEquals(1L, lootTimes.get(pdc, A));
    assertNull(lootTimes.get(pdc, B));
    assertEquals(3L, lootTimes.get(pdc, C));
  }

  @Test
  void retainOnlyLeavesUnrelatedKeys() {
    NamespacedKey other = new NamespacedKey("icecream", "other");
    NamespacedKey otherPlugin = new NamespacedKey("otherplugin", "trialvaultreset.loot." + A);
    pdc.set(other, PersistentDataType.BOOLEAN, true);
    pdc.set(otherPlugin, PersistentDataType.LONG, 5L);

    lootTimes.retainOnly(pdc, List.of());

    assertTrue(pdc.has(other));
    assertEquals(5L, pdc.get(otherPlugin, PersistentDataType.LONG));
  }
}
