package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;

/**
 * A squaremap layer of claims or markers. Markers are handed to the map from the largest area to
 * the smallest, so smaller claims are drawn on top and can still be hovered and clicked when they
 * lie inside a larger one.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-21
 */
final class AreaLayer implements LayerProvider {

  private record Entry(Marker marker, double size) {}

  private final String label;
  private final int priority;
  private final Map<String, Entry> markers = new HashMap<>();

  /** The markers sorted for the map, rebuilt on change since squaremap asks for them often. */
  private volatile List<Marker> sorted = List.of();

  /**
   * @param priority the layer's place in the map's layer list and drawing order; higher is on top
   */
  AreaLayer(String label, int priority) {
    this.label = label;
    this.priority = priority;
  }

  /** Adds or replaces the marker of a claim. */
  void put(String id, Marker marker, List<MapPoint> outline) {
    markers.put(id, new Entry(marker, size(outline)));
    sort();
  }

  void remove(String id) {
    markers.remove(id);
    sort();
  }

  private void sort() {
    sorted =
        markers.values().stream()
            .sorted(Comparator.comparingDouble(Entry::size).reversed())
            .map(Entry::marker)
            .toList();
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public boolean showControls() {
    return true;
  }

  @Override
  public int layerPriority() {
    return priority;
  }

  @Override
  public int zIndex() {
    return 240 + priority;
  }

  @Override
  public Collection<Marker> getMarkers() {
    return sorted;
  }

  /** The surface of an outline in square blocks, using the shoelace formula. */
  private static double size(List<MapPoint> outline) {
    double twice = 0;
    for (int i = 0; i < outline.size(); i++) {
      MapPoint a = outline.get(i);
      MapPoint b = outline.get((i + 1) % outline.size());
      twice += a.x() * b.z() - b.x() * a.z();
    }
    return Math.abs(twice) / 2;
  }
}
