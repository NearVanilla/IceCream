package com.nearvanilla.iceCream.modules.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

class TagItemTest {
  private final TagItem tagItem = new TagItem("icecream");
  private ServerMock server;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    player = server.addPlayer();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static ItemStack nameTag(Component name) {
    ItemStack item = new ItemStack(Material.NAME_TAG);
    item.editMeta(meta -> meta.displayName(name));
    return item;
  }

  @Test
  void createdItemIsATagItem() {
    ItemStack item = tagItem.create();

    assertEquals(Material.NAME_TAG, item.getType());
    assertTrue(tagItem.isTagItem(item));
  }

  @Test
  void recognisesPaperTweaksTagItem() {
    ItemStack legacy =
        nameTag(
            Component.text("Tag!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

    assertTrue(tagItem.isTagItem(legacy));
  }

  @Test
  void ignoresOtherItems() {
    assertFalse(tagItem.isTagItem(null));
    assertFalse(tagItem.isTagItem(new ItemStack(Material.NAME_TAG)));
    // What an anvil gives a name tag renamed to "Tag!".
    assertFalse(tagItem.isTagItem(nameTag(Component.text("Tag!"))));
    ItemStack paper = new ItemStack(Material.PAPER);
    paper.editMeta(
        meta ->
            meta.displayName(
                Component.text("Tag!", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)));
    assertFalse(tagItem.isTagItem(paper));
  }

  @Test
  void giveToPutsTheItemInTheInventory() {
    tagItem.giveTo(player);

    assertTrue(tagItem.isTagItem(player.getInventory().getItem(0)));
  }

  @Test
  void giveToDropsTheItemWhenTheInventoryIsFull() {
    // Mocks, as MockBukkit has no Item#setOwner.
    UUID id = UUID.randomUUID();
    Location location = new Location(null, 1, 64, 1);
    World world = mock(World.class);
    Item dropped = mock(Item.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    Player full = mock(Player.class);
    when(full.getInventory()).thenReturn(inventory);
    when(inventory.firstEmpty()).thenReturn(-1);
    when(full.getWorld()).thenReturn(world);
    when(full.getLocation()).thenReturn(location);
    when(full.getUniqueId()).thenReturn(id);
    ArgumentCaptor<ItemStack> stack = ArgumentCaptor.forClass(ItemStack.class);
    when(world.dropItem(eq(location), stack.capture())).thenReturn(dropped);

    tagItem.giveTo(full);

    assertTrue(tagItem.isTagItem(stack.getValue()));
    verify(dropped).setPickupDelay(0);
    verify(dropped).setOwner(id);
  }

  @Test
  void removeFromClearsEveryTagItemAndKeepsOtherItems() {
    player.getInventory().setItem(0, tagItem.create());
    player.getInventory().setItem(5, new ItemStack(Material.DIRT));
    player.getInventory().setItemInOffHand(tagItem.create());

    tagItem.removeFrom(player);

    assertNull(player.getInventory().getItem(0));
    assertEquals(Material.AIR, player.getInventory().getItemInOffHand().getType());
    assertEquals(Material.DIRT, player.getInventory().getItem(5).getType());
  }
}
