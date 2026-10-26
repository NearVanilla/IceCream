package com.nearvanilla.iceCream.modules.mapTools.commands;

import com.google.common.primitives.Ints;
import com.nearvanilla.iceCream.modules.mapTools.AreaForms;
import com.nearvanilla.iceCream.modules.mapTools.EditActions;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotation.specifier.Range;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

/**
 * Commands for claims and markers on squaremap. Each command names the kind after the action, such
 * as {@code /maptools tp claim <id>}. Shapes are drawn and edited in game with {@code /maptools
 * create} and {@code /maptools editmode}; rectangles can also be made from typed coordinates.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 * @see MapToolsModule
 * @see EditSession
 */
@SuppressWarnings("unused")
public class MapToolsCommand {

  /** Whether a command is about claims or markers. */
  public enum Kind {
    CLAIM,
    MARKER;

    boolean matches(MapArea area) {
      return area.isMarker() == (this == MARKER);
    }

    String label() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private static final String PERMISSION = MapToolsUtils.PERMISSION;
  private static final MiniMessage MM = MiniMessage.miniMessage();

  private static final Component PLAYERS_ONLY =
      MM.deserialize("<red>This command can only be used by players.</red>");
  private static final Component NOTHING_TO_SAVE =
      MM.deserialize(
          "<red>Nothing to save. Draw with /maptools create claim or create marker, or edit in"
              + " /maptools editmode.</red>");
  private static final Component EDIT_MODE_OFF = MM.deserialize("<red>Stopped.</red>");
  private static final Component EDIT_USAGE =
      MM.deserialize(
          "<gray>Use /maptools create claim or create marker to draw something new, or"
              + " /maptools editmode to change it.</gray>");

  /** How many entries {@code /maptools list} shows without a search. */
  private static final int LIST_LIMIT = 10;

  private static final Component POINTS_USAGE =
      MM.deserialize(
          "<red>Use points \\<x z; x z; x z> with at least 3 points that are not in one"
              + " line.</red>");
  private static final String RECT_TOO_LARGE_TEXT =
      "A rectangle can be at most " + MapToolsUtils.MAX_TYPED_SIZE + " blocks wide and long.";
  private static final Component RECT_TOO_LARGE =
      MM.deserialize("<red>" + RECT_TOO_LARGE_TEXT + "</red>");
  private static final Component INVALID_COLOR =
      MM.deserialize("<red>Use a color name such as gold, or a hex code such as ff8800.</red>");

  // Shapes and positions typed as coordinates have their own set commands.
  private static final List<String> CLAIM_FIELDS =
      List.of("id", "label", "color", "owners", "description", "opacity", "points");
  private static final List<String> MARKER_FIELDS =
      List.of("id", "label", "color", "owners", "description", "icon");

  @Suggestions("area_ids")
  public List<String> suggestIds(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return areas(typedKind(ctx)).map(MapArea::id).toList();
  }

  @Suggestions("claim_search")
  public List<String> suggestClaimSearch(
      CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return searchWords(Kind.CLAIM);
  }

  @Suggestions("marker_search")
  public List<String> suggestMarkerSearch(
      CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return searchWords(Kind.MARKER);
  }

  @Suggestions("area_fields")
  public List<String> suggestFields(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return typedKind(ctx) == Kind.MARKER ? MARKER_FIELDS : CLAIM_FIELDS;
  }

  @Suggestions("map_colors")
  public List<String> suggestColors(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return colorSuggestions(null);
  }

  /** The block the player stands on, instead of the default 0 to 9 number suggestions. */
  @Suggestions("block_x")
  public List<String> suggestBlockX(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return List.of(String.valueOf(ctx.sender().getLocation().getBlockX()));
  }

  @Suggestions("block_z")
  public List<String> suggestBlockZ(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return List.of(String.valueOf(ctx.sender().getLocation().getBlockZ()));
  }

  /** No suggestions, instead of the default 0 to 9 number suggestions. */
  @Suggestions("none")
  public List<String> suggestNothing(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return List.of();
  }

  /** Suggests the current value of the field being set. */
  @Suggestions("area_values")
  public List<String> suggestValues(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    // Read from the raw input, since earlier arguments are not always parsed yet while suggesting.
    String[] words = ctx.rawInput().input().trim().split("\\s+");
    if (words.length < 5) return List.of();
    MapArea area = MapToolsModule.store.getArea(words[3]);
    if (area == null) return List.of();
    return switch (words[4].toLowerCase(Locale.ROOT)) {
      case "color" -> colorSuggestions(area);
      case "label" -> area.label() == null ? List.of() : List.of(area.label());
      case "owners" ->
          area.owners().isEmpty() ? List.of() : List.of(String.join(", ", area.owners()));
      case "description" -> area.description() == null ? List.of() : List.of(area.description());
      case "icon" -> MapToolsUtils.iconNames();
      case "points" ->
          MapArea.POLYGON.equals(area.shape())
              ? List.of(MapToolsUtils.pointsText(area))
              : List.of();
      default -> List.of();
    };
  }

  @Command("maptools stop")
  @CommandDescription("Leave create or edit mode.")
  @Permission(PERMISSION)
  public void stop(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;
    if (!MapToolsUtils.confirmLeave(player, () -> stop(stack))) return;

    if (!MapToolsUtils.isEditing(player)) {
      player.sendMessage(EDIT_USAGE);
      return;
    }
    MapToolsUtils.stopEditing(player);
    player.sendMessage(EDIT_MODE_OFF);
  }

  @Command("maptools editmode")
  @CommandDescription("Toggle edit mode: show all claims, left click inside one to edit it.")
  @Permission(PERMISSION)
  public void editMode(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;
    // Leaving an edited claim through editmode means going back to viewing, not toggling off.
    Runnable toViewing = () -> MapToolsUtils.startEditing(player, EditSession.view());
    if (!MapToolsUtils.confirmLeave(player, toViewing)) return;

    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session != null && session.isViewing()) {
      MapToolsUtils.stopEditing(player);
      player.sendMessage(EDIT_MODE_OFF);
      return;
    }
    MapToolsUtils.startEditing(player, EditSession.view());
  }

  @Command("maptools create claim rect")
  @CommandDescription("Open a form to create a rectangular claim from coordinates.")
  @Permission(PERMISSION)
  public void createRectangle(CommandSourceStack stack) {
    openCreateForm(stack, Mode.RECT);
  }

  @Command("maptools create claim polygon")
  @CommandDescription("Draw a polygon claim in game, then save it with /maptools save.")
  @Permission(PERMISSION)
  public void createPolygon(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player != null) startNew(player, Mode.POLYGON);
  }

  @Command("maptools create claim circle")
  @CommandDescription("Open a form to create a circular claim from coordinates.")
  @Permission(PERMISSION)
  public void createCircle(CommandSourceStack stack) {
    openCreateForm(stack, Mode.CIRCLE);
  }

  @Command("maptools create marker")
  @CommandDescription("Place a new marker with an icon.")
  @Permission(PERMISSION)
  public void createMarker(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player != null) startNew(player, Mode.MARKER);
  }

  @Command("maptools save")
  @CommandDescription("Save what you are doing: a new shape opens a form, edits are saved.")
  @Permission(PERMISSION)
  public void save(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session != null && session.isEditingArea()) {
      EditActions.apply(player);
    } else if (session != null && session.isNew() && session.toGeometry() != null) {
      AreaForms.openCreate(player);
    } else {
      player.sendMessage(NOTHING_TO_SAVE);
    }
  }

  @Command("maptools undo")
  @CommandDescription("Undo the last change to the shape you are drawing or editing.")
  @Permission(PERMISSION)
  public void undo(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;
    EditActions.undo(player);
  }

  @Command("maptools redo")
  @CommandDescription("Redo the last change you undid.")
  @Permission(PERMISSION)
  public void redo(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;
    EditActions.redo(player);
  }

  @Command("maptools manage")
  @CommandDescription("Open the form of the claim or marker you are editing.")
  @Permission(PERMISSION)
  public void manageEdited(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    String id = sessionAreaId(player);
    MapArea area = id == null ? null : MapToolsModule.store.getArea(id);
    if (area == null) {
      player.sendMessage(
          MM.deserialize(
              "<red>Use /maptools manage claim|marker \\<id>, or run this while editing.</red>"));
      return;
    }
    AreaForms.openManage(player, area);
  }

  @Command("maptools manage <kind> <id>")
  @CommandDescription("Open a form to change, reshape or delete a claim or marker.")
  @Permission(PERMISSION)
  public void manage(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    MapArea area = find(player, kind, id);
    if (area != null) AreaForms.openManage(player, area);
  }

  @Command("maptools create claim rect <x1> <z1> <x2> <z2> <id> <color> <label>")
  @CommandDescription("Create a rectangular claim from coordinates.")
  @Permission(PERMISSION)
  public void createRect(
      CommandSourceStack stack,
      @Argument(value = "x1", suggestions = "block_x") int x1,
      @Argument(value = "z1", suggestions = "block_z") int z1,
      @Argument(value = "x2", suggestions = "block_x") int x2,
      @Argument(value = "z2", suggestions = "block_z") int z2,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    if (MapToolsUtils.rectangleTooLarge(x1, z1, x2, z2)) {
      stack.getSender().sendMessage(RECT_TOO_LARGE);
      return;
    }
    AreaGeometry geometry =
        new AreaGeometry(
            MapToolsUtils.overworld(),
            MapToolsUtils.rectangle(x1, z1, x2, z2),
            MapArea.RECT,
            null,
            null);
    createClaim(stack.getSender(), id, geometry, color, label);
  }

  @Command("maptools create claim circle <x> <z> <radius> <id> <color> <label>")
  @CommandDescription("Create a circular claim from coordinates.")
  @Permission(PERMISSION)
  public void createCircle(
      CommandSourceStack stack,
      @Argument(value = "x", suggestions = "block_x") int x,
      @Argument(value = "z", suggestions = "block_z") int z,
      @Argument(value = "radius", suggestions = "none") @Range(min = "1", max = "3000") int radius,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    AreaGeometry geometry =
        MapToolsUtils.circleShape(
            MapToolsUtils.overworld(), MapToolsUtils.blockCenter(x, z), radius);
    createClaim(stack.getSender(), id, geometry, color, label);
  }

  @Command("maptools create claim polygon <points> <id> <color> <label>")
  @CommandDescription("Create a polygon claim from coordinates, given as \"x z; x z; x z\".")
  @Permission(PERMISSION)
  public void createPolygon(
      CommandSourceStack stack,
      @Argument("points") @Quoted String points,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    AreaGeometry geometry = MapToolsUtils.parsePoints(MapToolsUtils.overworld(), points);
    if (geometry == null) {
      stack
          .getSender()
          .sendMessage(
              MM.deserialize(
                  "<red>Use points in quotes, such as \"0 0; 10 0; 10 10\", with at least 3"
                      + " points that are not in one line.</red>"));
      return;
    }
    createClaim(stack.getSender(), id, geometry, color, label);
  }

  @Command("maptools set <kind> <id> rect <x1> <z1> <x2> <z2>")
  @CommandDescription("Replace a claim's shape with a rectangle between two corners.")
  @Permission(PERMISSION)
  public void setRect(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "x1", suggestions = "block_x") int x1,
      @Argument(value = "z1", suggestions = "block_z") int z1,
      @Argument(value = "x2", suggestions = "block_x") int x2,
      @Argument(value = "z2", suggestions = "block_z") int z2) {
    setShape(
        stack.getSender(),
        kind,
        Kind.CLAIM,
        id,
        area ->
            MapToolsUtils.rectangleTooLarge(x1, z1, x2, z2)
                ? null
                : new AreaGeometry(
                    area.world(),
                    MapToolsUtils.rectangle(x1, z1, x2, z2),
                    MapArea.RECT,
                    null,
                    null),
        RECT_TOO_LARGE_TEXT);
  }

  @Command("maptools set <kind> <id> circle <x> <z> <radius>")
  @CommandDescription("Replace a claim's shape with a circle.")
  @Permission(PERMISSION)
  public void setCircle(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "x", suggestions = "block_x") int x,
      @Argument(value = "z", suggestions = "block_z") int z,
      @Argument(value = "radius", suggestions = "none") @Range(min = "1", max = "3000")
          int radius) {
    setShape(
        stack.getSender(),
        kind,
        Kind.CLAIM,
        id,
        area -> MapToolsUtils.circleShape(area.world(), MapToolsUtils.blockCenter(x, z), radius),
        null);
  }

  @Command("maptools set <kind> <id> radius <radius>")
  @CommandDescription("Resize a circle, keeping its center.")
  @Permission(PERMISSION)
  public void setRadius(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "radius", suggestions = "none") @Range(min = "1", max = "3000")
          int radius) {
    setShape(
        stack.getSender(),
        kind,
        Kind.CLAIM,
        id,
        area ->
            area.isCircle() ? MapToolsUtils.circleShape(area.world(), area.center(), radius) : null,
        "Only circles have a center and radius.");
  }

  @Command("maptools set <kind> <id> center <x> <z>")
  @CommandDescription("Move a circle, keeping its size.")
  @Permission(PERMISSION)
  public void setCenter(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "x", suggestions = "block_x") int x,
      @Argument(value = "z", suggestions = "block_z") int z) {
    setShape(
        stack.getSender(),
        kind,
        Kind.CLAIM,
        id,
        area ->
            area.isCircle()
                ? MapToolsUtils.circleShape(
                    area.world(), MapToolsUtils.blockCenter(x, z), area.radius())
                : null,
        "Only circles have a center and radius.");
  }

  @Command("maptools set <kind> <id> move <x> <z>")
  @CommandDescription("Move a marker.")
  @Permission(PERMISSION)
  public void setMove(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "x", suggestions = "block_x") int x,
      @Argument(value = "z", suggestions = "block_z") int z) {
    MapPoint spot = MapToolsUtils.blockCenter(x, z);
    setShape(
        stack.getSender(),
        kind,
        Kind.MARKER,
        id,
        area -> new AreaGeometry(area.world(), List.of(spot), MapArea.MARKER, spot, null),
        null);
  }

  @Command("maptools set <kind> <id> <field> [value]")
  @CommandDescription("Change the details of a claim or marker, or a polygon's points.")
  @Permission(PERMISSION)
  public void set(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "field", suggestions = "area_fields") String field,
      @Argument(value = "value", suggestions = "area_values") @Greedy String value) {
    CommandSender sender = stack.getSender();
    MapArea area = find(sender, kind, id);
    if (area == null) return;

    String lowerField = field.toLowerCase(Locale.ROOT);
    List<String> fields = kind == Kind.MARKER ? MARKER_FIELDS : CLAIM_FIELDS;
    if (!fields.contains(lowerField)) {
      sender.sendMessage(
          MM.deserialize(
              "<red>Use <fields>.</red>",
              Placeholder.unparsed("fields", String.join(", ", fields))));
      return;
    }

    // Longer values would not fit in the manage form.
    boolean isLabel = field.equalsIgnoreCase("label");
    int maxLength = isLabel ? MapToolsUtils.LABEL_MAX_LENGTH : MapToolsUtils.TEXT_MAX_LENGTH;
    if ((isLabel || field.equalsIgnoreCase("owners") || field.equalsIgnoreCase("description"))
        && value != null
        && value.length() > maxLength) {
      sender.sendMessage(
          MM.deserialize(
              "<red>That is too long; use up to <max> characters.</red>",
              Placeholder.unparsed("max", String.valueOf(maxLength))));
      return;
    }

    if (lowerField.equals("id")) {
      rename(sender, kind, area, value);
      return;
    }

    MapArea updated;
    switch (lowerField) {
      case "label" -> {
        if (value == null || value.isBlank()) {
          sender.sendMessage(MM.deserialize("<red>The label cannot be empty.</red>"));
          return;
        }
        updated = area.withLabel(value);
      }
      case "color" -> {
        if (MapToolsUtils.parseColor(value) == null) {
          sender.sendMessage(INVALID_COLOR);
          return;
        }
        updated = area.withColor(value);
      }
      case "owners" -> updated = area.withOwners(MapAreasStore.parseOwners(value));
      case "description" -> updated = area.withDescription(value);
      case "opacity" -> {
        // A percentage from 0 to 100; leaving it out goes back to the configured default.
        Integer percent = value == null ? null : Ints.tryParse(value.replace("%", "").trim());
        if (value != null && (percent == null || percent < 0 || percent > 100)) {
          sender.sendMessage(MM.deserialize("<red>Use a percentage from 0 to 100.</red>"));
          return;
        }
        updated = area.withOpacity(percent == null ? null : percent / 100.0);
      }
      case "icon" -> {
        if (value != null && !MapToolsUtils.iconExists(value)) {
          sender.sendMessage(
              MM.deserialize(
                  "<red>There is no icon called <icon>. Icons are the PNG files in"
                      + " plugins/IceCream/map-icons.</red>",
                  Placeholder.unparsed("icon", value)));
          return;
        }
        updated = area.withIcon(value);
      }
      case "points" -> {
        AreaGeometry geometry = MapToolsUtils.parsePoints(area.world(), value);
        if (geometry == null) {
          sender.sendMessage(POINTS_USAGE);
          return;
        }
        if (MapToolsUtils.blockedByEditor(sender, id)) return;
        AreaForms.confirmNearby(
            sender,
            geometry,
            id,
            () -> {
              MapArea current = find(sender, kind, id);
              if (current == null) return;
              MapToolsUtils.saveArea(current.withGeometry(geometry));
              sender.sendMessage(
                  MM.deserialize(
                      "<green>Updated the shape of <id>.</green>", Placeholder.unparsed("id", id)));
            });
        return;
      }
      default -> throw new IllegalStateException("Unhandled field " + lowerField);
    }
    MapToolsUtils.saveArea(updated);
    sender.sendMessage(
        MM.deserialize(
            "<green>Updated the <field> of <id>.</green>",
            Placeholder.unparsed("field", lowerField.equals("points") ? "shape" : lowerField),
            Placeholder.unparsed("id", id)));
  }

  @Command("maptools delete <kind> <id>")
  @CommandDescription("Delete a claim or marker, after confirming in a dialog.")
  @Permission(PERMISSION)
  public void delete(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id) {
    CommandSender sender = stack.getSender();
    if (find(sender, kind, id) == null) return;
    // Players confirm in a dialog; the console deletes right away.
    if (sender instanceof Player player) {
      AreaForms.confirmDelete(player, id);
      return;
    }
    sender.sendMessage(MapToolsUtils.deletedMessage(MapToolsUtils.deleteArea(id)));
  }

  @Command("maptools list claims [search]")
  @CommandDescription("List claims, optionally only those whose id, label or owners match.")
  @Permission(PERMISSION)
  public void listClaims(
      CommandSourceStack stack,
      @Argument(value = "search", suggestions = "claim_search") @Greedy String search) {
    list(stack.getSender(), Kind.CLAIM, search);
  }

  @Command("maptools list markers [search]")
  @CommandDescription("List markers, optionally only those whose id, label or owners match.")
  @Permission(PERMISSION)
  public void listMarkers(
      CommandSourceStack stack,
      @Argument(value = "search", suggestions = "marker_search") @Greedy String search) {
    list(stack.getSender(), Kind.MARKER, search);
  }

  private static void list(CommandSender sender, Kind kind, String search) {
    String query = search == null ? "" : search.toLowerCase(Locale.ROOT);
    String plural = kind.label() + "s";
    List<MapArea> matches =
        areas(kind)
            .filter(
                area ->
                    (area.id() + " " + area.label() + " " + String.join(" ", area.owners()))
                        .toLowerCase(Locale.ROOT)
                        .contains(query))
            .toList();
    if (matches.isEmpty()) {
      sender.sendMessage(
          MM.deserialize("<gray>No <kinds> found.</gray>", Placeholder.unparsed("kinds", plural)));
      return;
    }

    String command = "/maptools list " + plural + " ";
    sender.sendMessage(
        MM.deserialize(
            "<gold><title> (<count>)</gold><search>",
            Placeholder.unparsed(
                "title", plural.substring(0, 1).toUpperCase() + plural.substring(1)),
            Placeholder.unparsed("count", String.valueOf(matches.size())),
            Placeholder.unparsed("search", search == null ? "" : " matching \"" + search + "\"")));
    if (search == null) {
      sender.sendMessage(
          MM.deserialize(
              "<gray><hint><hover:show_text:'Click to search'>Search with <command>\\<text>"
                  + "</hover></hint></gray>",
              Placeholder.styling("hint", ClickEvent.suggestCommand(command)),
              Placeholder.unparsed("command", command)));
    }
    boolean canTeleport = sender.hasPermission(MapToolsUtils.TP_PERMISSION);
    // Without a search, only show the first few; searching narrows the list down instead.
    List<MapArea> shown = search == null ? matches.stream().limit(LIST_LIMIT).toList() : matches;
    for (MapArea area : shown) {
      String owners = area.owners().isEmpty() ? "none" : String.join(", ", area.owners());
      // Placeholders do not work inside click commands, so the click is a styling placeholder.
      Component tpButton =
          canTeleport
              ? MM.deserialize(
                  "   <tp><green>[TP]</green></tp>",
                  Placeholder.styling(
                      "tp",
                      ClickEvent.runCommand("/maptools tp " + kind.label() + " " + area.id()),
                      HoverEvent.showText(
                          MM.deserialize(
                              "Teleport to <label>", Placeholder.unparsed("label", area.label())))))
              : Component.empty();
      sender.sendMessage(
          MM.deserialize(
              " <hover:show_text:'<gray>Owner(s): <owners><newline><detail></gray>'>"
                  + "<color>\u25A0</color> <white><label></white>"
                  + " <gray>\u00B7 <id></gray></hover>"
                  + "<tp>",
              Placeholder.styling("color", MapToolsUtils.areaColor(area)),
              Placeholder.component("tp", tpButton),
              Placeholder.unparsed("id", area.id()),
              Placeholder.unparsed("label", area.label()),
              Placeholder.unparsed("owners", owners),
              Placeholder.unparsed(
                  "detail",
                  area.isMarker()
                      ? "Icon: " + (area.icon() == null ? "none" : area.icon())
                      : "Shape: " + area.shape())));
    }
    if (shown.size() < matches.size()) {
      sender.sendMessage(
          MM.deserialize(
              "<gray>...and <more> more. Search to narrow it down.</gray>",
              Placeholder.unparsed("more", String.valueOf(matches.size() - shown.size()))));
    }
  }

  @Command("maptools tp <kind> <id>")
  @CommandDescription("Teleport to the middle of a claim, or to a marker.")
  @Permission(MapToolsUtils.TP_PERMISSION)
  public void teleport(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    MapArea area = find(player, kind, id);
    if (area == null) return;
    World world = Bukkit.getWorld(NamespacedKey.fromString(area.world()));
    if (world == null) {
      player.sendMessage(MM.deserialize("<red>That world is not loaded.</red>"));
      return;
    }
    // The middle of the outline: a circle's center, otherwise the average of its corners.
    double x = area.points().stream().mapToDouble(MapPoint::x).average().orElse(0);
    double z = area.points().stream().mapToDouble(MapPoint::z).average().orElse(0);
    int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
    player.teleportAsync(new Location(world, x, y, z, player.getYaw(), player.getPitch()));
  }

  /** Creates a claim from typed coordinates, after the player confirms if it is near others. */
  private static void createClaim(
      CommandSender sender, String id, AreaGeometry geometry, String color, String label) {
    String problem = MapToolsUtils.validate(id, color, label, true);
    if (problem != null) {
      sender.sendMessage(
          MM.deserialize("<red><problem></red>", Placeholder.unparsed("problem", problem)));
      return;
    }
    AreaForms.confirmNearby(
        sender,
        geometry,
        null,
        () ->
            MapToolsUtils.createArea(
                sender, id, geometry, color, label, List.of(), null, null, null));
  }

  private static void openCreateForm(CommandSourceStack stack, Mode mode) {
    Player player = requirePlayer(stack);
    if (player != null) AreaForms.openCreate(player, mode);
  }

  /** Starts drawing a new shape in game. */
  private static void startNew(Player player, Mode mode) {
    if (!MapToolsUtils.confirmLeave(player, () -> startNew(player, mode))) return;

    String controls =
        switch (mode) {
          case RECT -> "click corner 1, shift + click corner 2";
          case POLYGON ->
              "click to add points, click a point to move it, shift + click to remove or insert";
          case CIRCLE -> "click the center, shift + click sets the radius";
          case MARKER -> "click where the marker goes";
        };
    MapToolsUtils.startEditing(player, EditSession.create(mode));
    player.sendMessage(
        MM.deserialize(
            "<gray><shape>: <controls>. /maptools save when done.</gray>",
            Placeholder.unparsed("shape", mode.label()),
            Placeholder.unparsed("controls", controls)));
  }

  /**
   * Replaces the shape of a claim or the position of a marker.
   *
   * @param expected the kind the command works on
   * @param shape builds the new shape, or returns null if it does not apply to the area
   * @param notApplicable what to tell the sender when {@code shape} returns null
   */
  private static void setShape(
      CommandSender sender,
      Kind kind,
      Kind expected,
      String id,
      Function<MapArea, AreaGeometry> shape,
      String notApplicable) {
    MapArea area = find(sender, kind, id);
    if (area == null) return;
    if (kind != expected) {
      sender.sendMessage(
          MM.deserialize(
              "<red>That only works for <kind>s.</red>",
              Placeholder.unparsed("kind", expected.label())));
      return;
    }
    AreaGeometry geometry = shape.apply(area);
    if (geometry == null) {
      sender.sendMessage(MM.deserialize("<red>" + notApplicable + "</red>"));
      return;
    }
    if (MapToolsUtils.blockedByEditor(sender, id)) return;
    Runnable save =
        () -> {
          MapArea current = find(sender, kind, id);
          if (current == null) return;
          MapToolsUtils.saveArea(current.withGeometry(geometry));
          sender.sendMessage(
              MM.deserialize(
                  "<green>Updated the <field> of <id>.</green>",
                  Placeholder.unparsed("field", kind == Kind.MARKER ? "position" : "shape"),
                  Placeholder.unparsed("id", id)));
        };
    if (kind == Kind.MARKER) {
      save.run();
    } else {
      AreaForms.confirmNearby(sender, geometry, id, save);
    }
  }

  /** Gives a claim or marker a new id, keeping everything else. */
  private static void rename(CommandSender sender, Kind kind, MapArea area, String newId) {
    if (newId == null) {
      sender.sendMessage(
          MM.deserialize(
              "<red>Use /maptools set <kind> \\<id> id \\<new id>.</red>",
              Placeholder.unparsed("kind", kind.label())));
      return;
    }
    String problem = MapToolsUtils.validate(newId, area.color(), area.label(), true);
    if (problem != null) {
      sender.sendMessage(
          MM.deserialize("<red><problem></red>", Placeholder.unparsed("problem", problem)));
      return;
    }
    if (MapToolsUtils.blockedByEditor(sender, area.id())) return;

    MapToolsUtils.renameArea(area, newId);
    sender.sendMessage(
        MM.deserialize(
            "<green>Renamed <old> to <new>.</green>",
            Placeholder.unparsed("old", area.id()),
            Placeholder.unparsed("new", newId)));
  }

  /**
   * The kind typed so far, read from the raw input since earlier arguments are not always parsed
   * yet while suggesting: {@code /maptools <action> <kind> ...}.
   *
   * @return the kind, or null if none was typed
   */
  private static Kind typedKind(CommandContext<CommandSourceStack> ctx) {
    String[] words = ctx.rawInput().input().trim().split("\\s+");
    if (words.length < 3) return null;
    return switch (words[2].toLowerCase(Locale.ROOT)) {
      case "claim" -> Kind.CLAIM;
      case "marker" -> Kind.MARKER;
      default -> null;
    };
  }

  /**
   * The color names, with the area's current color as a hex code first so players see that hex
   * codes work. Without an area, an example hex code is shown instead.
   */
  private static List<String> colorSuggestions(MapArea area) {
    List<String> colors = new ArrayList<>();
    colors.add(area == null ? "ff8800" : MapToolsUtils.areaColor(area).asHexString().substring(1));
    colors.addAll(NamedTextColor.NAMES.keys());
    return colors;
  }

  /** The claims or markers, or both when no kind is given. */
  private static Stream<MapArea> areas(Kind kind) {
    return MapToolsModule.store.getAreas().stream()
        .filter(area -> kind == null || kind.matches(area));
  }

  private static List<String> searchWords(Kind kind) {
    return areas(kind).map(MapArea::id).toList();
  }

  /**
   * Looks up a claim or marker by id.
   *
   * @return the area, or null after telling the sender it does not exist
   */
  private static MapArea find(CommandSender sender, Kind kind, String id) {
    MapArea area = MapToolsModule.store.getArea(id);
    if (area != null && kind.matches(area)) return area;
    sender.sendMessage(
        MM.deserialize(
            "<red>There is no <kind> called <id>.</red>",
            Placeholder.unparsed("kind", kind.label()),
            Placeholder.unparsed("id", id)));
    return null;
  }

  private static String sessionAreaId(Player player) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    return session == null ? null : session.getAreaId();
  }

  private static Player requirePlayer(CommandSourceStack stack) {
    if (stack.getSender() instanceof Player player) return player;
    stack.getSender().sendMessage(PLAYERS_ONLY);
    return null;
  }
}
