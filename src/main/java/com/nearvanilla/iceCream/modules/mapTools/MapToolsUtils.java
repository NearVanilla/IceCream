package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Shared map tools logic: the squaremap layer, saving and deleting areas, edit sessions, colors and
 * validation.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public final class MapToolsUtils {

  /** Permission required for all area commands and for area edit mode. */
  public static final String PERMISSION = "icecream.modules.maptools.manage";

  /** Permission for teleporting to claims; the [TP] buttons only show for players who have it. */
  public static final String TP_PERMISSION = "icecream.modules.maptools.tp";

  /** Longest label; the forms allow no more. */
  public static final int LABEL_MAX_LENGTH = 64;

  /** Longest owners list and description; the forms allow no more. */
  public static final int TEXT_MAX_LENGTH = 256;

  /** Valid area ids. */
  public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  private static final Key LAYER_KEY = Key.of("icecream_areas");
  private static final Key MARKER_LAYER_KEY = Key.of("icecream_markers");

  private static Squaremap squaremap;
  private static final Map<String, AreaLayer> layers = new HashMap<>();
  private static final Map<String, AreaLayer> markerLayers = new HashMap<>();

  private MapToolsUtils() {
    // Utility class; prevent instantiation.
  }

  /**
   * Adds the areas layer to every loaded world.
   *
   * @param api the squaremap API
   */
  public static void init(Squaremap api) {
    squaremap = api;
    MapIcons.load(api, new File(IceCream.instance.getDataFolder(), "map-icons"));
    Bukkit.getWorlds().forEach(MapToolsUtils::registerWorld);
  }

  /** Removes all layers this module added to squaremap. */
  public static void shutdown() {
    if (squaremap == null) return;

    for (String worldKey : layers.keySet()) {
      mapWorld(worldKey)
          .ifPresent(
              mapWorld -> {
                mapWorld.layerRegistry().unregister(LAYER_KEY);
                mapWorld.layerRegistry().unregister(MARKER_LAYER_KEY);
              });
    }
    layers.clear();
    markerLayers.clear();
    MapIcons.unload(squaremap);
    squaremap = null;
  }

  /**
   * Adds the areas layer to a world's map and renders its stored areas. Worlds that squaremap does
   * not map are skipped.
   *
   * @param world the world to register
   */
  public static void registerWorld(World world) {
    String worldKey = world.getKey().asString();
    if (squaremap == null || layers.containsKey(worldKey)) return;

    Optional<MapWorld> mapWorld = mapWorld(worldKey);
    if (mapWorld.isEmpty()) return;

    AreaLayer layer =
        new AreaLayer(IceCream.config.getString("modules.maptools.layer-label", "Claims"), 10);
    mapWorld.get().layerRegistry().register(LAYER_KEY, layer);
    layers.put(worldKey, layer);
    AreaLayer markerLayer =
        new AreaLayer(
            IceCream.config.getString("modules.maptools.marker-layer-label", "Markers"), 11);
    mapWorld.get().layerRegistry().register(MARKER_LAYER_KEY, markerLayer);
    markerLayers.put(worldKey, markerLayer);

    for (MapArea area : MapToolsModule.store.getAreas()) {
      if (area.world().equals(worldKey)) renderArea(area);
    }
  }

  /**
   * Draws or redraws an area on its world's layer.
   *
   * @param area the area to draw
   */
  public static void renderArea(MapArea area) {
    AreaLayer layer = (area.isMarker() ? markerLayers : layers).get(area.world());
    if (layer == null) return;

    Color color = new Color(areaColor(area).value());
    MarkerOptions options =
        MarkerOptions.builder()
            .strokeColor(color)
            .strokeWeight(2)
            .fillColor(color)
            .fillOpacity(fillOpacity(area))
            .hoverTooltip(escapeHtml(area.label()))
            .clickTooltip(tooltip(area))
            .build();
    // Circles keep their points for in-game outlines, but the map draws them as true circles.
    // Markers without a known icon show as a small dot in their color.
    Marker marker;
    if (area.isMarker()) {
      Point point = Point.of(area.center().x(), area.center().z());
      marker = MapIcons.marker(area.icon(), point);
      if (marker == null) marker = Marker.circle(point, 3);
    } else if (area.isCircle() && area.center() != null && area.radius() != null) {
      marker = Marker.circle(Point.of(area.center().x(), area.center().z()), area.radius());
    } else {
      marker = Marker.polygon(area.points().stream().map(p -> Point.of(p.x(), p.z())).toList());
    }
    layer.put(area.id(), marker.markerOptions(options), area.points());
  }

  private static void removeFromMap(String world, String id) {
    for (Map<String, AreaLayer> worldLayers : List.of(layers, markerLayers)) {
      AreaLayer layer = worldLayers.get(world);
      if (layer != null) layer.remove(id);
    }
  }

  /**
   * Saves an area to the file, redraws it on the map and refreshes the outlines of everyone in edit
   * mode.
   *
   * @param area the area to save
   */
  public static void saveArea(MapArea area) {
    saveArea(area, area.id());
  }

  /**
   * Saves an area, possibly under a new id. Open edits of the area without unsaved changes are
   * reloaded so they show the saved version; callers check {@link #blockedByEditor} first where an
   * edit with changes would be overwritten.
   *
   * @param previousId the id the area was stored under
   */
  private static void saveArea(MapArea area, String previousId) {
    if (previousId.equals(area.id())) {
      MapToolsModule.store.putArea(area);
    } else {
      MapToolsModule.store.renameArea(previousId, area);
      removeFromMap(area.world(), previousId);
    }
    renderArea(area);
    editorsOf(previousId)
        .forEach(
            (editor, session) -> {
              if (!session.hasChanges()) startEditing(editor, EditSession.edit(area));
            });
    AreaDisplays.refreshAllAreas();
  }

  /**
   * Returns how solid an area's fill is on the web map: its own opacity, or the configured default.
   *
   * @param area the area
   * @return the opacity, from 0 to 1
   */
  public static double fillOpacity(MapArea area) {
    return area.opacity() != null ? Math.clamp(area.opacity(), 0, 1) : defaultFillOpacity();
  }

  /** Whether there is a marker icon with this name. */
  public static boolean iconExists(String name) {
    return MapIcons.exists(name);
  }

  /**
   * The Minecraft item that stands for an icon in game: the one set under {@code marker-icon-items}
   * in the config, else the item with the icon's name ({@code chest.png} is a chest), else paper.
   */
  public static Material iconItem(String icon) {
    String configured = IceCream.config.getString("modules.maptools.marker-icon-items." + icon);
    Material material = Material.matchMaterial(configured != null ? configured : icon);
    return material != null && material.isItem() && !material.isAir() ? material : Material.PAPER;
  }

  /** The names of the marker icons, from the PNG files in the icon folder. */
  public static List<String> iconNames() {
    return MapIcons.names();
  }

  /** The configured fill opacity for claims that do not set their own, from 0 to 1. */
  public static double defaultFillOpacity() {
    return Math.clamp(IceCream.config.getDouble("modules.maptools.claim-fill-opacity", 0.2), 0, 1);
  }

  /** The configured color for new claims, or gold if it is not a valid color. */
  public static String defaultColor() {
    String color = IceCream.config.getString("modules.maptools.claim-color", "gold");
    return parseColor(color) == null ? "gold" : color;
  }

  /**
   * Returns an area's color, falling back to the default color if its stored color cannot be
   * parsed.
   *
   * @param area the area
   * @return the color to draw the area with
   */
  public static TextColor areaColor(MapArea area) {
    return colorOrDefault(area.color());
  }

  /** Parses a color, falling back to the default color when it cannot be parsed. */
  public static TextColor colorOrDefault(String input) {
    TextColor color = parseColor(input);
    return color == null ? parseColor(defaultColor()) : color;
  }

  /**
   * Parses a color given as a hex code ({@code #ff8800}) or an Adventure color name ({@code gold}).
   *
   * @param input the color text
   * @return the color, or null if it cannot be parsed
   */
  public static TextColor parseColor(String input) {
    if (input == null) return null;
    if (input.startsWith("#")) return TextColor.fromHexString(input);
    return NamedTextColor.NAMES.value(input.toLowerCase(Locale.ROOT));
  }

  /**
   * Builds the block-inclusive rectangle between two corner blocks.
   *
   * @return the four corners of the rectangle
   */
  public static List<MapPoint> rectangle(double x1, double z1, double x2, double z2) {
    double minX = Math.min(x1, x2);
    double minZ = Math.min(z1, z2);
    double maxX = Math.max(x1, x2) + 1;
    double maxZ = Math.max(z1, z2) + 1;
    return List.of(
        new MapPoint(minX, minZ),
        new MapPoint(maxX, minZ),
        new MapPoint(maxX, maxZ),
        new MapPoint(minX, maxZ));
  }

  /**
   * Builds a circle as a polygon, so circles work everywhere polygons do.
   *
   * @param centerX the center x coordinate
   * @param centerZ the center z coordinate
   * @param radius the radius in blocks
   * @return the circle's points
   */
  public static List<MapPoint> circle(double centerX, double centerZ, double radius) {
    // About one point per 4 blocks of circumference, between 16 and 128 points.
    int count = (int) Math.clamp(Math.round(2 * Math.PI * radius / 4), 16, 128);
    List<MapPoint> points = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      double angle = 2 * Math.PI * i / count;
      points.add(
          new MapPoint(centerX + radius * Math.cos(angle), centerZ + radius * Math.sin(angle)));
    }
    return points;
  }

  /**
   * Puts a player in edit mode with the given session, replacing any session they had.
   *
   * @param player the player
   * @param session the shape to draw or the area to edit
   */
  public static void startEditing(Player player, EditSession session) {
    warnDiscarded(player, MapToolsModule.sessions.put(player.getUniqueId(), session));
    AreaDisplays.refresh(player);
  }

  /**
   * Takes a player out of area edit mode.
   *
   * @param player the player
   */
  public static void stopEditing(Player player) {
    warnDiscarded(player, MapToolsModule.sessions.remove(player.getUniqueId()));
    AreaDisplays.refresh(player);
  }

  /**
   * Checks whether the player may leave their current session. With unsaved changes, a form asks
   * whether to save or discard them first; picking either then continues with {@code then}.
   *
   * @param then what the player was doing, run after they choose
   * @return true if there is nothing to save and the caller can go ahead right away
   */
  public static boolean confirmLeave(Player player, Runnable then) {
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null || !session.hasChanges()) return true;
    AreaForms.confirmLeave(player, session, then);
    return false;
  }

  /**
   * Starts editing an area unless someone else is already editing it.
   *
   * @return true if the player is now editing the area
   */
  public static boolean tryEditing(Player player, MapArea area) {
    // The claim may change while the player answers the form, so look it up again afterwards.
    Runnable retry =
        () -> {
          MapArea current = MapToolsModule.store.getArea(area.id());
          if (current != null) tryEditing(player, current);
        };
    if (!confirmLeave(player, retry)) return false;
    // Only one person edits a claim at a time, so nobody overwrites someone else's changes.
    Player other =
        editorsOf(area.id()).keySet().stream()
            .filter(editor -> !editor.equals(player))
            .findFirst()
            .orElse(null);
    if (other != null) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red><player> is already editing <id>.</red>",
                  Placeholder.unparsed("player", other.getName()),
                  Placeholder.unparsed("id", area.id())));
      return false;
    }
    startEditing(player, EditSession.edit(area));
    return true;
  }

  /**
   * Deletes an area from the file and the map, and takes anyone editing it back to view mode so its
   * outline disappears in game too.
   *
   * @return the deleted area, or null if it did not exist
   */
  public static MapArea deleteArea(String id) {
    MapArea area = MapToolsModule.store.removeArea(id);
    if (area == null) return null;

    removeFromMap(area.world(), area.id());
    editorsOf(id)
        .forEach(
            (editor, session) -> {
              // The area is gone, so its unsaved changes are too; skip the discard warning.
              session.setChanged(false);
              startEditing(editor, EditSession.view());
            });
    AreaDisplays.refreshAllAreas();
    return area;
  }

  /**
   * Saves an area under a new id in the file and on the map. Callers check {@link #blockedByEditor}
   * first; an open edit without unsaved changes continues under the new id.
   *
   * @param area the area to save, still with its old id
   * @return the renamed area
   */
  public static MapArea renameArea(MapArea area, String newId) {
    MapArea renamed = area.withId(newId);
    saveArea(renamed, area.id());
    return renamed;
  }

  /** The online players editing the area with this id, with their sessions. */
  private static Map<Player, EditSession> editorsOf(String id) {
    Map<Player, EditSession> editors = new HashMap<>();
    MapToolsModule.sessions.forEach(
        (uuid, session) -> {
          Player editor = Bukkit.getPlayer(uuid);
          if (editor != null && id.equals(session.getAreaId())) editors.put(editor, session);
        });
    return editors;
  }

  /**
   * Returns the area containing a spot, skipping one area.
   *
   * @param skipId the id of an area to skip, or null
   * @return the first area containing the spot, or null
   */
  public static MapArea areaAt(String world, double x, double z, String skipId) {
    MapArea found = null;
    for (MapArea area : MapToolsModule.store.getAreas()) {
      if (!area.world().equals(world) || area.id().equals(skipId)) continue;
      // A marker is hit on its own block, and wins over a claim around it.
      if (area.isMarker()) {
        if (block(area.center().x()) == block(x) && block(area.center().z()) == block(z)) {
          return area;
        }
      } else if (found == null && contains(area.points(), x, z)) {
        found = area;
      }
    }
    return found;
  }

  /**
   * Whether someone else is editing the claim in game, which would overwrite a change when they
   * save. The sender's own edit does not count unless it has unsaved changes; it is reloaded after
   * the change instead.
   *
   * @return true if the change must not be made; the sender has been told why
   */
  public static boolean blockedByEditor(CommandSender sender, String id) {
    for (Map.Entry<Player, EditSession> entry : editorsOf(id).entrySet()) {
      Player editor = entry.getKey();
      if (editor.equals(sender)) {
        if (!entry.getValue().hasChanges()) continue;
        sender.sendMessage(
            MiniMessage.miniMessage()
                .deserialize("<red>Save or stop your own changes to this claim first.</red>"));
        return true;
      }
      sender.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red><player> is editing this claim right now.</red>",
                  Placeholder.unparsed("player", editor.getName())));
      return true;
    }
    return false;
  }

  /** The message shown after deleting an area, with its shape so it can be recreated. */
  public static Component deletedMessage(MapArea area) {
    return MiniMessage.miniMessage()
        .deserialize(
            "<green>Deleted <id> (<label>).</green> <gray><shape> in <world></gray>",
            Placeholder.unparsed("id", area.id()),
            Placeholder.unparsed("label", area.label()),
            Placeholder.unparsed("shape", shapeText(area)),
            Placeholder.unparsed("world", area.world()));
  }

  /** An area's shape as the block coordinates {@code /maptools set} takes. */
  public static String shapeText(MapArea area) {
    if (area.isMarker()) {
      return "marker %d %d".formatted(block(area.center().x()), block(area.center().z()));
    }
    if (area.isCircle()) {
      return "circle %d %d %d"
          .formatted(block(area.center().x()), block(area.center().z()), Math.round(area.radius()));
    }
    if (MapArea.RECT.equals(area.shape())) {
      int[] corners = rectangleBlocks(area.points());
      return "rect %d %d %d %d".formatted(corners[0], corners[1], corners[2], corners[3]);
    }
    List<String> points = new ArrayList<>();
    for (MapPoint point : area.points()) points.add(block(point.x()) + " " + block(point.z()));
    return "points " + String.join("; ", points);
  }

  private static long block(double coordinate) {
    return (long) Math.floor(coordinate);
  }

  /**
   * The corner blocks of a rectangle built by {@link #rectangle}, which stores its far edges one
   * block past the last block.
   *
   * @return min x, min z, max x and max z
   */
  public static int[] rectangleBlocks(List<MapPoint> points) {
    double minX = points.stream().mapToDouble(MapPoint::x).min().orElse(0);
    double minZ = points.stream().mapToDouble(MapPoint::z).min().orElse(0);
    double maxX = points.stream().mapToDouble(MapPoint::x).max().orElse(1);
    double maxZ = points.stream().mapToDouble(MapPoint::z).max().orElse(1);
    return new int[] {
      (int) Math.floor(minX),
      (int) Math.floor(minZ),
      (int) Math.floor(maxX) - 1,
      (int) Math.floor(maxZ) - 1
    };
  }

  /**
   * @return the ground block at the spot, ignoring leaves, or null if its chunk is not loaded
   */
  public static Location groundBlock(World world, double x, double z) {
    int blockX = (int) Math.floor(x);
    int blockZ = (int) Math.floor(z);
    if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return null;
    int y = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    return new Location(world, blockX, y, blockZ);
  }

  /**
   * Validates and saves a new area, telling the sender what went wrong if it cannot be created.
   *
   * @param owners the owners shown in the map popup, possibly empty
   * @param description the description shown in the map popup, or null
   * @param opacity how solid the fill is on the map, 0 to 1, or null for the default
   * @param icon the icon of a marker, or null
   * @return true if the area was created
   */
  public static boolean createArea(
      CommandSender sender,
      String id,
      AreaGeometry geometry,
      String color,
      String label,
      List<String> owners,
      String description,
      Double opacity,
      String icon) {
    String error = validate(id, color, label, true);
    if (error != null) {
      sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>" + error + "</red>"));
      return false;
    }

    MapArea area =
        new MapArea(
                id,
                null,
                List.of(),
                label,
                description,
                owners,
                color,
                null,
                null,
                null,
                opacity,
                icon)
            .withGeometry(geometry);
    saveArea(area);
    sender.sendMessage(
        MiniMessage.miniMessage()
            .deserialize(
                "<green>Created <kind> <id>.</green>",
                Placeholder.unparsed("kind", area.isMarker() ? "marker" : "claim"),
                Placeholder.unparsed("id", id)));
    if (!layers.containsKey(geometry.world())) {
      sender.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<yellow>squaremap does not render this world, so it will not show on the"
                      + " map.</yellow>"));
    }
    return true;
  }

  /**
   * Checks the details of an area.
   *
   * @param id the area id
   * @param isNew whether the id must not be taken yet
   * @return a plain text error, or null if everything is valid
   */
  public static String validate(String id, String color, String label, boolean isNew) {
    if (id == null || !ID_PATTERN.matcher(id).matches()) {
      return "Ids may only use a-z, 0-9, _ and -, up to 32 characters.";
    }
    if (isNew && MapToolsModule.store.getArea(id) != null) {
      return "A claim with id " + id + " already exists.";
    }
    if (label == null || label.isBlank()) return "The label cannot be empty.";
    if (label.length() > LABEL_MAX_LENGTH) {
      return "Labels can be up to " + LABEL_MAX_LENGTH + " characters.";
    }
    if (parseColor(color) == null) {
      return "Use a color name such as gold, or a hex code such as #ff8800.";
    }
    return null;
  }

  /** Tells the player when leaving a session threw away unsaved changes. */
  private static void warnDiscarded(Player player, EditSession previous) {
    if (previous == null || !previous.hasChanges() || !player.isOnline()) return;
    player.sendMessage(
        MiniMessage.miniMessage()
            .deserialize(
                "<yellow>Discarded the unsaved changes to <id>.</yellow>",
                Placeholder.unparsed("id", previous.getAreaId())));
  }

  /**
   * Returns whether a point lies inside an area, using an even-odd ray cast so polygons of any
   * shape work.
   *
   * @param points the outline of the area
   * @param x the block x coordinate
   * @param z the block z coordinate
   * @return true if the point is inside the outline
   */
  public static boolean contains(List<MapPoint> points, double x, double z) {
    boolean inside = false;
    for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
      MapPoint a = points.get(i);
      MapPoint b = points.get(j);
      if ((a.z() > z) != (b.z() > z)
          && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
        inside = !inside;
      }
    }
    return inside;
  }

  /**
   * @return true if the player is in edit mode, which shows areas and turns clicks into edits
   */
  public static boolean isEditing(Player player) {
    return MapToolsModule.sessions.containsKey(player.getUniqueId());
  }

  private static Optional<MapWorld> mapWorld(String worldKey) {
    NamespacedKey key = NamespacedKey.fromString(worldKey);
    World world = key == null ? null : Bukkit.getWorld(key);
    if (world == null) return Optional.empty();
    return squaremap.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world));
  }

  private static String tooltip(MapArea area) {
    StringBuilder html = new StringBuilder("<b>").append(escapeHtml(area.label())).append("</b>");
    if (!area.owners().isEmpty()) {
      html.append("<br>Owner(s): ").append(escapeHtml(String.join(", ", area.owners())));
    }
    if (area.description() != null && !area.description().isBlank()) {
      // Descriptions can be multiline from the details form; HTML needs explicit line breaks.
      html.append("<br>").append(escapeHtml(area.description()).replaceAll("\\R", "<br>"));
    }
    return html.toString();
  }

  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
