package com.nearvanilla.iceCream.modules.bannerLayers.events;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import com.nearvanilla.iceCream.modules.bannerLayers.BannerLayerStore;
import io.papermc.paper.event.player.PlayerLoomPatternSelectEvent;
import java.util.function.Consumer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LoomInventory;
import org.bukkit.inventory.PlayerInventory;

/**
 * Lets the loom add layers to banners that already have 6, up to the configured limit.
 *
 * <p>The client hides the loom's patterns once a banner has 6 layers, and the server clears the
 * result, so the banner in the loom is swapped for a hidden copy showing only 5 (see {@link
 * BannerLayerStore}). The loom then works as normal, and its result is revealed into the real
 * banner with the new layer on top.
 *
 * <p>A hidden copy is only ever meant to sit in the loom's banner slot. It is revealed before any
 * click that could take it out, when the loom closes, and in the player's inventory one tick after
 * the loom closes in case one slipped out another way.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public class BannerLayersListener implements Listener {
  /** The loom's banner input slot. */
  public static final int BANNER_SLOT = 0;

  /** The loom's result slot. */
  public static final int RESULT_SLOT = 3;

  private final BannerLayerStore store;
  private final int maxLayers;
  private final Consumer<Runnable> nextTick;

  /**
   * Creates the listener.
   *
   * @param store hides and reveals banner layers
   * @param maxLayers the most layers a banner may get in the loom
   * @param nextTick runs a task on the next server tick
   */
  public BannerLayersListener(BannerLayerStore store, int maxLayers, Consumer<Runnable> nextTick) {
    this.store = store;
    this.maxLayers = maxLayers;
    this.nextTick = nextTick;
  }

  /** Turns the loom's result, made from a hidden copy, into the real banner. */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPrepareResult(PrepareResultEvent event) {
    if (!(event.getInventory() instanceof LoomInventory)) return;
    ItemStack result = event.getResult();
    if (store.isHidden(result)) event.setResult(store.reveal(result));
  }

  /**
   * Reveals a hidden copy before a click can take it out of the loom, and checks the loom again
   * once the click is done.
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory() instanceof LoomInventory loom)) return;
    // The loom's slots come first in the view, so its raw slots match its own slot numbers.
    int slot = event.getRawSlot();
    if (slot == RESULT_SLOT) {
      // Only the result: revealing the banner as well would make the loom clear the result.
      revealSlot(loom, RESULT_SLOT);
    } else if (slot == BANNER_SLOT) {
      revealSlot(loom, BANNER_SLOT);
    } else if (slot >= loom.getSize()
        && event.isShiftClick()
        && stacksWithHiddenBanner(loom, event.getCurrentItem())) {
      // Lets a shift-clicked banner join the identical banners already in the loom.
      revealSlot(loom, BANNER_SLOT);
    }
    syncNextTick(event.getWhoClicked(), loom);
  }

  /** Checks the loom again once a drag has placed its items. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDrag(InventoryDragEvent event) {
    if (event.getView().getTopInventory() instanceof LoomInventory loom) {
      syncNextTick(event.getWhoClicked(), loom);
    }
  }

  /** Reveals the result the loom made from a hidden copy once a pattern is picked. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSelectPattern(PlayerLoomPatternSelectEvent event) {
    syncNextTick(event.getPlayer(), event.getLoomInventory());
  }

  /** Reveals the banner before the loom hands it back to the player. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory() instanceof LoomInventory loom)) return;
    revealSlot(loom, BANNER_SLOT);
    HumanEntity player = event.getPlayer();
    nextTick.accept(() -> revealInventory(player));
  }

  /**
   * Hides the banner in a player's open loom when the loom would refuse it, and reveals the loom's
   * result when it came from a hidden copy. Does nothing once the player has left the loom.
   *
   * @param player the player using the loom
   * @param loom the loom's inventory
   */
  public void sync(HumanEntity player, LoomInventory loom) {
    if (!loom.equals(player.getOpenInventory().getTopInventory())) return;
    ItemStack banner = loom.getItem(BANNER_SLOT);
    if (store.shouldHide(banner, maxLayers)) loom.setItem(BANNER_SLOT, store.hide(banner));
    revealSlot(loom, RESULT_SLOT);
  }

  /**
   * Reveals the banner and result in a player's open loom, if they have one. Used when the module
   * shuts down, so no hidden copy is left behind.
   *
   * @param player the player
   */
  public void revealOpenLoom(Player player) {
    if (player.getOpenInventory().getTopInventory() instanceof LoomInventory loom) {
      revealSlot(loom, BANNER_SLOT);
      revealSlot(loom, RESULT_SLOT);
    }
  }

  private void syncNextTick(HumanEntity player, LoomInventory loom) {
    nextTick.accept(() -> sync(player, loom));
  }

  private boolean stacksWithHiddenBanner(LoomInventory loom, ItemStack moving) {
    ItemStack banner = loom.getItem(BANNER_SLOT);
    return moving != null && store.isHidden(banner) && moving.isSimilar(store.reveal(banner));
  }

  private void revealSlot(Inventory inventory, int slot) {
    ItemStack item = inventory.getItem(slot);
    if (store.isHidden(item)) inventory.setItem(slot, store.reveal(item));
  }

  private void revealInventory(HumanEntity player) {
    PlayerInventory inventory = player.getInventory();
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      revealSlot(inventory, slot);
    }
    ItemStack cursor = player.getItemOnCursor();
    if (store.isHidden(cursor)) player.setItemOnCursor(store.reveal(cursor));
  }
}
