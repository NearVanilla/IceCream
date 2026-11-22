package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes the claims and markers to a public JSON file in squaremap's web folder, so website can
 * look them up by id. It is rewritten on startup and after every change.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-20
 */
public final class ClaimsExport {

  /** Where the file is written, relative to squaremap's web folder. */
  public static final String PATH = "icecream/map-areas.json";

  /** The layout of the file; raised when a change could break readers. */
  public static final int VERSION = 1;

  /**
   * One claim or marker as websites see it.
   *
   * @param kind {@code claim} or {@code marker}
   * @param color the color as a hex code, with the default already applied
   * @param fillOpacity how solid the fill is, from 0 to 1, with the default already applied
   * @param shape {@code rect}, {@code polygon}, {@code circle} or {@code marker}
   * @param center the center of a circle or the spot of a marker, else null
   * @param radius the radius of a circle, else null
   * @param points the corners of a rectangle or polygon, else null
   * @param icon the icon of a marker, or null for a plain dot
   * @param allowNearby whether others may claim close to this claim, short of overlapping it;
   *     always false for markers
   */
  public record ExportedArea(
      String id,
      String kind,
      String world,
      String label,
      String description,
      List<String> owners,
      String color,
      double fillOpacity,
      String shape,
      MapPoint center,
      Double radius,
      List<MapPoint> points,
      String icon,
      boolean allowNearby) {}

  /**
   * The whole file.
   *
   * @param updated when the file was written, in milliseconds since the epoch
   * @param icons the names of the marker icons that can be used
   * @param areas the claims and markers, keyed by id
   */
  public record ExportedData(
      int version, long updated, List<String> icons, Map<String, ExportedArea> areas) {}

  private ClaimsExport() {
    // Utility class; prevent instantiation.
  }

  /**
   * Converts an area to its public form.
   *
   * @param area the area
   * @param color the color to draw it with, as a hex code
   * @param fillOpacity how solid its fill is, from 0 to 1
   * @return the public form
   */
  public static ExportedArea of(MapArea area, String color, double fillOpacity) {
    boolean outlined = !area.isCircle() && !area.isMarker();
    return new ExportedArea(
        area.id(),
        area.isMarker() ? "marker" : "claim",
        area.world(),
        area.label(),
        area.description(),
        area.owners(),
        color,
        fillOpacity,
        area.shape() != null ? area.shape() : MapArea.POLYGON,
        outlined ? null : area.center(),
        area.isCircle() ? area.radius() : null,
        outlined ? area.points() : null,
        area.isMarker() ? area.icon() : null,
        !area.isMarker() && area.allowsNearby());
  }

  /** Writes the file one at a time, in order, off the main thread. */
  private static ExecutorService writer;

  /** The newest data waiting to be written; older data still waiting is skipped. */
  private static final AtomicReference<Pending> pending = new AtomicReference<>();

  private record Pending(Path file, ExportedData data) {}

  /**
   * Writes all areas to the file in squaremap's web folder, off the main thread. The data is taken
   * now, so later changes to the areas do not affect it. When several writes are waiting, only the
   * newest one is written. Failures are logged and do not affect the stored areas.
   *
   * @param webDir squaremap's web folder
   * @param areas the areas to write
   * @param icons the names of the marker icons
   */
  public static synchronized void writeLater(
      Path webDir, Collection<MapArea> areas, List<String> icons) {
    Map<String, ExportedArea> exported = new LinkedHashMap<>();
    for (MapArea area : areas) {
      exported.put(
          area.id(),
          of(area, MapToolsUtils.areaColor(area).asHexString(), MapToolsUtils.fillOpacity(area)));
    }
    Pending next =
        new Pending(
            webDir.resolve(PATH),
            new ExportedData(VERSION, System.currentTimeMillis(), List.copyOf(icons), exported));
    // A write is already queued: it picks up this data instead.
    if (pending.getAndSet(next) != null) return;
    if (writer == null) {
      writer =
          Executors.newSingleThreadExecutor(
              Thread.ofPlatform().daemon().name("IceCream map export").factory());
    }
    writer.execute(ClaimsExport::writePending);
  }

  private static void writePending() {
    Pending next = pending.getAndSet(null);
    if (next == null) return;
    try {
      write(next.file(), next.data());
    } catch (IOException | RuntimeException e) {
      IceCream.logger.warning("Failed to write " + PATH + " for the web map: " + e.getMessage());
    }
  }

  /** Waits a few seconds for queued writes to finish, for when the plugin shuts down. */
  public static synchronized void finishWrites() {
    if (writer == null) return;
    writer.shutdown();
    try {
      if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
        IceCream.logger.warning("Gave up waiting to write " + PATH + " for the web map.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    writer = null;
    // Anything still waiting would block the next write after a restart.
    pending.set(null);
  }

  /**
   * Writes the data through a temporary file, so the web server never serves a half-written file.
   *
   * @param file the file to write
   * @param data the data
   * @throws IOException if writing fails
   */
  static void write(Path file, ExportedData data) throws IOException {
    Files.createDirectories(file.getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    JsonLoader.save(temporary.toFile(), data);
    try {
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
