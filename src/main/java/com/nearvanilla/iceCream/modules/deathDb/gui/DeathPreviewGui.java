package com.nearvanilla.iceCream.modules.deathDb.gui;

import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.filler;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.line;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.named;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.InventoryRestorer;
import com.nearvanilla.iceCream.modules.deathDb.InventorySnapshot;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRecord;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import com.nearvanilla.iceCream.utils.SchedulerUtils;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only preview of one recorded inventory, laid out like the in-game inventory, with a cancel
 * pane and a restore pane.
 */
public final class DeathPreviewGui implements DeathGui {
  private static final int SIZE = 54;
  private static final int INFO_SLOT = 0;
  private static final int HELMET_SLOT = 2;
  private static final int CHESTPLATE_SLOT = 3;
  private static final int LEGGINGS_SLOT = 4;
  private static final int BOOTS_SLOT = 5;
  private static final int OFFHAND_SLOT = 7;
  private static final int MAIN_START = 9;
  private static final int HOTBAR_START = 36;
  private static final int CANCEL_SLOT = 48;
  private static final int RESTORE_SLOT = 50;

  private final UUID targetId;
  private final String targetName;
  private final DeathSummary death;
  private final ItemStack[] snapshot;
  private final Inventory inventory;
  private boolean restored = false;

  private DeathPreviewGui(
      UUID targetId, String targetName, DeathSummary death, ItemStack[] snapshot) {
    this.targetId = targetId;
    this.targetName = targetName;
    this.death = death;
    this.snapshot = snapshot;
    this.inventory =
        Bukkit.createInventory(
            this,
            SIZE,
            line("<dark_gray>Inventory of <name>", Placeholder.unparsed("name", targetName)));
    populate();
  }

  private void populate() {
    for (int slot = 0; slot < 9; slot++) {
      inventory.setItem(slot, filler());
    }
    for (int slot = 45; slot < SIZE; slot++) {
      inventory.setItem(slot, filler());
    }

    inventory.setItem(
        INFO_SLOT,
        named(
            Material.CLOCK,
            line("<gold>Death of <name>", Placeholder.unparsed("name", targetName)),
            line(
                "<gray>Time: <white><time></white>",
                Placeholder.unparsed("time", DeathGuiItems.formatTime(death.diedAtMillis()))),
            line(
                "<gray>World: <white><world></white>",
                Placeholder.unparsed("world", death.world())),
            line(
                "<gray>Position: <white><position></white>",
                Placeholder.unparsed("position", DeathGuiItems.formatPosition(death)))));

    placeEquipment(HELMET_SLOT, InventorySnapshot.ARMOR_START + 3, "helmet");
    placeEquipment(CHESTPLATE_SLOT, InventorySnapshot.ARMOR_START + 2, "chestplate");
    placeEquipment(LEGGINGS_SLOT, InventorySnapshot.ARMOR_START + 1, "leggings");
    placeEquipment(BOOTS_SLOT, InventorySnapshot.ARMOR_START, "boots");
    placeEquipment(OFFHAND_SLOT, InventorySnapshot.OFFHAND_SLOT, "offhand item");

    for (int index = 9; index < InventorySnapshot.STORAGE_SLOTS; index++) {
      inventory.setItem(MAIN_START + (index - 9), snapshot[index]);
    }
    for (int index = 0; index < 9; index++) {
      inventory.setItem(HOTBAR_START + index, snapshot[index]);
    }

    inventory.setItem(
        CANCEL_SLOT,
        named(
            Material.RED_STAINED_GLASS_PANE,
            line("<red>Cancel"),
            line("<gray>Close without restoring anything")));
    inventory.setItem(
        RESTORE_SLOT,
        named(
            Material.LIME_STAINED_GLASS_PANE,
            line("<green>Restore this inventory"),
            line(
                "<gray>Replaces the current inventory of <white><name></white>",
                Placeholder.unparsed("name", targetName)),
            line("<gray>Their current items are dropped at their feet")));
  }

  private void placeEquipment(int guiSlot, int snapshotIndex, String label) {
    ItemStack item = snapshot[snapshotIndex];
    inventory.setItem(
        guiSlot,
        InventorySnapshot.isEmpty(item)
            ? named(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                line("<gray>No <label>", Placeholder.unparsed("label", label)))
            : item);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public void onClick(Player viewer, int rawSlot) {
    if (rawSlot == CANCEL_SLOT) {
      viewer.closeInventory();
    } else if (rawSlot == RESTORE_SLOT) {
      restore(viewer);
    }
  }

  private void restore(Player staff) {
    if (restored) {
      return;
    }
    Player target = Bukkit.getPlayer(targetId);
    if (target == null) {
      notRestored(
          staff, "<red><name> is offline. They must be online to restore an inventory.</red>");
      return;
    }
    restored = true;
    // The target may be in another region, so restore on their thread and report back on ours.
    SchedulerUtils.runFor(
        target,
        () -> {
          if (target.isDead()) {
            SchedulerUtils.runFor(
                staff,
                () ->
                    notRestored(
                        staff,
                        "<red><name> is on the respawn screen. Try again once they have respawned."
                            + "</red>"));
            return;
          }
          InventoryRestorer.restore(target, snapshot);
          IceCream.logger.info(
              staff.getName() + " restored death " + death.id() + " of " + targetName);
          SchedulerUtils.runFor(
              staff,
              () -> {
                staff.closeInventory();
                staff.sendMessage(
                    line(
                        "<green>Restored the inventory of <name>.</green>",
                        Placeholder.unparsed("name", targetName)));
              });
        },
        () ->
            SchedulerUtils.runFor(
                staff,
                () ->
                    notRestored(
                        staff,
                        "<red><name> is offline. They must be online to restore an inventory."
                            + "</red>")));
  }

  /** Closes the preview and tells staff why nothing was restored, so they can try again. */
  private void notRestored(Player staff, String template) {
    restored = false;
    staff.closeInventory();
    staff.sendMessage(line(template, Placeholder.unparsed("name", targetName)));
  }

  /** Loads the record asynchronously, then opens the preview for the viewer on their own thread. */
  public static void open(
      Player viewer, DeathRepository repository, UUID targetId, String targetName, long deathId) {
    repository
        .findRecord(deathId, targetId)
        .whenComplete(
            (result, error) ->
                SchedulerUtils.runFor(
                    viewer,
                    () -> {
                      if (!viewer.isOnline()) {
                        return;
                      }
                      if (error != null) {
                        IceCream.logger.log(Level.SEVERE, "Failed to load death " + deathId, error);
                        viewer.sendMessage(
                            line("<red>Could not load that death. Check the console.</red>"));
                        return;
                      }
                      if (result.isEmpty()) {
                        viewer.sendMessage(line("<red>That death record no longer exists.</red>"));
                        return;
                      }
                      DeathRecord record = result.get();
                      ItemStack[] snapshot;
                      try {
                        snapshot = InventorySnapshot.deserialize(record.inventory());
                      } catch (IOException | RuntimeException e) {
                        IceCream.logger.log(
                            Level.SEVERE, "Death " + deathId + " has an unreadable snapshot", e);
                        viewer.sendMessage(
                            line("<red>That record is corrupt and cannot be previewed.</red>"));
                        return;
                      }
                      viewer.openInventory(
                          new DeathPreviewGui(targetId, targetName, record.summary(), snapshot)
                              .getInventory());
                    }));
  }
}
