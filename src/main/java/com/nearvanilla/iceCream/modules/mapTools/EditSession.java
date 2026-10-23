package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * A player's edit mode: only viewing areas, drawing a new shape, or editing an existing area with a
 * corner possibly picked up. Shapes are kept as the blocks that define them, so the preview can
 * outline exactly those blocks.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class EditSession {

  /** The kinds of shape an area can have. */
  public enum Mode {
    RECT("Rectangle"),
    POLYGON("Polygon"),
    CIRCLE("Circle"),
    MARKER("Marker");

    private final String label;

    Mode(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }

    public static Mode of(MapArea area) {
      return valueOf(area.shape().toUpperCase(Locale.ROOT));
    }
  }

  private final Mode mode;
  private final String areaId;
  private String world;
  private Location corner1;
  private Location corner2;
  private final List<Location> points = new ArrayList<>();
  private Location center;
  private Integer radius;
  private Integer picked;
  private boolean changed;
  private boolean hinted;

  private EditSession(Mode mode, String areaId) {
    this.mode = mode;
    this.areaId = areaId;
  }

  /** Only shows areas; clicking inside one starts editing it. */
  public static EditSession view() {
    return new EditSession(null, null);
  }

  /** Starts drawing a new shape. */
  public static EditSession create(Mode mode) {
    return new EditSession(mode, null);
  }

  /** Starts editing an existing area, placing its corner blocks on the ground. */
  public static EditSession edit(MapArea area) {
    EditSession session = new EditSession(Mode.of(area), area.id());
    session.world = area.world();
    World world = Bukkit.getWorld(NamespacedKey.fromString(area.world()));
    if (world == null) return session;

    switch (session.mode) {
      case RECT -> {
        int[] corners = MapToolsUtils.rectangleBlocks(area.points());
        List<Location> blocks =
            ground(
                world,
                List.of(
                    new MapPoint(corners[0], corners[1]), new MapPoint(corners[2], corners[3])));
        session.corner1 = blocks.get(0);
        session.corner2 = blocks.get(1);
      }
      case POLYGON -> session.points.addAll(ground(world, area.points()));
      case CIRCLE -> {
        session.center = ground(world, List.of(area.center())).getFirst();
        session.radius = (int) Math.round(area.radius());
      }
      case MARKER -> session.center = ground(world, List.of(area.center())).getFirst();
    }
    return session;
  }

  /**
   * Finds the ground blocks for the given spots without loading chunks. Spots in unloaded chunks
   * get the lowest height found, since lines and corners are drawn at the lowest corner anyway.
   */
  private static List<Location> ground(World world, List<MapPoint> spots) {
    List<Location> blocks = new ArrayList<>();
    for (MapPoint spot : spots) blocks.add(MapToolsUtils.groundBlock(world, spot.x(), spot.z()));
    int fallbackY =
        blocks.stream()
            .filter(Objects::nonNull)
            .mapToInt(Location::getBlockY)
            .min()
            .orElse(world.getSeaLevel());
    for (int i = 0; i < blocks.size(); i++) {
      if (blocks.get(i) != null) continue;
      MapPoint spot = spots.get(i);
      blocks.set(i, new Location(world, Math.floor(spot.x()), fallbackY, Math.floor(spot.z())));
    }
    return blocks;
  }

  public Mode getMode() {
    return mode;
  }

  /** The id of the area being edited, or null when drawing a new shape. */
  public String getAreaId() {
    return areaId;
  }

  /** True when drawing a new shape, as opposed to viewing or editing an existing area. */
  public boolean isNew() {
    return mode != null && areaId == null;
  }

  public boolean isViewing() {
    return mode == null;
  }

  /** Whether this session edits a saved area, rather than viewing or drawing a new shape. */
  public boolean isEditingArea() {
    return areaId != null;
  }

  public String getWorld() {
    return world;
  }

  /** True when an existing area's shape was changed but not saved yet. */
  public boolean hasChanges() {
    return changed;
  }

  public void setChanged(boolean changed) {
    this.changed = changed;
  }

  /**
   * Marks that the save hint was shown for this session.
   *
   * @return true the first time, so the hint is only shown once
   */
  public boolean markHinted() {
    if (hinted) return false;
    hinted = true;
    return true;
  }

  public Integer getRadius() {
    return radius;
  }

  public Integer getPicked() {
    return picked;
  }

  public void setPicked(Integer picked) {
    this.picked = picked;
  }

  public Location getCenter() {
    return center;
  }

  public int getPointCount() {
    return points.size();
  }

  public void setCorner1(Location block) {
    switchWorld(block);
    corner1 = block.toBlockLocation();
  }

  public void setCorner2(Location block) {
    switchWorld(block);
    corner2 = block.toBlockLocation();
  }

  /** Moves a rectangle so the picked corner lands on the block and the opposite corner stays. */
  public void moveRectCorner(int corner, Location block) {
    corner1 = rectCorners().get((corner + 2) % 4);
    corner2 = block.toBlockLocation();
  }

  public void addPoint(Location block) {
    switchWorld(block);
    points.add(block.toBlockLocation());
  }

  public void insertPoint(int index, Location block) {
    points.add(index, block.toBlockLocation());
  }

  /**
   * Measures how far a block is outside the shape, from the block's center to the nearest edge.
   *
   * @return the distance in blocks, or 0 if the block is inside the shape
   */
  public double distanceToOutline(Location block) {
    AreaGeometry geometry = toGeometry();
    if (geometry == null) return 0;
    List<MapPoint> outline = geometry.points();
    double x = block.getBlockX() + 0.5;
    double z = block.getBlockZ() + 0.5;
    if (MapToolsUtils.contains(outline, x, z)) return 0;

    double nearest = Double.MAX_VALUE;
    for (int i = 0; i < outline.size(); i++) {
      MapPoint a = outline.get(i);
      MapPoint b = outline.get((i + 1) % outline.size());
      nearest = Math.min(nearest, distanceToSegment(x, z, a.x(), a.z(), b.x(), b.z()));
    }
    return nearest;
  }

  /** The distance from a point to the closest spot on the segment from (ax, az) to (bx, bz). */
  private static double distanceToSegment(
      double x, double z, double ax, double az, double bx, double bz) {
    double dx = bx - ax;
    double dz = bz - az;
    double lengthSquared = dx * dx + dz * dz;
    double t =
        lengthSquared == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / lengthSquared, 0, 1);
    return Math.hypot(x - (ax + t * dx), z - (az + t * dz));
  }

  /**
   * Finds the polygon edge closest to a block, measured from the block's center.
   *
   * @return the index of the point the nearest edge starts at; a new point goes in after it
   */
  public int nearestEdge(Location block) {
    double x = block.getBlockX() + 0.5;
    double z = block.getBlockZ() + 0.5;
    int nearest = points.size() - 1;
    double nearestDistance = Double.MAX_VALUE;
    for (int i = 0; i < points.size(); i++) {
      Location a = points.get(i);
      Location b = points.get((i + 1) % points.size());
      double distance =
          distanceToSegment(
              x,
              z,
              a.getBlockX() + 0.5,
              a.getBlockZ() + 0.5,
              b.getBlockX() + 0.5,
              b.getBlockZ() + 0.5);
      if (distance < nearestDistance) {
        nearest = i;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  public void setPoint(int index, Location block) {
    points.set(index, block.toBlockLocation());
  }

  public void removePoint(int index) {
    points.remove(index);
  }

  public void undoPoint() {
    if (!points.isEmpty()) points.removeLast();
  }

  public void setCenter(Location block) {
    switchWorld(block);
    center = block.toBlockLocation();
  }

  public void setRadius(int radius) {
    this.radius = radius;
  }

  /**
   * The blocks that define the shape, outlined as ghost blocks: the four rectangle corners, the
   * polygon points, or the circle or marker center. Rectangle corners that were not clicked use the
   * lower of the two clicked heights.
   */
  public List<Location> definingBlocks() {
    return switch (mode) {
      case RECT -> {
        if (corner1 != null && corner2 != null) yield rectCorners();
        List<Location> single = new ArrayList<>();
        if (corner1 != null) single.add(corner1);
        if (corner2 != null) single.add(corner2);
        yield single;
      }
      case POLYGON -> List.copyOf(points);
      case CIRCLE, MARKER -> center == null ? List.of() : List.of(center);
    };
  }

  /**
   * Finds which defining block sits at the clicked block's x and z.
   *
   * @return its index in {@link #definingBlocks()}, or null if none matches
   */
  public Integer indexAt(Location block) {
    List<Location> blocks = definingBlocks();
    for (int i = 0; i < blocks.size(); i++) {
      Location candidate = blocks.get(i);
      if (candidate.getBlockX() == block.getBlockX()
          && candidate.getBlockZ() == block.getBlockZ()) {
        return i;
      }
    }
    return null;
  }

  /**
   * Finds the defining block the player is looking at, so ghost blocks can be clicked even though
   * they have no hitbox. A block counts when the line of sight passes within one block of its
   * center; the nearest such block wins.
   *
   * @param eye the player's eye location
   * @param reach how far to look
   * @return the defining block in sight, or null if there is none
   */
  public Location cornerInSight(Location eye, double reach) {
    Vector origin = eye.toVector();
    Vector direction = eye.getDirection();
    Location nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    List<Location> blocks = definingBlocks();
    // Ghost blocks are drawn at the lowest corner's height, so aim at that height too.
    int drawnY = blocks.stream().mapToInt(Location::getBlockY).min().orElse(0);
    for (Location block : blocks) {
      if (!block.getWorld().equals(eye.getWorld())) continue;

      Vector toCenter =
          new Vector(block.getBlockX() + 0.5, drawnY + 0.5, block.getBlockZ() + 0.5)
              .subtract(origin);
      double along = toCenter.dot(direction);
      if (along < 0 || along > reach) continue;
      double offRaySquared = toCenter.lengthSquared() - along * along;
      if (offRaySquared > 1.0 || along >= nearestDistance) continue;

      nearest = block;
      nearestDistance = along;
    }
    return nearest;
  }

  /**
   * Builds the area shape from this session.
   *
   * @return the shape, or null while it is incomplete
   */
  public AreaGeometry toGeometry() {
    return switch (mode) {
      case RECT -> {
        if (corner1 == null || corner2 == null) yield null;
        yield new AreaGeometry(
            world,
            MapToolsUtils.rectangle(
                corner1.getBlockX(), corner1.getBlockZ(), corner2.getBlockX(), corner2.getBlockZ()),
            MapArea.RECT,
            null,
            null);
      }
      case POLYGON -> {
        if (points.size() < 3) yield null;
        List<MapPoint> centers =
            points.stream()
                .map(p -> new MapPoint(p.getBlockX() + 0.5, p.getBlockZ() + 0.5))
                .toList();
        yield new AreaGeometry(world, centers, MapArea.POLYGON, null, null);
      }
      case CIRCLE -> {
        if (center == null || radius == null) yield null;
        MapPoint middle = new MapPoint(center.getBlockX() + 0.5, center.getBlockZ() + 0.5);
        yield new AreaGeometry(
            world,
            MapToolsUtils.circle(middle.x(), middle.z(), radius),
            MapArea.CIRCLE,
            middle,
            (double) radius);
      }
      case MARKER -> {
        if (center == null) yield null;
        MapPoint spot = new MapPoint(center.getBlockX() + 0.5, center.getBlockZ() + 0.5);
        yield new AreaGeometry(world, List.of(spot), MapArea.MARKER, spot, null);
      }
    };
  }

  /**
   * The outline to preview: the finished shape, or the open line through the points of a polygon
   * that has fewer than three.
   *
   * @return the outline points, or an empty list if there is nothing to draw yet
   */
  public List<MapPoint> previewOutline() {
    AreaGeometry geometry = toGeometry();
    if (geometry != null) return geometry.points();
    if (mode == Mode.POLYGON && points.size() == 2) {
      return points.stream()
          .map(p -> new MapPoint(p.getBlockX() + 0.5, p.getBlockZ() + 0.5))
          .toList();
    }
    return List.of();
  }

  private List<Location> rectCorners() {
    World blockWorld = corner1.getWorld();
    int y = Math.min(corner1.getBlockY(), corner2.getBlockY());
    return List.of(
        corner1,
        new Location(blockWorld, corner2.getBlockX(), y, corner1.getBlockZ()),
        corner2,
        new Location(blockWorld, corner1.getBlockX(), y, corner2.getBlockZ()));
  }

  private void switchWorld(Location block) {
    String blockWorld = block.getWorld().getKey().asString();
    if (blockWorld.equals(world)) return;
    world = blockWorld;
    corner1 = null;
    corner2 = null;
    points.clear();
    center = null;
    picked = null;
  }
}
