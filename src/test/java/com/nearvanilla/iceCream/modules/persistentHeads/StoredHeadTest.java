package com.nearvanilla.iceCream.modules.persistentHeads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class StoredHeadTest {
  private static final List<Component> LORE = List.of(Component.text("Killed by Steve"));

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void restoresLoreOnPlayerHead() {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);

    new StoredHead(LORE, null).applyTo(head);

    assertEquals(LORE, head.lore());
  }

  @Test
  void restoresPaperTweaksNameWhenItemHasNone() {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);

    new StoredHead(null, Component.text("Trophy")).applyTo(head);

    assertEquals(Component.text("Trophy"), head.getItemMeta().customName());
  }

  @Test
  void keepsNameVanillaAlreadyRestored() {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    head.editMeta(meta -> meta.customName(Component.text("Vanilla")));

    new StoredHead(null, Component.text("Old")).applyTo(head);

    assertEquals(Component.text("Vanilla"), head.getItemMeta().customName());
  }

  @Test
  void leavesOtherItemsAlone() {
    ItemStack dirt = new ItemStack(Material.DIRT);

    new StoredHead(LORE, Component.text("Trophy")).applyTo(dirt);

    assertNull(dirt.lore());
    assertFalse(dirt.getItemMeta().hasCustomName());
  }
}
