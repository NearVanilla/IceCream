package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.utils.SchedulerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest menu to pick a marker icon: one item per icon, plus a barrier for no icon. Clicking an
 * item picks it; closing the menu keeps the current icon. Either way the form it came from reopens.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-22
 */
public final class IconMenu implements InventoryHolder {

  private final Inventory inventory;

  /** The icon in each slot; null is the no-icon slot. */
  private final List<String> slots = new ArrayList<>();

  private final String current;
  private final Consumer<String> onPick;
  private boolean done;

  private IconMenu(String current, Consumer<String> onPick) {
    this.current = current;
    this.onPick = onPick;
    slots.add(null);
    slots.addAll(MapToolsUtils.iconNames());
    int size = Math.min(54, (slots.size() + 8) / 9 * 9);
    inventory = Bukkit.createInventory(this, size, Component.text("Pick an icon"));
    for (int i = 0; i < Math.min(size, slots.size()); i++) {
      inventory.setItem(i, item(slots.get(i)));
    }
  }

  /**
   * Opens the menu.
   *
   * @param current the icon picked so far, or null
   * @param onPick reopens the form with the picked icon, or the current one when closed
   */
  static void open(Player player, String current, Consumer<String> onPick) {
    player.openInventory(new IconMenu(current, onPick).inventory);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  /** Picks the icon in the clicked slot. */
  public void click(Player player, int slot) {
    if (slot < 0 || slot >= slots.size() || slot >= inventory.getSize()) return;
    finish(player, slots.get(slot));
  }

  /** Keeps the current icon when the menu is closed without picking one. */
  public void closed(Player player) {
    finish(player, current);
  }

  private void finish(Player player, String icon) {
    if (done) return;
    done = true;
    // Close and reopen the form on the next tick, outside the inventory event.
    SchedulerUtils.runForNextTick(
        player,
        () -> {
          player.closeInventory();
          onPick.accept(icon);
        });
  }

  private ItemStack item(String icon) {
    ItemStack item = new ItemStack(icon == null ? Material.BARRIER : MapToolsUtils.iconItem(icon));
    item.editMeta(
        meta -> {
          meta.displayName(
              Component.text(icon == null ? "No icon (dot)" : icon, NamedTextColor.WHITE)
                  .decoration(TextDecoration.ITALIC, false));
          if (Objects.equals(icon, current)) {
            meta.setEnchantmentGlintOverride(true);
            meta.lore(
                List.of(
                    Component.text("Selected", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
          }
        });
    return item;
  }
}
