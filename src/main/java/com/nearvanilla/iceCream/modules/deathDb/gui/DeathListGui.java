package com.nearvanilla.iceCream.modules.deathDb.gui;

import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.filler;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.line;
import static com.nearvanilla.iceCream.modules.deathDb.gui.DeathGuiItems.named;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathPage;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Paginated chest GUI listing a player's recorded deaths, newest first. */
public final class DeathListGui implements DeathGui {
  public static final int PAGE_SIZE = 45;
  private static final int SIZE = 54;
  private static final int PREVIOUS_SLOT = 45;
  private static final int INFO_SLOT = 49;
  private static final int NEXT_SLOT = 53;

  private final DeathRepository repository;
  private final UUID targetId;
  private final String targetName;
  private final DeathPage page;
  private final Map<Integer, Long> deathIdBySlot = new HashMap<>();
  private final Inventory inventory;

  private DeathListGui(
      DeathRepository repository, UUID targetId, String targetName, DeathPage page) {
    this.repository = repository;
    this.targetId = targetId;
    this.targetName = targetName;
    this.page = page;
    this.inventory =
        Bukkit.createInventory(
            this,
            SIZE,
            line(
                "<dark_gray>Deaths of <name> (<page>/<pages>)",
                Placeholder.unparsed("name", targetName),
                Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                Placeholder.unparsed("pages", String.valueOf(page.pageCount()))));
    populate();
  }

  private void populate() {
    List<DeathSummary> entries = page.entries();
    for (int slot = 0; slot < entries.size(); slot++) {
      DeathSummary death = entries.get(slot);
      inventory.setItem(
          slot,
          named(
              Material.SKELETON_SKULL,
              line(
                  "<red><time></red>",
                  Placeholder.unparsed("time", DeathGuiItems.formatTime(death.diedAtMillis()))),
              line(
                  "<gray>World: <white><world></white>",
                  Placeholder.unparsed("world", death.world())),
              line(
                  "<gray>Position: <white><position></white>",
                  Placeholder.unparsed("position", DeathGuiItems.formatPosition(death))),
              line("<dark_gray>Click to preview this inventory")));
      deathIdBySlot.put(slot, death.id());
    }
    for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
      inventory.setItem(slot, filler());
    }
    if (page.page() > 0) {
      inventory.setItem(PREVIOUS_SLOT, named(Material.ARROW, line("<yellow>Previous page")));
    }
    if (page.page() < page.pageCount() - 1) {
      inventory.setItem(NEXT_SLOT, named(Material.ARROW, line("<yellow>Next page")));
    }
    inventory.setItem(
        INFO_SLOT,
        named(
            Material.PAPER,
            line(
                "<gold>Page <page> of <pages>",
                Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                Placeholder.unparsed("pages", String.valueOf(page.pageCount()))),
            line(
                "<gray><total> recorded deaths",
                Placeholder.unparsed("total", String.valueOf(page.totalRecords())))));
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public void onClick(Player viewer, int rawSlot) {
    if (rawSlot == PREVIOUS_SLOT && page.page() > 0) {
      open(viewer, repository, targetId, targetName, page.page() - 1);
    } else if (rawSlot == NEXT_SLOT && page.page() < page.pageCount() - 1) {
      open(viewer, repository, targetId, targetName, page.page() + 1);
    } else {
      Long deathId = deathIdBySlot.get(rawSlot);
      if (deathId != null) {
        DeathPreviewGui.open(viewer, repository, targetId, targetName, deathId);
      }
    }
  }

  /** Loads the page asynchronously, then opens the GUI for the viewer on the server thread. */
  public static void open(
      Player viewer, DeathRepository repository, UUID targetId, String targetName, int page) {
    repository
        .findPage(targetId, page, PAGE_SIZE)
        .whenComplete(
            (result, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        IceCream.instance,
                        () -> {
                          if (!viewer.isOnline()) {
                            return;
                          }
                          if (error != null) {
                            IceCream.logger.log(
                                Level.SEVERE, "Failed to load deaths of " + targetName, error);
                            viewer.sendMessage(
                                line(
                                    "<red>Could not load death records. Check the console.</red>"));
                            return;
                          }
                          if (result.totalRecords() == 0) {
                            viewer.sendMessage(
                                line(
                                    "<gray><name> has no recorded deaths.</gray>",
                                    Placeholder.unparsed("name", targetName)));
                            return;
                          }
                          viewer.openInventory(
                              new DeathListGui(repository, targetId, targetName, result)
                                  .getInventory());
                        }));
  }
}
