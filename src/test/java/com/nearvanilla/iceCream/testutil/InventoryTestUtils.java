package com.nearvanilla.iceCream.testutil;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

/**
 * Helpers for testing inventory click events with MockBukkit, whose default inventory view does not
 * implement {@code convertSlot}, which {@code InventoryClickEvent}'s constructor calls, and copies
 * the cursor item in a way that drops its data components.
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
    return new IdentitySlotView(player, type);
  }

  private static final class IdentitySlotView extends SimpleInventoryViewMock {
    private ItemStack cursor = ItemStack.empty();

    IdentitySlotView(HumanEntity player, InventoryType type) {
      super(player, player.getInventory(), player.getInventory(), type);
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
      return rawSlot;
    }
  }
}
