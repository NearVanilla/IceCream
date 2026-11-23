package com.nearvanilla.iceCream.modules.persistentHeads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HeadStoreTest {
  private static final NamespacedKey LEGACY_LORE = new NamespacedKey("vanillatweaks", "head_lore");
  private static final NamespacedKey LEGACY_NAME = new NamespacedKey("vanillatweaks", "head_name");

  private final HeadStore store = new HeadStore("icecream");
  private PersistentDataContainer pdc;

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    // A player's container stands in for the placed head's container.
    pdc = server.addPlayer().getPersistentDataContainer();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void emptyContainerHasNothingStored() {
    StoredHead head = store.read(pdc);

    assertTrue(head.isEmpty());
    assertNull(head.lore());
    assertNull(head.legacyName());
  }

  @Test
  void readsBackSavedLore() {
    List<Component> lore =
        List.of(Component.text("Killed by Steve"), Component.text("Second", NamedTextColor.GOLD));

    store.save(pdc, lore);

    assertEquals(lore, store.read(pdc).lore());
  }

  @Test
  void savesUnderPluginNamespace() {
    store.save(pdc, List.of(Component.text("Killed by Steve")));

    assertTrue(pdc.has(new NamespacedKey("icecream", "head_lore")));
  }

  @Test
  void readsPaperTweaksLore() {
    pdc.set(LEGACY_LORE, PersistentDataType.STRING, "[{\"text\":\"Killed by Alex\"}]");

    assertEquals(List.of(Component.text("Killed by Alex")), store.read(pdc).lore());
  }

  @Test
  void readsPaperTweaksLegacySectionLore() {
    pdc.set(LEGACY_LORE, PersistentDataType.STRING, "[\"§6Gold line\"]");

    assertEquals(List.of(Component.text("Gold line", NamedTextColor.GOLD)), store.read(pdc).lore());
  }

  @Test
  void prefersOwnLoreOverPaperTweaksLore() {
    pdc.set(LEGACY_LORE, PersistentDataType.STRING, "[{\"text\":\"Old\"}]");
    store.save(pdc, List.of(Component.text("New")));

    assertEquals(List.of(Component.text("New")), store.read(pdc).lore());
  }

  @Test
  void readsPaperTweaksName() {
    pdc.set(LEGACY_NAME, PersistentDataType.STRING, "{\"text\":\"Trophy\"}");

    StoredHead head = store.read(pdc);

    assertEquals(Component.text("Trophy"), head.legacyName());
    assertNull(head.lore());
  }

  @Test
  void ignoresUnreadablePaperTweaksData() {
    pdc.set(LEGACY_LORE, PersistentDataType.STRING, "not json [");
    pdc.set(LEGACY_NAME, PersistentDataType.INTEGER, 5);

    assertTrue(store.read(pdc).isEmpty());
  }
}
