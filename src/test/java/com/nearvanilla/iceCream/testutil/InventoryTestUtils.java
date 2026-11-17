package com.nearvanilla.iceCream.testutil;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

/**
 * Helpers for testing inventory click events with MockBukkit, whose default inventory view does not
 * implement {@code convertSlot}, which {@code InventoryClickEvent}'s constructor calls, or {@code
 * getInventory(int)}, which {@code getClickedInventory} calls, and copies the cursor item in a way
 * that drops its data components.
 */
public final class InventoryTestUtils {

  private InventoryTestUtils() {}

  /**
   * Creates the view a player has open when no container is open: their own inventory with the
   * crafting grid. Unlike MockBukkit's default view it maps a raw slot to itself, so an {@code
   * InventoryClickEvent} can be constructed from it, it keeps the exact cursor item it was given
   * (with its data components), and its cursor starts as an empty item, like a real server's,
   * instead of null.
   *
   * @param player the player the view belongs to
   * @return a crafting view with an empty cursor
   */
  public static InventoryView craftingView(HumanEntity player) {
    return craftingView(player, InventoryType.CRAFTING);
  }

  /**
   * Creates a view like {@link #craftingView(HumanEntity)} with another inventory type, for tests
   * of code that only reacts to the crafting view.
   *
   * @param player the player the view belongs to
   * @param type the type the view reports
   * @return a view of the given type with an empty cursor
   */
  public static InventoryView craftingView(HumanEntity player, InventoryType type) {
    return new IdentitySlotView(player, player.getInventory(), type, false);
  }

  /**
   * Creates the view a player has open when they look into a container: the container on top and
   * their own inventory below. An {@code InventoryClickEvent} can be constructed from it, and its
   * cursor starts as an empty item. A raw slot in the container is the slot itself. The rows below
   * it map straight to the player's slots 0 to 35, which is simpler than a real server, where they
   * map to slots 9 to 35 and then the hotbar slots 0 to 8.
   *
   * @param player the player the view belongs to
   * @param top the container shown on top
   * @return a chest view of the container
   */
  public static InventoryView containerView(HumanEntity player, Inventory top) {
    return new IdentitySlotView(player, top, InventoryType.CHEST, true);
  }

  /**
   * Counts how many items of a material a player carries in total: the storage slots, the armor
   * slots and the off hand. A test that moves a stack around can assert this total instead of where
   * each item ended up.
   *
   * @param inventory the player's inventory
   * @param material the material to count
   * @return the summed amount of every stack of the material
   */
  public static int countOf(PlayerInventory inventory, Material material) {
    int total = 0;
    for (ItemStack stack : inventory.getContents()) {
      if (stack != null && stack.getType() == material) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private static final class IdentitySlotView extends SimpleInventoryViewMock {
    private ItemStack cursor = ItemStack.empty();

    private final boolean container;

    IdentitySlotView(HumanEntity player, Inventory top, InventoryType type, boolean container) {
      super(player, top, player.getInventory(), type);
      this.container = container;
    }

    @Override
    public void setCursor(ItemStack item) {
      cursor = item == null ? ItemStack.empty() : item;
    }

    @Override
    public ItemStack getCursor() {
      return cursor;
    }

    @Override
    public int convertSlot(int rawSlot) {
      if (!container) return rawSlot;
      int topSize = getTopInventory().getSize();
      return rawSlot < topSize ? rawSlot : rawSlot - topSize;
    }

    // MockBukkit does not implement this, which InventoryClickEvent#getClickedInventory calls.
    @Override
    public Inventory getInventory(int rawSlot) {
      int topSize = getTopInventory().getSize();
      if (rawSlot < 0) return null;
      if (rawSlot < topSize) return getTopInventory();
      if (rawSlot < topSize + getBottomInventory().getSize()) return getBottomInventory();
      return null;
    }
  }
}
