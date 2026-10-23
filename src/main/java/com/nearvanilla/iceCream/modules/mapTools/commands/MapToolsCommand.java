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

  private static final Component SHAPE_USAGE =
      MM.deserialize(
          "<red>Use rect \\<x1> \\<z1> \\<x2> \\<z2>, circle \\<x> \\<z> \\<radius>, or"
              + " points \\<x z; x z; x z> with at least 3 points.</red>");
  private static final Component MOVE_USAGE = MM.deserialize("<red>Use move \\<x> \\<z>.</red>");
  private static final Component INVALID_COLOR =
      MM.deserialize("<red>Use a color name such as gold, or a hex code such as #ff8800.</red>");

  private static final List<String> CLAIM_FIELDS =
      List.of(
          "id", "label", "color", "owners", "description", "opacity", "rect", "circle", "points");
  private static final List<String> MARKER_FIELDS =
      List.of("id", "label", "color", "owners", "description", "icon", "move");

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
    return List.copyOf(NamedTextColor.NAMES.keys());
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
  @CommandDescription("Draw a new rectangular claim.")
  @Permission(PERMISSION)
  public void createRectangle(CommandSourceStack stack) {
    startNew(stack, Mode.RECT, "click corner 1, shift + click corner 2");
  }

  @Command("maptools create claim polygon")
  @CommandDescription("Draw a new polygon claim.")
  @Permission(PERMISSION)
  public void createPolygon(CommandSourceStack stack) {
    startNew(
        stack,
        Mode.POLYGON,
        "click to add points, click a point to move it, shift + click to remove or insert");
  }

  @Command("maptools create claim circle")
  @CommandDescription("Draw a new circular claim.")
  @Permission(PERMISSION)
  public void createCircle(CommandSourceStack stack) {
    startNew(stack, Mode.CIRCLE, "click the center, shift + click sets the radius");
  }

  @Command("maptools create marker")
  @CommandDescription("Place a new marker with an icon.")
  @Permission(PERMISSION)
  public void createMarker(CommandSourceStack stack) {
    startNew(stack, Mode.MARKER, "click where the marker goes");
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

  @Command("maptools create claim rect <world> <x1> <z1> <x2> <z2> <id> <color> <label>")
  @CommandDescription("Create a rectangular claim from coordinates.")
  @Permission(PERMISSION)
  public void createRect(
      CommandSourceStack stack,
      @Argument("world") World world,
      @Argument("x1") int x1,
      @Argument("z1") int z1,
      @Argument("x2") int x2,
      @Argument("z2") int z2,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    AreaGeometry geometry =
        new AreaGeometry(
            world.getKey().asString(),
            MapToolsUtils.rectangle(x1, z1, x2, z2),
            MapArea.RECT,
            null,
            null);
    MapToolsUtils.createArea(
        stack.getSender(), id, geometry, color, label, List.of(), null, null, null);
  }

  @Command("maptools create claim circle <world> <x> <z> <radius> <id> <color> <label>")
  @CommandDescription("Create a circular claim from coordinates.")
  @Permission(PERMISSION)
  public void createCircle(
      CommandSourceStack stack,
      @Argument("world") World world,
      @Argument("x") int x,
      @Argument("z") int z,
      @Argument("radius") int radius,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    AreaGeometry geometry =
        parseShape(world.getKey().asString(), "circle", x + " " + z + " " + radius);
    if (geometry == null) {
      stack.getSender().sendMessage(MM.deserialize("<red>The radius must be at least 1.</red>"));
      return;
    }
    MapToolsUtils.createArea(
        stack.getSender(), id, geometry, color, label, List.of(), null, null, null);
  }

  @Command("maptools create claim polygon <world> <points> <id> <color> <label>")
  @CommandDescription("Create a polygon claim from coordinates, given as \"x z; x z; x z\".")
  @Permission(PERMISSION)
  public void createPolygon(
      CommandSourceStack stack,
      @Argument("world") World world,
      @Argument("points") @Quoted String points,
      @Argument("id") String id,
      @Argument(value = "color", suggestions = "map_colors") String color,
      @Argument("label") @Greedy String label) {
    AreaGeometry geometry = parseShape(world.getKey().asString(), "points", points);
    if (geometry == null) {
      stack
          .getSender()
          .sendMessage(
              MM.deserialize(
                  "<red>Use points in quotes, such as \"0 0; 10 0; 10 10\", with at least 3"
                      + " points.</red>"));
      return;
    }
    MapToolsUtils.createArea(
        stack.getSender(), id, geometry, color, label, List.of(), null, null, null);
  }

  @Command("maptools set <kind> <id> <field> [value]")
  @CommandDescription("Change details, or replace a claim's shape or move a marker by coordinates.")
  @Permission(PERMISSION)
  public void set(
      CommandSourceStack stack,
      @Argument("kind") Kind kind,
      @Argument(value = "id", suggestions = "area_ids") String id,
      @Argument(value = "field", suggestions = "area_fields") String field,
      @Argument("value") @Greedy String value) {
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

    boolean shapeChanged = List.of("rect", "circle", "points", "move").contains(lowerField);

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
      case "rect", "circle", "points", "move" -> {
        AreaGeometry geometry = parseShape(area.world(), lowerField, value);
        if (geometry == null) {
          sender.sendMessage(kind == Kind.MARKER ? MOVE_USAGE : SHAPE_USAGE);
          return;
        }
        if (MapToolsUtils.blockedByEditor(sender, id)) return;
        updated = area.withGeometry(geometry);
      }
      default -> throw new IllegalStateException("Unhandled field " + lowerField);
    }
    MapToolsUtils.saveArea(updated);
    sender.sendMessage(
        MM.deserialize(
            "<green>Updated the <field> of <id>.</green>",
            Placeholder.unparsed(
                "field", shapeChanged ? (kind == Kind.MARKER ? "position" : "shape") : lowerField),
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

  private static void startNew(CommandSourceStack stack, Mode mode, String controls) {
    Player player = requirePlayer(stack);
    if (player == null) return;
    if (!MapToolsUtils.confirmLeave(player, () -> startNew(stack, mode, controls))) return;

    MapToolsUtils.startEditing(player, EditSession.create(mode));
    player.sendMessage(
        MM.deserialize(
            "<gray><shape>: <controls>. /maptools save when done.</gray>",
            Placeholder.unparsed("shape", mode.label()),
            Placeholder.unparsed("controls", controls)));
  }

  /**
   * Parses a shape typed as block coordinates, the same blocks that would be clicked in game.
   *
   * @param shape rect, circle or points for a claim, move for a marker
   * @return the shape, or null if the coordinates do not fit the shape
   */
  private static AreaGeometry parseShape(String world, String shape, String value) {
    if (value == null) return null;
    try {
      if (shape.equals("points")) {
        List<MapPoint> points = new ArrayList<>();
        for (String point : value.split(";")) {
          int[] xz = numbers(point, 2);
          if (xz == null) return null;
          points.add(new MapPoint(xz[0] + 0.5, xz[1] + 0.5));
        }
        if (points.size() < 3) return null;
        return new AreaGeometry(world, points, MapArea.POLYGON, null, null);
      }
      if (shape.equals("move")) {
        int[] c = numbers(value, 2);
        if (c == null) return null;
        MapPoint spot = new MapPoint(c[0] + 0.5, c[1] + 0.5);
        return new AreaGeometry(world, List.of(spot), MapArea.MARKER, spot, null);
      }
      if (shape.equals("rect")) {
        int[] c = numbers(value, 4);
        if (c == null) return null;
        return new AreaGeometry(
            world, MapToolsUtils.rectangle(c[0], c[1], c[2], c[3]), MapArea.RECT, null, null);
      }
      int[] c = numbers(value, 3);
      if (c == null || c[2] < 1) return null;
      MapPoint center = new MapPoint(c[0] + 0.5, c[1] + 0.5);
      return new AreaGeometry(
          world,
          MapToolsUtils.circle(center.x(), center.z(), c[2]),
          MapArea.CIRCLE,
          center,
          (double) c[2]);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Splits text on spaces into exactly {@code count} whole numbers, or returns null. */
  private static int[] numbers(String text, int count) {
    String[] parts = text.trim().split("\\s+");
    if (parts.length != count) return null;
    int[] values = new int[count];
    for (int i = 0; i < count; i++) values[i] = Integer.parseInt(parts[i]);
    return values;
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

  /** The claims or markers, or both when no kind is given. */
  private static Stream<MapArea> areas(Kind kind) {
    return MapToolsModule.store.getAreas().stream()
        .filter(area -> kind == null || kind.matches(area));
  }

  private static List<String> searchWords(Kind kind) {
    return areas(kind)
        .flatMap(area -> Stream.concat(Stream.of(area.id(), area.label()), area.owners().stream()))
        .distinct()
        .toList();
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
