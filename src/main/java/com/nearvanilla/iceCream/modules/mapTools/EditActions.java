package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles clicks in edit mode. Drawing a new shape uses left click and shift + left click to place
 * its blocks. Editing an existing area picks up a corner with one left click and moves it with the
 * next; changes are kept in the session until they are saved with {@code /maptools save}.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public final class EditActions {

  private static final MiniMessage MM = MiniMessage.miniMessage();

  /** How far outside a claim, in blocks, a left click stops editing it. */
  private static final double LEAVE_DISTANCE = 8;

  private EditActions() {
    // Utility class; prevent instantiation.
  }

  /**
   * Applies a left click on a block.
   *
   * @param player the editing player
   * @param block the clicked block
   * @param sneaking whether the player held shift
   */
  public static void click(Player player, Location block, boolean sneaking) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null) return;

    if (session.isViewing()) {
      editAreaAt(player, block, null);
      return;
    } else if (session.isNew()) {
      drawNew(player, session, block, sneaking);
    } else if (!block.getWorld().getKey().asString().equals(session.getWorld())) {
      send(player, "<red>This claim is in another world.</red>");
      return;
    } else {
      editExisting(player, session, block, sneaking);
    }
    AreaDisplays.refreshPreview(player);
  }

  /**
   * Handles a left click that hits no block, such as at the sky. While editing a claim it puts down
   * the picked up corner, or with nothing picked up stops editing the claim, like clicking far
   * outside it.
   *
   * @param player the editing player
   * @param sneaking whether the player held shift
   */
  public static void clickNothing(Player player, boolean sneaking) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (sneaking || session == null || !session.isEditingArea()) return;

    if (session.getPicked() != null) {
      session.setPicked(null);
      AreaDisplays.refreshPreview(player);
      return;
    }
    leave(player);
  }

  /** Stops editing the claim and goes back to edit mode, asking first if there are changes. */
  private static void leave(Player player) {
    Runnable toViewing = () -> MapToolsUtils.startEditing(player, EditSession.view());
    if (MapToolsUtils.confirmLeave(player, toViewing)) toViewing.run();
  }

  /**
   * Starts editing the area at the clicked block, used in view mode and to switch between areas
   * while editing.
   *
   * @param currentId the area already being edited, which is skipped, or null
   * @return true if an area was found and is now being edited
   */
  private static boolean editAreaAt(Player player, Location block, String currentId) {
    MapArea area =
        MapToolsUtils.areaAt(
            block.getWorld().getKey().asString(),
            block.getBlockX() + 0.5,
            block.getBlockZ() + 0.5,
            currentId);
    if (area == null) return false;
    MapToolsUtils.tryEditing(player, area);
    return true;
  }

  private static void drawNew(
      Player player, EditSession session, Location block, boolean sneaking) {
    switch (session.getMode()) {
      case RECT -> {
        if (sneaking) {
          session.setCorner2(block);
        } else {
          session.setCorner1(block);
        }
      }
      case POLYGON -> {
        if (!sneaking) {
          session.addPoint(block);
        } else {
          session.undoPoint();
        }
      }
      case CIRCLE -> {
        if (!sneaking) {
          session.setCenter(block);
        } else if (session.getCenter() != null) {
          setRadius(session, block);
        }
      }
      case MARKER -> {
        if (!sneaking) session.setCenter(block);
      }
    }
    if (session.toGeometry() != null && session.markHinted()) {
      player.sendMessage(
          MM.deserialize(
              "<gray>Run <usage> to fill in the details and save it.</gray>",
              Placeholder.unparsed("usage", "/maptools save")));
    }
  }

  private static void editExisting(
      Player player, EditSession session, Location block, boolean sneaking) {
    Integer index = session.indexAt(block);
    Integer picked = session.getPicked();
    // With nothing picked up, a left click inside another area switches to editing that one, and
    // one well outside this claim stops editing it.
    if (!sneaking && index == null && picked == null) {
      if (editAreaAt(player, block, session.getAreaId())) return;
      if (session.distanceToOutline(block) > LEAVE_DISTANCE) {
        leave(player);
        return;
      }
    }
    switch (session.getMode()) {
      case RECT -> {
        if (sneaking) return;
        if (index != null) {
          session.setPicked(index);
        } else if (picked != null) {
          session.moveRectCorner(picked, block);
          change(session);
        }
      }
      case POLYGON -> {
        if (sneaking && index != null) {
          if (session.getPointCount() <= 3) {
            send(player, "<red>A polygon needs at least 3 points.</red>");
            return;
          }
          session.removePoint(index);
          change(session);
        } else if (sneaking) {
          // Insert into the edge nearest the click, so clicking on a line splits that line.
          session.insertPoint(session.nearestEdge(block) + 1, block);
          change(session);
        } else if (index != null) {
          session.setPicked(index);
        } else if (picked != null) {
          session.setPoint(picked, block);
          change(session);
        }
      }
      case CIRCLE -> {
        if (sneaking) {
          setRadius(session, block);
          change(session);
        } else if (index != null) {
          session.setPicked(index);
        } else if (picked != null) {
          session.setCenter(block);
          change(session);
        }
      }
      case MARKER -> {
        if (sneaking) return;
        if (index != null) {
          session.setPicked(index);
        } else if (picked != null) {
          session.setCenter(block);
          change(session);
        }
      }
    }
  }

  private static void setRadius(EditSession session, Location block) {
    Location center = session.getCenter();
    int radius =
        (int)
            Math.max(
                1,
                Math.round(
                    Math.hypot(
                        block.getBlockX() - center.getBlockX(),
                        block.getBlockZ() - center.getBlockZ())));
    session.setRadius(radius);
  }

  /** Records a shape change in the session without saving it; the action bar shows it. */
  private static void change(EditSession session) {
    session.setPicked(null);
    session.setChanged(true);
  }

  /**
   * Saves the changes of the area the player is editing to the file and the map.
   *
   * @param player the editing player
   */
  public static void apply(Player player) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null || !session.isEditingArea()) {
      send(player, "<red>You are not editing a claim.</red>");
      return;
    }
    if (!session.hasChanges()) {
      send(player, "<gray>There are no changes to save.</gray>");
      return;
    }
    MapArea area = MapToolsModule.store.getArea(session.getAreaId());
    AreaGeometry geometry = session.toGeometry();
    if (area == null || geometry == null) {
      send(player, "<red>This claim no longer exists.</red>");
      return;
    }

    session.setChanged(false);
    // Saving finishes editing this area; go back to edit mode so the next one can be clicked.
    MapToolsUtils.startEditing(player, EditSession.view());
    MapToolsUtils.saveArea(area.withGeometry(geometry));
    player.sendMessage(
        MM.deserialize(
            "<green>Saved the changes to <id>.</green>", Placeholder.unparsed("id", area.id())));
  }

  private static void send(Player player, String message) {
    player.sendMessage(MM.deserialize(message));
  }
}
