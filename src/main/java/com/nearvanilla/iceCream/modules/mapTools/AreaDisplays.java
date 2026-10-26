package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws areas and the shape being edited in game with block display entities that only the viewing
 * player can see: outlined corner blocks (ghost blocks) joined by lines, and a faint grid inside.
 * Everything follows the ground, so large areas over hills stay visible. Only the part near the
 * player is drawn.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public final class AreaDisplays {

  /**
   * How far around the player outlines are drawn, which is also how far clicks reach. Paper does
   * not send display entities further than {@code entity-tracking-range.display} in spigot.yml,
   * which is 128 by default.
   */
  public static final double VIEW_DISTANCE = 128;

  /** How far around the player grids are drawn; kept short to limit the number of entities. */
  private static final double GRID_DISTANCE = 80;

  /** Blocks between the ground samples an outline follows. */
  private static final double OUTLINE_STEP = 4;

  /** Blocks between the ground samples a grid line follows; coarser to limit the entity count. */
  private static final double GRID_STEP = 12;

  /** The largest height change between two ground samples before a line samples in between. */
  private static final double MAX_RISE = 1;

  /** How far lines and dots float above the ground, so they are not hidden in it. */
  private static final double GROUND_LIFT = 0.15;

  /** Blocks between grid lines, measured along x + z. */
  private static final int GRID_SPACING = 12;

  private static final float LINE_THICKNESS = 0.15f;
  private static final float GRID_THICKNESS = 0.04f;

  private static final float GRID_DOT_SIZE = 0.2f;

  /** Translucent, for the faint grid lines. */
  private static final BlockData GLASS_BLOCK = Material.WHITE_STAINED_GLASS.createBlockData();

  /** Solid, for the shape being edited and the grid dots. */
  private static final BlockData SOLID_BLOCK = Material.WHITE_CONCRETE.createBlockData();

  private static final BlockData HIGHLIGHT_BLOCK = Material.GLASS.createBlockData();

  /** Markers show as a lodestone, so they stand apart from claim corners. */
  private static final BlockData MARKER_BLOCK = Material.LODESTONE.createBlockData();

  /** The picked up corner: a solid yellow block, so it stands out from the glass corners. */
  private static final BlockData PICKED_BLOCK = Material.YELLOW_CONCRETE.createBlockData();

  private static final Color PICKED_COLOR = Color.YELLOW;

  /** How far a corner block highlight sticks out past the block on each side. */
  private static final float HIGHLIGHT_GROWTH = 0.03f;

  private static final Map<UUID, Drawing> areaDrawings = new HashMap<>();
  private static final Map<UUID, Drawing> previewDrawings = new HashMap<>();
  private static final Map<UUID, Drawing> gridDrawings = new HashMap<>();
  private static final Map<UUID, Drawing> previewGridDrawings = new HashMap<>();

  /** The entities drawn for one player, and where the player stood when they were drawn. */
  private record Drawing(List<Display> entities, Location anchor) {}

  private AreaDisplays() {
    // Utility class; prevent instantiation.
  }

  /**
   * Redraws the areas around a player, or removes them if the player is not in edit mode. The area
   * the player is editing is left out, since the preview draws it.
   *
   * @param player the viewing player
   */
  private static void refreshAreas(Player player) {
    drawAreas(player);
    refreshGrid(player);
  }

  /** Redraws the areas and the shape being edited around a player, with their grids. */
  public static void refresh(Player player) {
    drawAreas(player);
    drawPreview(player);
    refreshGrid(player);
    drawPreviewGrid(player);
  }

  private static void drawAreas(Player player) {
    remove(areaDrawings.remove(player.getUniqueId()));
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null) {
      remove(gridDrawings.remove(player.getUniqueId()));
      return;
    }

    Location anchor = player.getLocation();
    World world = anchor.getWorld();
    String worldKey = world.getKey().asString();
    List<Display> entities = new ArrayList<>();
    for (MapArea area : MapToolsModule.store.getAreas()) {
      if (!area.world().equals(worldKey)
          || area.id().equals(session.getAreaId())
          || area.isHidden()) {
        continue;
      }
      if (!isNear(area, anchor, VIEW_DISTANCE)) continue;

      Color color = Color.fromRGB(MapToolsUtils.areaColor(area).value());
      List<Location> ghosts = areaGhosts(area, world);
      BlockData ghost = area.isMarker() ? MARKER_BLOCK : HIGHLIGHT_BLOCK;
      drawShape(
          player,
          anchor,
          area.points(),
          true,
          ghosts,
          ghost,
          null,
          color,
          lineBlock(color),
          entities);
    }
    areaDrawings.put(player.getUniqueId(), new Drawing(entities, anchor));
  }

  /**
   * Redraws the grids inside the saved areas near a player. Grids cover a shorter distance than
   * outlines and are redrawn on their own, more often.
   *
   * @param player the viewing player
   */
  private static void refreshGrid(Player player) {
    remove(gridDrawings.remove(player.getUniqueId()));
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null) return;

    Location anchor = player.getLocation();
    World world = anchor.getWorld();
    String worldKey = world.getKey().asString();
    List<Display> entities = new ArrayList<>();
    for (MapArea area : MapToolsModule.store.getAreas()) {
      if (!area.world().equals(worldKey)
          || area.id().equals(session.getAreaId())
          || area.isHidden()) {
        continue;
      }
      if (!isNear(area, anchor, GRID_DISTANCE)) continue;
      Color color = Color.fromRGB(MapToolsUtils.areaColor(area).value());
      drawGrid(player, anchor, area.points(), color, entities);
    }
    gridDrawings.put(player.getUniqueId(), new Drawing(entities, anchor));
  }

  /** Redraws the grid inside the shape being drawn or edited, which changes with every click. */
  private static void drawPreviewGrid(Player player) {
    remove(previewGridDrawings.remove(player.getUniqueId()));
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null || session.isViewing()) return;

    Location anchor = player.getLocation();
    if (!anchor.getWorld().getKey().asString().equals(session.getWorld())) return;

    List<Display> entities = new ArrayList<>();
    drawGrid(player, anchor, session.previewOutline(), Color.WHITE, entities);
    previewGridDrawings.put(player.getUniqueId(), new Drawing(entities, anchor));
  }

  /** Redraws the areas for every player in edit mode. */
  public static void refreshAllAreas() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (MapToolsUtils.isEditing(player)) refreshAreas(player);
    }
  }

  /**
   * Redraws the shape a player is drawing or editing in white, with the picked up corner in yellow.
   *
   * @param player the editing player
   */
  public static void refreshPreview(Player player) {
    drawPreview(player);
    drawPreviewGrid(player);
  }

  private static void drawPreview(Player player) {
    remove(previewDrawings.remove(player.getUniqueId()));
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session == null || session.isViewing()) return;

    Location anchor = player.getLocation();
    if (!anchor.getWorld().getKey().asString().equals(session.getWorld())) return;

    List<Display> entities = new ArrayList<>();
    List<MapPoint> outline = session.previewOutline();
    drawShape(
        player,
        anchor,
        outline,
        outline.size() >= 3,
        session.definingBlocks(),
        session.getMode() == EditSession.Mode.MARKER ? MARKER_BLOCK : HIGHLIGHT_BLOCK,
        session.getPicked(),
        Color.WHITE,
        SOLID_BLOCK,
        entities);
    previewDrawings.put(player.getUniqueId(), new Drawing(entities, anchor));
  }

  /**
   * Hides a player's areas once they leave edit mode, and redraws areas and preview once the player
   * moved far enough that the clipped lines no longer cover their surroundings.
   *
   * @param player the player to check
   */
  public static void update(Player player) {
    Drawing areas = areaDrawings.get(player.getUniqueId());
    Drawing preview = previewDrawings.get(player.getUniqueId());
    boolean redrawAreas =
        MapToolsUtils.isEditing(player) ? isStale(areas, player, VIEW_DISTANCE) : areas != null;
    boolean redrawPreview = preview != null && isStale(preview, player, VIEW_DISTANCE);
    if (redrawAreas) drawAreas(player);
    if (redrawPreview) drawPreview(player);
    if (redrawAreas || isStale(gridDrawings.get(player.getUniqueId()), player, GRID_DISTANCE)) {
      refreshGrid(player);
    }
    Drawing previewGrid = previewGridDrawings.get(player.getUniqueId());
    if (previewGrid != null && isStale(previewGrid, player, GRID_DISTANCE)) {
      drawPreviewGrid(player);
    }
  }

  /** Removes everything drawn for every player. */
  public static void clearAll() {
    areaDrawings.values().forEach(AreaDisplays::remove);
    previewDrawings.values().forEach(AreaDisplays::remove);
    gridDrawings.values().forEach(AreaDisplays::remove);
    previewGridDrawings.values().forEach(AreaDisplays::remove);
    areaDrawings.clear();
    previewDrawings.clear();
    gridDrawings.clear();
    previewGridDrawings.clear();
  }

  private static boolean isStale(Drawing drawing, Player player, double distance) {
    if (drawing == null) return true;

    Location location = player.getLocation();
    if (!location.getWorld().equals(drawing.anchor().getWorld())) return true;
    double dx = location.getX() - drawing.anchor().getX();
    double dz = location.getZ() - drawing.anchor().getZ();
    double moveLimit = distance / 3;
    return dx * dx + dz * dz > moveLimit * moveLimit;
  }

  /** The ghost blocks of a saved area: its corners, or the center of a circle or marker. */
  private static List<Location> areaGhosts(MapArea area, World world) {
    List<Location> ghosts = new ArrayList<>();
    if (area.isCircle() || area.isMarker()) {
      ghosts.add(MapToolsUtils.groundBlock(world, area.center().x(), area.center().z()));
    } else {
      for (MapPoint corner : area.points()) {
        ghosts.add(cornerBlock(area.points(), corner, world));
      }
    }
    return ghosts;
  }

  /** Whether any part of the area's bounding box lies within range of the anchor. */
  private static boolean isNear(MapArea area, Location anchor, double range) {
    double minX = Double.MAX_VALUE;
    double minZ = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxZ = -Double.MAX_VALUE;
    for (MapPoint point : area.points()) {
      minX = Math.min(minX, point.x());
      minZ = Math.min(minZ, point.z());
      maxX = Math.max(maxX, point.x());
      maxZ = Math.max(maxZ, point.z());
    }
    double dx = Math.max(0, Math.max(minX - anchor.getX(), anchor.getX() - maxX));
    double dz = Math.max(0, Math.max(minZ - anchor.getZ(), anchor.getZ() - maxZ));
    return dx * dx + dz * dz <= range * range;
  }

  /**
   * The solid block an area's outline is drawn with: the concrete closest to its color, so the
   * outline stands out in daylight.
   */
  private static BlockData lineBlock(Color color) {
    DyeColor closest = DyeColor.WHITE;
    int closestDistance = Integer.MAX_VALUE;
    for (DyeColor dye : DyeColor.values()) {
      Color candidate = dye.getColor();
      int dr = candidate.getRed() - color.getRed();
      int dg = candidate.getGreen() - color.getGreen();
      int db = candidate.getBlue() - color.getBlue();
      int distance = dr * dr + dg * dg + db * db;
      if (distance < closestDistance) {
        closest = dye;
        closestDistance = distance;
      }
    }
    return Material.valueOf(closest.name() + "_CONCRETE").createBlockData();
  }

  /**
   * Where a ghost block is drawn: on the ground at its x and z, or where it is if its chunk is not
   * loaded.
   *
   * @param block the defining block
   * @return the block to outline
   */
  public static Location drawnGhost(Location block) {
    Location ground = MapToolsUtils.groundBlock(block.getWorld(), block.getX(), block.getZ());
    return ground == null ? block : ground;
  }

  /** The height lines are drawn at above a spot, or null if its chunk is not loaded. */
  private static Double surfaceHeight(World world, double x, double z) {
    Location ground = MapToolsUtils.groundBlock(world, x, z);
    return ground == null ? null : ground.getBlockY() + 1 + GROUND_LIFT;
  }

  /**
   * Draws a shape as ghost blocks on the ground joined by lines that follow the ground.
   *
   * @param outline the points the lines run through
   * @param ghosts the blocks to outline; null entries are skipped
   * @param ghostBlock the block the ghost blocks are drawn as
   * @param picked the index of the ghost block to draw as picked up, or null
   */
  private static void drawShape(
      Player player,
      Location anchor,
      List<MapPoint> outline,
      boolean closed,
      List<Location> ghosts,
      BlockData ghostBlock,
      Integer picked,
      Color color,
      BlockData block,
      List<Display> entities) {
    for (int i = 0; i < ghosts.size(); i++) {
      Location ghost = ghosts.get(i);
      if (ghost == null) continue;
      Location drawn = drawnGhost(ghost);
      boolean isPicked = picked != null && picked == i;
      highlightBlock(
          player,
          anchor,
          drawn,
          isPicked ? PICKED_COLOR : color,
          isPicked ? PICKED_BLOCK : ghostBlock,
          entities);
    }
    if (outline.size() < 2) return;

    double range = VIEW_DISTANCE;
    int edges = closed ? outline.size() : outline.size() - 1;
    for (int i = 0; i < edges; i++) {
      MapPoint a = outline.get(i);
      MapPoint b = outline.get((i + 1) % outline.size());
      drawLine(player, anchor, a, b, range, OUTLINE_STEP, LINE_THICKNESS, color, block, entities);
    }
  }

  /**
   * Draws a diagonal grid inside a closed shape, cut to the outline so it works for any shape:
   * faint glass lines, with glowing dots at the crossings that show through blocks.
   */
  private static void drawGrid(
      Player player,
      Location anchor,
      List<MapPoint> outline,
      Color dotColor,
      List<Display> entities) {
    if (outline.size() < 3) return;
    int spacing = GRID_SPACING;
    double range = GRID_DISTANCE;
    // Each diagonal family is the set of lines where x + z (or x - z) is a multiple of the spacing.
    for (int sign : new int[] {1, -1}) {
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;
      for (MapPoint point : outline) {
        double value = point.x() + sign * point.z();
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
      double center = anchor.getX() + sign * anchor.getZ();
      min = Math.max(min, center - range * Math.sqrt(2));
      max = Math.min(max, center + range * Math.sqrt(2));

      for (double line = Math.ceil(min / spacing) * spacing; line <= max; line += spacing) {
        // Find where this line crosses the outline, measured along the line by x - sign * z;
        // the inside runs between pairs of crossings. A tiny offset avoids passing through corners.
        double probe = line + 1e-6;
        List<Double> crossings = new ArrayList<>();
        for (int i = 0; i < outline.size(); i++) {
          MapPoint a = outline.get(i);
          MapPoint b = outline.get((i + 1) % outline.size());
          double fa = a.x() + sign * a.z();
          double fb = b.x() + sign * b.z();
          if ((fa <= probe) == (fb <= probe)) continue;
          double t = (probe - fa) / (fb - fa);
          double wa = a.x() - sign * a.z();
          double wb = b.x() - sign * b.z();
          crossings.add(wa + t * (wb - wa));
        }
        crossings.sort(null);
        for (int i = 0; i + 1 < crossings.size(); i += 2) {
          MapPoint from = onDiagonal(sign, line, crossings.get(i));
          MapPoint to = onDiagonal(sign, line, crossings.get(i + 1));
          drawLine(
              player,
              anchor,
              from,
              to,
              range,
              GRID_STEP,
              GRID_THICKNESS,
              null,
              GLASS_BLOCK,
              entities);
        }
      }
    }
    drawGridDots(player, anchor, outline, spacing, range, dotColor, entities);
  }

  /**
   * Draws a small glowing dot where the diagonal grid lines cross, so the grid can be seen through
   * blocks without solid glowing lines.
   */
  private static void drawGridDots(
      Player player,
      Location anchor,
      List<MapPoint> outline,
      int spacing,
      double range,
      Color color,
      List<Display> entities) {
    double reach = range * Math.sqrt(2);
    double sum = anchor.getX() + anchor.getZ();
    double difference = anchor.getX() - anchor.getZ();
    Transformation dot =
        new Transformation(
            new Vector3f(-GRID_DOT_SIZE / 2),
            new Quaternionf(),
            new Vector3f(GRID_DOT_SIZE),
            new Quaternionf());
    // Crossings are where x + z and x - z are both multiples of the spacing.
    for (double a = Math.ceil((sum - reach) / spacing) * spacing; a <= sum + reach; a += spacing) {
      for (double b = Math.ceil((difference - reach) / spacing) * spacing;
          b <= difference + reach;
          b += spacing) {
        double x = (a + b) / 2;
        double z = (a - b) / 2;
        double dx = x - anchor.getX();
        double dz = z - anchor.getZ();
        if (dx * dx + dz * dz > range * range) continue;
        if (!MapToolsUtils.contains(outline, x, z)) continue;
        Double y = surfaceHeight(anchor.getWorld(), x, z);
        if (y == null) continue;

        Location location = new Location(anchor.getWorld(), x, y + GRID_DOT_SIZE / 2, z);
        entities.add(spawn(player, location, dot, color, SOLID_BLOCK));
      }
    }
  }

  /** The point where x + sign * z equals {@code line} and x - sign * z equals {@code along}. */
  private static MapPoint onDiagonal(int sign, double line, double along) {
    return new MapPoint((line + along) / 2, sign * (line - along) / 2);
  }

  /**
   * Draws the part of the line from a to b that lies within range of the anchor, following the
   * ground in pieces of at most {@code step} blocks, and shorter pieces where the ground is steep.
   * Pieces over unloaded chunks are left out.
   */
  private static void drawLine(
      Player player,
      Location anchor,
      MapPoint a,
      MapPoint b,
      double range,
      double step,
      float thickness,
      Color color,
      BlockData block,
      List<Display> entities) {
    double dx = b.x() - a.x();
    double dz = b.z() - a.z();
    double fx = a.x() - anchor.getX();
    double fz = a.z() - anchor.getZ();
    double qa = dx * dx + dz * dz;
    if (qa == 0) return;

    // Solve |a + t(b - a) - anchor| = range in x and z for t, then clamp to the segment.
    double qb = 2 * (fx * dx + fz * dz);
    double qc = fx * fx + fz * fz - range * range;
    double discriminant = qb * qb - 4 * qa * qc;
    if (discriminant < 0) return;
    double root = Math.sqrt(discriminant);
    double t0 = Math.max(0, (-qb - root) / (2 * qa));
    double t1 = Math.min(1, (-qb + root) / (2 * qa));
    if (t0 >= t1) return;

    World world = anchor.getWorld();
    int pieces = (int) Math.max(1, Math.ceil(Math.sqrt(qa) * (t1 - t0) / step));
    Vector3f previous = null;
    for (int i = 0; i <= pieces; i++) {
      double t = t0 + (t1 - t0) * i / pieces;
      Vector3f point = groundPoint(world, a.x() + dx * t, a.z() + dz * t);
      if (previous != null && point != null) {
        drawGroundSegment(player, world, previous, point, thickness, color, block, entities);
      }
      previous = point;
    }
  }

  /** The point just above the ground at a spot, or null if its chunk is not loaded. */
  private static Vector3f groundPoint(World world, double x, double z) {
    Double y = surfaceHeight(world, x, z);
    return y == null ? null : new Vector3f((float) x, y.floatValue(), (float) z);
  }

  /**
   * Draws a piece of line between two ground points, splitting it in half while the height changes
   * by more than {@link #MAX_RISE} over more than a block, so it follows cliffs and ravines instead
   * of cutting through the air or the ground.
   */
  private static void drawGroundSegment(
      Player player,
      World world,
      Vector3f from,
      Vector3f to,
      float thickness,
      Color color,
      BlockData block,
      List<Display> entities) {
    float flatLength = (float) Math.hypot(to.x - from.x, to.z - from.z);
    if (Math.abs(to.y - from.y) > MAX_RISE && flatLength > 1) {
      Vector3f middle = groundPoint(world, (from.x + to.x) / 2, (from.z + to.z) / 2);
      if (middle != null) {
        drawGroundSegment(player, world, from, middle, thickness, color, block, entities);
        drawGroundSegment(player, world, middle, to, thickness, color, block, entities);
        return;
      }
    }
    drawSegment(player, world, from, to, thickness, color, block, entities);
  }

  private static void drawSegment(
      Player player,
      World world,
      Vector3f from,
      Vector3f to,
      float thickness,
      Color color,
      BlockData block,
      List<Display> entities) {
    Vector3f direction = new Vector3f(to).sub(from);
    float length = direction.length();
    if (length == 0) return;

    Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), direction);
    Vector3f offset = rotation.transform(new Vector3f(0, -thickness / 2, -thickness / 2));
    Transformation transformation =
        new Transformation(
            offset, rotation, new Vector3f(length, thickness, thickness), new Quaternionf());
    Location location = new Location(world, from.x, from.y, from.z);
    entities.add(spawn(player, location, transformation, color, block));
  }

  /**
   * Outlines a whole block with a glowing box slightly larger than the block, so the exact corner
   * block stands out. Blocks out of range are skipped.
   */
  private static void highlightBlock(
      Player player,
      Location anchor,
      Location block,
      Color color,
      BlockData shown,
      List<Display> entities) {
    if (block == null || !block.getWorld().equals(anchor.getWorld())) return;
    double dx = block.getBlockX() + 0.5 - anchor.getX();
    double dz = block.getBlockZ() + 0.5 - anchor.getZ();
    double range = VIEW_DISTANCE;
    if (dx * dx + dz * dz > range * range) return;

    Transformation transformation =
        new Transformation(
            new Vector3f(-HIGHLIGHT_GROWTH),
            new Quaternionf(),
            new Vector3f(1 + 2 * HIGHLIGHT_GROWTH),
            new Quaternionf());
    entities.add(spawn(player, block.toBlockLocation(), transformation, color, shown));
  }

  /**
   * Finds the ghost block that marks a polygon corner: the block containing the corner, or for
   * corners on a block edge (rectangles), the neighboring block that lies inside the polygon.
   *
   * @return the corner block on the ground, or null if its chunk is not loaded
   */
  private static Location cornerBlock(List<MapPoint> points, MapPoint corner, World world) {
    double x = corner.x();
    double z = corner.z();
    if (x == Math.floor(x) && z == Math.floor(z)) {
      search:
      for (int offsetX = -1; offsetX <= 0; offsetX++) {
        for (int offsetZ = -1; offsetZ <= 0; offsetZ++) {
          if (MapToolsUtils.contains(points, x + offsetX + 0.5, z + offsetZ + 0.5)) {
            x += offsetX;
            z += offsetZ;
            break search;
          }
        }
      }
    }
    return MapToolsUtils.groundBlock(world, x, z);
  }

  private static Display spawn(
      Player player,
      Location location,
      Transformation transformation,
      Color color,
      BlockData block) {
    World world = location.getWorld();
    BlockDisplay display =
        world.spawn(
            location,
            BlockDisplay.class,
            entity -> {
              entity.setVisibleByDefault(false);
              entity.setPersistent(false);
              entity.setBlock(block);
              entity.setTransformation(transformation);
              // A null color means a faint line that does not glow.
              if (color != null) {
                entity.setGlowing(true);
                entity.setGlowColorOverride(color);
              }
              entity.setBrightness(new Display.Brightness(15, 15));
              // View range is a multiplier of 64 blocks; the server's display tracking range in
              // spigot.yml still caps how far away entities are sent.
              entity.setViewRange((float) (VIEW_DISTANCE / 64) + 0.5f);
            });
    player.showEntity(IceCream.instance, display);
    return display;
  }

  private static void remove(Drawing drawing) {
    if (drawing == null) return;
    drawing.entities().forEach(Display::remove);
  }
}
