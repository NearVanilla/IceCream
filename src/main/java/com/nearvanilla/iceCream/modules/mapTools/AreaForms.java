package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
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
  private static final ClickCallback.Options ONCE =
      ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build();

  /** The values typed into a form, used to fill it in again. */
  private record Values(
      boolean marker,
      String id,
      String label,
      String color,
      String owner,
      String description,
      int opacity,
      String icon) {

    static Values of(MapArea area) {
      return new Values(
          area.isMarker(),
          area.id(),
          area.label(),
          area.color(),
          area.owners().isEmpty() ? null : String.join(", ", area.owners()),
          area.description(),
          toPercent(MapToolsUtils.fillOpacity(area)),
          area.icon());
    }

    /**
     * Reads what was typed. The color and icon come from their pickers, and markers have no
     * opacity, so those come from the values the form was opened with.
     */
    static Values of(DialogResponseView response, Values opened) {
      Float opacity = response.getFloat("opacity");
      return new Values(
          opened.marker(),
          trimmed(response.getText("area_id")),
          trimmed(response.getText("label")),
          opened.color(),
          trimmed(response.getText("owner")),
          trimmed(response.getText("description")),
          opacity == null ? opened.opacity() : Math.round(opacity),
          opened.icon());
    }

    Values withColor(String color) {
      return new Values(marker, id, label, color, owner, description, opacity, icon);
    }

    Values withIcon(String icon) {
      return new Values(marker, id, label, color, owner, description, opacity, icon);
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
    openCreate(
        player,
        new Values(
            isDrawingMarker(player),
            "",
            "",
            MapToolsUtils.defaultColor(),
            "",
            "",
            toPercent(MapToolsUtils.defaultFillOpacity()),
            MapToolsUtils.iconNames().stream().findFirst().orElse(null)),
        null);
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
    List<DialogInput> inputs = new ArrayList<>();
    inputs.add(idInput(values));
    inputs.addAll(detailInputs(values));
    DialogAction save =
        customClick(
            (response, clicker) -> {
              Values entered = Values.of(response, values);
              EditSession session = MapToolsModule.sessions.get(clicker.getUniqueId());
              AreaGeometry geometry =
                  session == null || !session.isNew() ? null : session.toGeometry();
              if (geometry == null) {
                clicker.sendMessage(MM.deserialize("<red>Your shape is no longer there.</red>"));
                return;
              }
              String problem =
                  MapToolsUtils.validate(entered.id(), entered.color(), entered.label(), true);
              if (problem != null) {
                openCreate(clicker, entered, problem);
                return;
              }
              if (MapToolsUtils.createArea(
                  clicker,
                  entered.id(),
                  geometry,
                  entered.color(),
                  entered.label(),
                  MapAreasStore.parseOwners(entered.owner()),
                  entered.description(),
                  toStored(entered.opacity()),
                  entered.icon())) {
                MapToolsUtils.tryEditing(clicker, MapToolsModule.store.getArea(entered.id()));
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
    show(player, "New " + values.kind(), body(values, error), inputs, type);
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
              if (renaming) {
                if (MapToolsUtils.blockedByEditor(clicker, id)) return;
                MapToolsUtils.renameArea(updated, entered.id());
              } else if (updated != area) {
                MapToolsUtils.saveArea(updated);
              } else {
                return;
              }
              clicker.sendMessage(
                  MM.deserialize(
                      "<green>Updated <id>.</green>", Placeholder.unparsed("id", entered.id())));
            });
    DialogAction editShape =
        customClick(
            (response, clicker) -> {
              MapArea area = MapToolsModule.store.getArea(id);
              if (area != null) MapToolsUtils.tryEditing(clicker, area);
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
    // Two columns: the picker and shape editing on top, Save next to Delete below.
    if (!values.marker()) {
      buttons.add(ActionButton.create(Component.text("Pick color"), null, 150, pickColor));
    } else {
      buttons.add(
          pickIconButton(
              values, (clicker, entered) -> openManage(clicker, id, shown, entered, null)));
    }
    buttons.add(
        ActionButton.create(
            Component.text(values.marker() ? "Move" : "Edit shape"), null, 150, editShape));
    buttons.add(ActionButton.create(Component.text("Save"), null, 150, save));
    buttons.add(
        ActionButton.create(Component.text("Delete", NamedTextColor.RED), null, 150, delete));
    DialogType type =
        DialogType.multiAction(
            buttons, ActionButton.create(Component.text("Cancel"), null, 150, null), 2);
    List<DialogInput> inputs = new ArrayList<>();
    inputs.add(idInput(values));
    inputs.addAll(detailInputs(values));
    show(player, "Manage " + values.kind() + " " + id, body(values, error), inputs, type);
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
              EditActions.apply(clicker);
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
                  String hex = trimmed(response.getText("hex"));
                  if (hex != null && !hex.startsWith("#")) hex = "#" + hex;
                  if (hex == null || MapToolsUtils.parseColor(hex) == null) {
                    clicker.sendMessage(INVALID_HEX);
                    pickColor(clicker, current, onPick);
                    return;
                  }
                  onPick.accept(hex.toLowerCase(Locale.ROOT));
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
