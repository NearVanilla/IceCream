package com.nearvanilla.iceCream.modules.mapTools;

/**
 * The shapes a form creates or manages from typed coordinates: rectangles and circles by their
 * corners or center and radius, and markers by their spot.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-20
 */
public enum AreaShape {
  RECT("Rectangle"),
  CIRCLE("Circle"),
  MARKER("Marker");

  private final String label;

  AreaShape(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
