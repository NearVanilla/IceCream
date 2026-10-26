package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the drawn areas and persists them to a JSON file in the plugin data folder. squaremap does
 * not persist markers added through its API, so this file is the source of truth and the map layer
 * is rebuilt from it on startup.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class MapAreasStore {

  /** A point on the map in block coordinates. */
  public record MapPoint(double x, double z) {}

  /**
   * A named area (claim) or marker on the map. {@code shape} says how it is edited: a {@value
   * #RECT} keeps its four corners square, a {@value #POLYGON} has free points, a {@value #CIRCLE}
   * is defined by its {@code center} and {@code radius}, and a {@value #MARKER} is a single spot at
   * its {@code center} shown with an {@code icon}. Circle and marker points are built from the
   * center when loading and are not written to disk. A {@code hidden} area is not drawn or clicked
   * in edit mode.
   */
  public record MapArea(
      String id,
      String world,
      List<MapPoint> points,
      String label,
      String description,
      List<String> owners,
      String color,
      String shape,
      MapPoint center,
      Double radius,
      Double opacity,
      String icon,
      Boolean hidden) {

    public MapArea {
      // Older or hand-edited files may leave owners out.
      owners = owners == null ? List.of() : owners;
      // The stored form is a lowercase hex code or color name; unparseable colors stay as they are.
      String normalized = MapToolsUtils.normalizeColor(color);
      color = normalized != null ? normalized : color;
    }

    public static final String RECT = "rect";
    public static final String POLYGON = "polygon";
    public static final String CIRCLE = "circle";
    public static final String MARKER = "marker";

    public boolean isCircle() {
      return CIRCLE.equals(shape);
    }

    /** Whether the area is left out of edit mode, so it does not get in the way or get clicked. */
    public boolean isHidden() {
      return Boolean.TRUE.equals(hidden);
    }

    public boolean isMarker() {
      return MARKER.equals(shape);
    }

    public MapArea withId(String id) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    public MapArea withLabel(String label) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    public MapArea withDescription(String description) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    public MapArea withOwners(List<String> owners) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    public MapArea withColor(String color) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    /**
     * @param hidden whether to leave the area out of edit mode; stored only when true
     */
    public MapArea withHidden(boolean hidden) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden ? true : null);
    }

    /**
     * @param opacity how solid the fill is on the web map, 0 to 1, or null for the default
     */
    public MapArea withOpacity(Double opacity) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    public MapArea withGeometry(AreaGeometry geometry) {
      return new MapArea(
          id,
          geometry.world(),
          geometry.points(),
          label,
          description,
          owners,
          color,
          geometry.shape(),
          geometry.center(),
          geometry.radius(),
          opacity,
          icon,
          hidden);
    }

    /**
     * @param icon the name of the icon a marker is shown with, or null for a plain dot
     */
    public MapArea withIcon(String icon) {
      return new MapArea(
          id,
          world,
          points,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    /** Circles and markers are saved without points; rebuild them from the center. */
    private MapArea withOutline() {
      List<MapPoint> outline;
      if (isCircle()) {
        outline = MapToolsUtils.circle(center.x(), center.z(), radius);
      } else if (isMarker()) {
        outline = List.of(center);
      } else {
        return this;
      }
      return new MapArea(
          id,
          world,
          outline,
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }

    /** The area as written to disk: circles and markers leave out their points. */
    private MapArea forStorage() {
      if (!isCircle() && !isMarker()) return this;
      return new MapArea(
          id,
          world,
          List.of(),
          label,
          description,
          owners,
          color,
          shape,
          center,
          radius,
          opacity,
          icon,
          hidden);
    }
  }

  /**
   * Splits typed owner names on commas, trimming each and dropping empty ones.
   *
   * @param text names such as {@code "Dynant, Alex, Sam"}, or null
   * @return the names, empty if there are none
   */
  public static List<String> parseOwners(String text) {
    if (text == null) return List.of();
    return Arrays.stream(text.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .toList();
  }

  /** The shape part of an area, as built by an edit session. */
  public record AreaGeometry(
      String world, List<MapPoint> points, String shape, MapPoint center, Double radius) {}

  /**
   * The on-disk layout of the store. {@code version} says which layout the file uses, so a future
   * change to it can convert older files instead of guessing their format.
   */
  public record StoredData(int version, List<MapArea> areas) {}

  /** The layout written by this version of the plugin. */
  private static final int VERSION = 1;

  private final File file;
  private final Map<String, MapArea> areas = new LinkedHashMap<>();

  /** When the file was last loaded or written by this store; a different time means a hand edit. */
  private long knownModified;

  public MapAreasStore(File file) {
    this.file = file;
  }

  /**
   * Loads areas from disk, replacing anything held in memory.
   *
   * @throws IOException if the file cannot be read, so the module does not start and later saves do
   *     not overwrite claims that failed to load
   * @throws IllegalStateException if the file was written by a newer version of the plugin
   */
  public void load() throws IOException {
    areas.clear();
    if (!file.exists()) return;

    StoredData data = JsonLoader.load(file, StoredData.class);
    if (data.version() > VERSION) {
      throw new IllegalStateException(
          file.getName()
              + " has version "
              + data.version()
              + ", but this plugin only supports up to version "
              + VERSION);
    }
    if (data.areas() != null) {
      data.areas().forEach(area -> areas.put(area.id(), area.withOutline()));
    }
    knownModified = file.lastModified();
  }

  /**
   * Writes all areas to disk. The data is written to a temporary file first and then moved over the
   * real file, so a crash during the write never leaves a half-written file behind. The previous
   * version is kept as a {@code .bak} file, so a bad change can be undone by hand; a file edited by
   * hand since it was loaded or saved may be broken, so it does not replace the backup.
   */
  public void save() {
    try {
      file.getParentFile().mkdirs();
      File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
      JsonLoader.save(
          temporary,
          new StoredData(VERSION, areas.values().stream().map(MapArea::forStorage).toList()));
      if (file.exists() && file.lastModified() == knownModified) {
        Files.copy(
            file.toPath(),
            new File(file.getParentFile(), file.getName() + ".bak").toPath(),
            StandardCopyOption.REPLACE_EXISTING);
      }
      try {
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      knownModified = file.lastModified();
    } catch (IOException | RuntimeException e) {
      IceCream.logger.severe("Failed to save " + file.getName() + ": " + e.getMessage());
    }
  }

  public MapArea getArea(String id) {
    return areas.get(id);
  }

  public Collection<MapArea> getAreas() {
    return Collections.unmodifiableCollection(areas.values());
  }

  public void putArea(MapArea area) {
    areas.put(area.id(), area);
    save();
  }

  /** Replaces an area under a new id, saving once so the file never holds both or neither. */
  public void renameArea(String oldId, MapArea area) {
    areas.remove(oldId);
    areas.put(area.id(), area);
    save();
  }

  public MapArea removeArea(String id) {
    MapArea removed = areas.remove(id);
    if (removed != null) save();
    return removed;
  }
}
