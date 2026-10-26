package com.nearvanilla.iceCream.modules.mapTools;

import com.google.common.primitives.Ints;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * In-game forms (Paper dialogs) to save a new area, manage an existing one, pick a color and
 * confirm leaving or deleting. When a value is invalid the form reopens with what was typed.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public final class AreaForms {

  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final Component INVALID_HEX =
      MiniMessage.miniMessage().deserialize("<red>Use a hex code such as #ff8800.</red>");

  /** How many nearby claims the confirmation lists before summing up the rest. */
  private static final int NEARBY_SHOWN = 6;

  private static final ClickCallback.Options ONCE =
      ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build();

  /**
   * The values typed into a form, used to fill it in again. {@code typed} is the shape whose
   * coordinates are typed into the form, or null when the shape was drawn in game.
   */
  private record Values(
      boolean marker,
      String id,
      String label,
      String color,
      String owner,
      String description,
      int opacity,
      String icon,
      EditSession.Mode typed,
      List<String> coordinates,
      boolean hidden) {

    static Values of(MapArea area) {
      return new Values(
          area.isMarker(),
          area.id(),
          area.label(),
          area.color(),
          area.owners().isEmpty() ? null : String.join(", ", area.owners()),
          area.description(),
          toPercent(MapToolsUtils.fillOpacity(area)),
          area.icon(),
          area.isMarker() || MapArea.POLYGON.equals(area.shape())
              ? null
              : EditSession.Mode.of(area),
          MapToolsUtils.shapeFields(area),
          area.isHidden());
    }

    /**
     * Reads what was typed. The color and icon come from their pickers, markers have no opacity and
     * only claims being managed have the hide checkbox, so those come from the values the form was
     * opened with.
     */
    static Values of(DialogResponseView response, Values opened) {
      Float opacity = response.getFloat("opacity");
      // Only the manage form of a claim has the checkbox.
      Boolean hidden = response.getBoolean("hidden");
      List<String> coordinates = new ArrayList<>();
      for (int i = 0; i < opened.coordinates().size(); i++) {
        coordinates.add(trimmed(response.getText("coordinate" + i)));
      }
      return new Values(
          opened.marker(),
          trimmed(response.getText("area_id")),
          trimmed(response.getText("label")),
          opened.color(),
          trimmed(response.getText("owner")),
          trimmed(response.getText("description")),
          opacity == null ? opened.opacity() : Math.round(opacity),
          opened.icon(),
          opened.typed(),
          coordinates,
          hidden == null ? opened.hidden() : hidden);
    }

    Values withColor(String color) {
      return new Values(
          marker, id, label, color, owner, description, opacity, icon, typed, coordinates, hidden);
    }

    Values withIcon(String icon) {
      return new Values(
          marker, id, label, color, owner, description, opacity, icon, typed, coordinates, hidden);
    }

    String kind() {
      return marker ? "marker" : "claim";
    }
  }

  private AreaForms() {
    // Utility class; prevent instantiation.
  }

  /**
   * Opens the form that saves the shape the player drew as a new area.
   *
   * @param player the player who drew the shape
   */
  public static void openCreate(Player player) {
    openCreate(player, newValues(isDrawingMarker(player), null, List.of()), null);
  }

  /**
   * Opens the form that creates a claim from typed coordinates, filled in with where the player
   * stands.
   *
   * @param shape the shape to create, not a marker
   */
  public static void openCreate(Player player, EditSession.Mode shape) {
    String here = player.getLocation().getBlockX() + " " + player.getLocation().getBlockZ();
    List<String> coordinates =
        switch (shape) {
          case RECT -> List.of(here, here);
          case CIRCLE -> List.of(here, "10");
          default -> throw new IllegalArgumentException("Only rectangles and circles are typed");
        };
    openCreate(player, newValues(false, shape, coordinates), null);
  }

  private static Values newValues(
      boolean marker, EditSession.Mode typed, List<String> coordinates) {
    return new Values(
        marker,
        "",
        "",
        MapToolsUtils.defaultColor(),
        "",
        "",
        toPercent(MapToolsUtils.defaultFillOpacity()),
        MapToolsUtils.iconNames().stream().findFirst().orElse(null),
        typed,
        coordinates,
        false);
  }

  private static boolean isDrawingMarker(Player player) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    return session != null && session.getMode() == EditSession.Mode.MARKER;
  }

  /**
   * Opens the form that manages an existing area: change its details, edit its shape, or delete it.
   *
   * @param player the player
   * @param area the area to manage
   */
  public static void openManage(Player player, MapArea area) {
    Values current = Values.of(area);
    openManage(player, area.id(), current, current, null);
  }

  private static void openCreate(Player player, Values values, String error) {
    List<DialogInput> inputs = new ArrayList<>(shapeInputs(values));
    inputs.add(idInput(values));
    inputs.addAll(detailInputs(values));
    DialogAction save =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              AreaGeometry geometry;
              if (entered.typed() != null) {
                geometry = typedGeometry(entered);
                if (geometry == null) {
                  openCreate(clicker, entered, coordinatesProblem(entered.typed()));
                  return;
                }
              } else {
                EditSession session = MapToolsModule.sessions.get(clicker.getUniqueId());
                geometry = session == null || !session.isNew() ? null : session.toGeometry();
                if (geometry == null) {
                  clicker.sendMessage(MM.deserialize("<red>Your shape is no longer there.</red>"));
                  return;
                }
              }
              String problem =
                  MapToolsUtils.validate(entered.id(), entered.color(), entered.label(), true);
              if (problem != null) {
                openCreate(clicker, entered, problem);
                return;
              }
              // A typed claim replaces whatever the player was drawing or editing.
              if (entered.typed() != null
                  && !MapToolsUtils.confirmLeave(
                      clicker, () -> openCreate(clicker, entered, null))) {
                return;
              }
              Runnable create =
                  () -> {
                    boolean created =
                        MapToolsUtils.createArea(
                            clicker,
                            entered.id(),
                            geometry,
                            entered.color(),
                            entered.label(),
                            MapAreasStore.parseOwners(entered.owner()),
                            entered.description(),
                            toStored(entered.opacity()),
                            entered.icon());
                    if (!created) return;
                    if (entered.typed() == null) {
                      // A drawn shape stays shown for editing.
                      MapToolsUtils.tryEditing(clicker, MapToolsModule.store.getArea(entered.id()));
                    } else if (MapToolsUtils.isEditing(clicker)) {
                      // A typed one is done once created, so leave the shape that was being drawn.
                      MapToolsUtils.stopEditing(clicker);
                    }
                  };
              if (entered.marker()) {
                create.run();
              } else {
                confirmNearby(
                    clicker, geometry, null, create, () -> openCreate(clicker, entered, null));
              }
            });
    DialogAction pickColor =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              pickColor(
                  clicker,
                  entered.color(),
                  color -> openCreate(clicker, entered.withColor(color), null));
            });
    List<ActionButton> buttons = new ArrayList<>();
    buttons.add(ActionButton.create(Component.text("Create"), null, 150, save));
    if (!values.marker()) {
      buttons.add(ActionButton.create(Component.text("Pick color"), null, 150, pickColor));
    } else {
      buttons.add(pickIconButton(values, (clicker, entered) -> openCreate(clicker, entered, null)));
    }
    DialogType type =
        DialogType.multiAction(
            buttons, ActionButton.create(Component.text("Cancel"), null, 150, null), 2);
    String title =
        values.typed() == null
            ? "New " + values.kind()
            : "New " + values.typed().label().toLowerCase(Locale.ROOT) + " claim";
    show(player, title, body(values, error), inputs, type);
  }

  /** The area with the fields the player changed in the manage form, and the new shape if any. */
  private static MapArea changed(
      MapArea area, Values entered, Values shown, AreaGeometry newShape) {
    MapArea updated = area;
    if (!Objects.equals(entered.label(), shown.label())) {
      updated = updated.withLabel(entered.label());
    }
    if (!Objects.equals(entered.color(), shown.color())) {
      updated = updated.withColor(entered.color());
    }
    if (!Objects.equals(entered.owner(), shown.owner())) {
      updated = updated.withOwners(MapAreasStore.parseOwners(entered.owner()));
    }
    if (!Objects.equals(entered.description(), shown.description())) {
      updated = updated.withDescription(entered.description());
    }
    if (entered.opacity() != shown.opacity()) {
      updated = updated.withOpacity(toStored(entered.opacity()));
    }
    if (!Objects.equals(entered.icon(), shown.icon())) {
      updated = updated.withIcon(entered.icon());
    }
    if (entered.hidden() != shown.hidden()) {
      updated = updated.withHidden(entered.hidden());
    }
    return newShape == null ? updated : updated.withGeometry(newShape);
  }

  /** The shape fields of a new or managed claim, with x and z on one line. */
  private static List<DialogInput> shapeInputs(Values values) {
    if (values.typed() == null) return List.of();
    List<String> labels =
        switch (values.typed()) {
          case RECT -> List.of("Corner 1, x z", "Corner 2, x z");
          default -> List.of("Center, x z", "Radius");
        };
    List<DialogInput> inputs = new ArrayList<>();
    for (int i = 0; i < labels.size() && i < values.coordinates().size(); i++) {
      inputs.add(text("coordinate" + i, labels.get(i), values.coordinates().get(i), 24));
    }
    return inputs;
  }

  /** Reads the shape fields of a claim being managed, or returns null if they are invalid. */
  private static AreaGeometry managedGeometry(MapArea area, Values shown, Values values) {
    List<String> fields = values.coordinates();
    String world = area.world();
    switch (values.typed()) {
      case CIRCLE -> {
        // The form shows a rounded center and radius, so a part that was not edited is kept as it
        // is, and a radius drawn in game can be above the typed limit.
        MapPoint center = area.center();
        if (!Objects.equals(fields.getFirst(), shown.coordinates().getFirst())) {
          int[] typed = parseBlock(fields.getFirst());
          if (typed == null) return null;
          center = MapToolsUtils.blockCenter(typed[0], typed[1]);
        }
        double radius = area.radius();
        if (!Objects.equals(fields.get(1), shown.coordinates().get(1))) {
          Integer typed = fields.get(1) == null ? null : Ints.tryParse(fields.get(1));
          if (typed == null || typed < 1 || typed > MapToolsUtils.MAX_TYPED_SIZE) return null;
          radius = typed;
        }
        return MapToolsUtils.circleShape(world, center, radius);
      }
      case RECT -> {
        int[] first = parseBlock(fields.getFirst());
        int[] second = parseBlock(fields.get(1));
        if (first == null
            || second == null
            || MapToolsUtils.rectangleTooLarge(first[0], first[1], second[0], second[1])) {
          return null;
        }
        return new AreaGeometry(
            world,
            MapToolsUtils.rectangle(first[0], first[1], second[0], second[1]),
            MapArea.RECT,
            null,
            null);
      }
      default -> {
        return null;
      }
    }
  }

  /** Parses {@code x z}, with a space or comma between, or returns null. */
  private static int[] parseBlock(String text) {
    if (text == null) return null;
    String[] parts = text.trim().split("[\\s,]+");
    Integer x = parts.length == 2 ? Ints.tryParse(parts[0]) : null;
    Integer z = parts.length == 2 ? Ints.tryParse(parts[1]) : null;
    return x == null || z == null ? null : new int[] {x, z};
  }

  /** Builds the typed shape in the overworld, or returns null if a coordinate is invalid. */
  private static AreaGeometry typedGeometry(Values values) {
    String world = MapToolsUtils.overworld();
    List<String> typed = values.coordinates();
    return switch (values.typed()) {
      case RECT -> {
        int[] first = parseBlock(typed.get(0));
        int[] second = parseBlock(typed.get(1));
        if (first == null
            || second == null
            || MapToolsUtils.rectangleTooLarge(first[0], first[1], second[0], second[1])) {
          yield null;
        }
        yield new AreaGeometry(
            world,
            MapToolsUtils.rectangle(first[0], first[1], second[0], second[1]),
            MapArea.RECT,
            null,
            null);
      }
      case CIRCLE -> {
        int[] center = parseBlock(typed.get(0));
        Integer radius = typed.get(1) == null ? null : Ints.tryParse(typed.get(1));
        if (center == null
            || radius == null
            || radius < 1
            || radius > MapToolsUtils.MAX_TYPED_SIZE) {
          yield null;
        }
        yield MapToolsUtils.circleShape(
            world, MapToolsUtils.blockCenter(center[0], center[1]), radius);
      }
      default -> null;
    };
  }

  private static String coordinatesProblem(EditSession.Mode shape) {
    int max = MapToolsUtils.MAX_TYPED_SIZE;
    return shape == EditSession.Mode.RECT
        ? "Use whole numbers like 100 -200 for each corner, and a rectangle at most "
            + max
            + " blocks wide and long."
        : "Use whole numbers like 100 -200 for the center, and a radius from 1 to " + max + ".";
  }

  /**
   * @param shown the values the form showed when it was first opened, used to save only the fields
   *     this player changed, so two people with the form open do not undo each other's changes
   * @param values the values to fill the form with
   */
  private static void openManage(
      Player player, String id, Values shown, Values values, String error) {
    DialogAction save =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              MapArea area = MapToolsModule.store.getArea(id);
              if (area == null) {
                clicker.sendMessage(MM.deserialize("<red>This claim no longer exists.</red>"));
                return;
              }
              boolean renaming = !id.equals(entered.id());
              String problem =
                  MapToolsUtils.validate(entered.id(), entered.color(), entered.label(), renaming);
              if (problem != null) {
                openManage(clicker, id, shown, entered, problem);
                return;
              }
              boolean reshaping =
                  entered.typed() != null && !entered.coordinates().equals(shown.coordinates());
              AreaGeometry newShape = null;
              if (reshaping) {
                newShape = managedGeometry(area, shown, entered);
                if (newShape == null) {
                  openManage(clicker, id, shown, entered, coordinatesProblem(entered.typed()));
                  return;
                }
              }
              if (!renaming && !reshaping && changed(area, entered, shown, null).equals(area)) {
                return;
              }
              if ((renaming || reshaping) && MapToolsUtils.blockedByEditor(clicker, id)) return;
              AreaGeometry reshaped = newShape;
              // Changes are applied to the claim as it is once confirmed, in case it changed while
              // the confirmation was open.
              Runnable commit =
                  () -> {
                    MapArea current = MapToolsModule.store.getArea(id);
                    if (current == null) {
                      clicker.sendMessage(
                          MM.deserialize("<red>This claim no longer exists.</red>"));
                      return;
                    }
                    MapArea result = changed(current, entered, shown, reshaped);
                    if (renaming) {
                      MapToolsUtils.renameArea(result, entered.id());
                    } else {
                      MapToolsUtils.saveArea(result);
                    }
                    clicker.sendMessage(
                        MM.deserialize(
                            "<green>Updated <id>.</green>",
                            Placeholder.unparsed("id", entered.id())));
                  };
              if (reshaping) {
                confirmNearby(
                    clicker,
                    reshaped,
                    id,
                    commit,
                    () -> openManage(clicker, id, shown, entered, null));
              } else {
                commit.run();
              }
            });
    DialogAction delete = customClick((response, clicker) -> confirmDelete(clicker, id, shown));

    DialogAction pickColor =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              pickColor(
                  clicker,
                  entered.color(),
                  color -> openManage(clicker, id, shown, entered.withColor(color), null));
            });

    List<ActionButton> buttons = new ArrayList<>();
    // Two columns: the picker next to Save, Delete below.
    if (!values.marker()) {
      buttons.add(ActionButton.create(Component.text("Pick color"), null, 150, pickColor));
    } else {
      buttons.add(
          pickIconButton(
              values, (clicker, entered) -> openManage(clicker, id, shown, entered, null)));
    }
    buttons.add(ActionButton.create(Component.text("Save"), null, 150, save));
    buttons.add(
        ActionButton.create(Component.text("Delete", NamedTextColor.RED), null, 150, delete));
    DialogType type =
        DialogType.multiAction(
            buttons, ActionButton.create(Component.text("Cancel"), null, 150, null), 2);
    List<DialogInput> inputs = new ArrayList<>();
    inputs.add(idInput(values));
    inputs.addAll(shapeInputs(values));
    inputs.addAll(detailInputs(values));
    if (!values.marker()) {
      inputs.add(
          DialogInput.bool("hidden", Component.text("Hide in edit mode"))
              .initial(values.hidden())
              .build());
    }
    show(player, "Manage " + values.kind() + " " + id, body(values, error), inputs, type);
  }

  /**
   * Runs {@code proceed}, but first asks a player to confirm if the claim is close to or overlaps
   * other claims, listing them. Without a player, or with no claims nearby, it just runs.
   *
   * @param sender who is creating or reshaping the claim
   * @param geometry the shape of the claim
   * @param editingId the id of the claim being reshaped, which is left out of the list, or null for
   *     a new claim
   * @param proceed creates the claim or saves the new shape
   */
  public static void confirmNearby(
      CommandSender sender, AreaGeometry geometry, String editingId, Runnable proceed) {
    confirmNearby(sender, geometry, editingId, proceed, null);
  }

  /**
   * Like {@link #confirmNearby(CommandSender, AreaGeometry, String, Runnable)}, but Cancel goes
   * back instead of just closing.
   *
   * @param back reopens the form the player came from, or null to just close the dialog
   */
  public static void confirmNearby(
      CommandSender sender,
      AreaGeometry geometry,
      String editingId,
      Runnable proceed,
      Runnable back) {
    List<MapToolsUtils.NearbyClaim> nearby = MapToolsUtils.nearbyClaims(geometry, editingId);
    if (!(sender instanceof Player player) || nearby.isEmpty()) {
      proceed.run();
      return;
    }

    List<DialogBody> body = new ArrayList<>();
    body.add(
        DialogBody.plainMessage(
            MM.deserialize(
                "<yellow>This claim overlaps or is within <distance> blocks of <count> other"
                    + " <claims>:</yellow>",
                Placeholder.unparsed(
                    "distance", String.valueOf(MapToolsUtils.nearbyClaimDistance())),
                Placeholder.unparsed("count", String.valueOf(nearby.size())),
                Placeholder.unparsed("claims", nearby.size() == 1 ? "claim" : "claims"))));
    for (MapToolsUtils.NearbyClaim near : nearby.stream().limit(NEARBY_SHOWN).toList()) {
      MapArea claim = near.claim();
      body.add(
          DialogBody.plainMessage(
              MM.deserialize(
                  "<white><label></white> <gray>(<id>)<newline>Owner(s): <owners>"
                      + " \u00B7 <away></gray>",
                  Placeholder.unparsed("label", claim.label()),
                  Placeholder.unparsed("id", claim.id()),
                  Placeholder.unparsed(
                      "owners",
                      claim.owners().isEmpty() ? "none" : String.join(", ", claim.owners())),
                  Placeholder.unparsed(
                      "away",
                      near.distance() == 0
                          ? "overlaps"
                          : Math.round(near.distance()) + " blocks away"))));
    }
    if (nearby.size() > NEARBY_SHOWN) {
      body.add(
          DialogBody.plainMessage(
              MM.deserialize(
                  "<gray>...and <more> more.</gray>",
                  Placeholder.unparsed("more", String.valueOf(nearby.size() - NEARBY_SHOWN)))));
    }

    DialogType type =
        DialogType.multiAction(
            List.of(
                ActionButton.create(
                    Component.text(
                        editingId == null ? "Create anyway" : "Save anyway", NamedTextColor.GREEN),
                    null,
                    150,
                    customClick((response, clicker) -> proceed.run()))),
            ActionButton.create(
                Component.text("Cancel"),
                null,
                150,
                back == null ? null : customClick((response, clicker) -> back.run())),
            1);
    show(player, "Claims nearby", body, List.of(), type);
  }

  /**
   * Asks a player with unsaved shape changes whether to save or discard them before continuing.
   * Cancel keeps them editing with their changes.
   *
   * @param then what the player was doing, run after saving or discarding
   */
  public static void confirmLeave(Player player, EditSession session, Runnable then) {
    DialogAction save =
        customClick(
            (response, clicker) -> {
              EditActions.apply(clicker, false);
              then.run();
            });
    DialogAction discard =
        customClick(
            (response, clicker) -> {
              session.setChanged(false);
              then.run();
            });
    DialogType type =
        DialogType.multiAction(
            List.of(
                ActionButton.create(Component.text("Save", NamedTextColor.GREEN), null, 100, save),
                ActionButton.create(
                    Component.text("Discard", NamedTextColor.RED), null, 100, discard)),
            ActionButton.create(Component.text("Cancel"), null, 100, null),
            2);
    show(
        player,
        "Unsaved changes to " + session.getAreaId(),
        List.of(DialogBody.plainMessage(Component.text("Save or discard your shape changes?"))),
        List.of(),
        type);
  }

  /** Asks the player to confirm deleting an area. Cancel just closes the dialog. */
  public static void confirmDelete(Player player, String id) {
    confirmDelete(player, id, null);
  }

  /**
   * @param shown the manage form values to go back to on cancel, or null to just close
   */
  private static void confirmDelete(Player player, String id, Values shown) {
    DialogAction delete =
        customClick(
            (response, clicker) -> {
              MapArea deleted = MapToolsUtils.deleteArea(id);
              if (deleted == null) {
                clicker.sendMessage(MM.deserialize("<red>This claim no longer exists.</red>"));
                return;
              }
              clicker.sendMessage(MapToolsUtils.deletedMessage(deleted));
            });
    DialogAction back =
        customClick(
            (response, clicker) -> {
              MapArea area = MapToolsModule.store.getArea(id);
              if (area != null) openManage(clicker, id, shown, Values.of(area), null);
            });
    DialogType type =
        DialogType.confirmation(
            ActionButton.create(Component.text("Delete", NamedTextColor.RED), null, 100, delete),
            ActionButton.create(Component.text("Cancel"), null, 100, shown == null ? null : back));
    MapArea area = MapToolsModule.store.getArea(id);
    String label = area == null ? id : area.label();
    String shape = area == null ? "" : MapToolsUtils.shapeText(area);
    show(
        player,
        "Delete " + id + "?",
        List.of(
            DialogBody.plainMessage(Component.text(label + ": " + shape)),
            DialogBody.plainMessage(Component.text("This cannot be undone."))),
        List.of(),
        type);
  }

  private static DialogInput idInput(Values values) {
    // Not "id": Paper stores its own callback id under that key in the response.
    return text("area_id", "Id (a-z, 0-9, _ and -)", values.id(), 32);
  }

  private static List<DialogInput> detailInputs(Values values) {
    List<DialogInput> inputs = new ArrayList<>();
    inputs.add(text("label", "Label", values.label(), MapToolsUtils.LABEL_MAX_LENGTH));
    if (!values.marker()) inputs.add(opacityInput(values));
    inputs.add(
        text(
            "owner",
            "Owner(s), comma separated, optional",
            values.owner(),
            MapToolsUtils.TEXT_MAX_LENGTH));
    inputs.add(
        DialogInput.text("description", Component.text("Description (optional)"))
            .initial(fit(values.description(), MapToolsUtils.TEXT_MAX_LENGTH))
            .maxLength(MapToolsUtils.TEXT_MAX_LENGTH)
            .width(300)
            .multiline(TextDialogInput.MultilineOptions.create(4, null))
            .build());
    return inputs;
  }

  private static DialogInput opacityInput(Values values) {
    return DialogInput.numberRange("opacity", Component.text("Fill opacity on the map"), 0, 100)
        .step(5f)
        .initial((float) values.opacity())
        .labelFormat("%s: %s%%")
        .width(300)
        .build();
  }

  /**
   * A button that opens the icon menu, keeping what was typed so far.
   *
   * @param reopen opens the form again with the typed values and the picked icon
   */
  private static ActionButton pickIconButton(Values values, BiConsumer<Player, Values> reopen) {
    DialogAction pick =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              IconMenu.open(
                  clicker, entered.icon(), icon -> reopen.accept(clicker, entered.withIcon(icon)));
            });
    return ActionButton.create(Component.text("Pick icon"), null, 150, pick);
  }

  /**
   * Opens a color picker: a hex code field and a colored button per Minecraft color. Picking a
   * color, or going back, reopens the form the picker came from.
   *
   * @param onPick reopens the form with the chosen color, or the current one when going back
   */
  private static void pickColor(Player player, String current, Consumer<String> onPick) {
    TextColor shown = MapToolsUtils.colorOrDefault(current);

    List<ActionButton> buttons = new ArrayList<>();
    for (String name : NamedTextColor.NAMES.keys()) {
      buttons.add(
          ActionButton.create(
              Component.text("\u25A0 " + name, NamedTextColor.NAMES.value(name)),
              null,
              100,
              customClick((response, clicker) -> onPick.accept(name))));
    }
    buttons.add(
        ActionButton.create(
            Component.text("\u25A0 Use hex", shown),
            null,
            100,
            customClick(
                (response, clicker) -> {
                  String hex = MapToolsUtils.normalizeColor(trimmed(response.getText("hex")));
                  if (hex == null) {
                    clicker.sendMessage(INVALID_HEX);
                    pickColor(clicker, current, onPick);
                    return;
                  }
                  onPick.accept(hex);
                })));
    DialogType type =
        DialogType.multiAction(
            buttons,
            ActionButton.create(
                Component.text("Back"),
                null,
                100,
                customClick((response, clicker) -> onPick.accept(current))),
            4);
    List<DialogBody> body =
        List.of(
            DialogBody.plainMessage(
                MM.deserialize(
                    "Current: <color>\u25A0 <hex></color>",
                    Placeholder.styling("color", shown),
                    Placeholder.unparsed("hex", shown.asHexString()))));
    show(
        player,
        "Pick a color",
        body,
        List.of(text("hex", "Hex code", shown.asHexString(), 7)),
        type);
  }

  /** The form body: an error if there is one, then the chosen color, or a marker's icon. */
  private static List<DialogBody> body(Values values, String error) {
    List<DialogBody> body = new ArrayList<>();
    if (error != null) {
      body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED)));
    }
    if (values.typed() != null) {
      body.add(
          DialogBody.plainMessage(
              Component.text("Created in the overworld.", NamedTextColor.GRAY)));
    }
    // Markers have an icon instead of a color.
    if (!values.marker()) {
      String color = values.color() == null ? MapToolsUtils.defaultColor() : values.color();
      body.add(
          DialogBody.plainMessage(
              MM.deserialize(
                  "Color: <color>\u25A0 <name></color>",
                  Placeholder.styling("color", MapToolsUtils.colorOrDefault(color)),
                  Placeholder.unparsed("name", color))));
    } else {
      String icon = values.icon();
      Component description =
          Component.text("Icon: " + (icon == null ? "none, shown as a dot" : icon));
      body.add(
          DialogBody.item(
                  new ItemStack(icon == null ? Material.BARRIER : MapToolsUtils.iconItem(icon)))
              .description(DialogBody.plainMessage(description))
              .showTooltip(false)
              .build());
    }
    return body;
  }

  private static DialogInput text(String key, String label, String initial, int maxLength) {
    return DialogInput.text(key, Component.text(label))
        .initial(fit(initial, maxLength))
        .maxLength(maxLength)
        .width(300)
        .build();
  }

  private static void show(
      Player player,
      String title,
      List<DialogBody> body,
      List<DialogInput> inputs,
      DialogType type) {
    DialogBase base = DialogBase.builder(Component.text(title)).body(body).inputs(inputs).build();
    player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
  }

  private static int toPercent(double opacity) {
    return (int) Math.round(opacity * 100);
  }

  /** The opacity to store: null when it matches the configured default, so that default applies. */
  private static Double toStored(int percent) {
    return percent == toPercent(MapToolsUtils.defaultFillOpacity()) ? null : percent / 100.0;
  }

  /** The text cut to the input's max length, since a longer initial value breaks the dialog. */
  private static String fit(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) : text;
  }

  /**
   * A dialog button action that does nothing once the player has lost the manage permission, since
   * a form can stay open long after it was shown.
   */
  private static DialogAction customClick(BiConsumer<DialogResponseView, Player> callback) {
    return DialogAction.customClick(
        (response, audience) -> {
          if (audience instanceof Player player && player.hasPermission(MapToolsUtils.PERMISSION)) {
            callback.accept(response, player);
          }
        },
        ONCE);
  }

  private static String trimmed(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
