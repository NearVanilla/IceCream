package com.nearvanilla.iceCream.modules.mapTools.events;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A claim or marker was created, updated, deleted or renamed, after the change was saved.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-22
 */
public class MapAreaChangeEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  /** What happened to the area. A rename can also have changed other details. */
  public enum Change {
    CREATED,
    UPDATED,
    DELETED,
    RENAMED
  }

  private final Change change;
  private final MapArea area;
  private final String previousId;

  /**
   * @param area the area as it is now, or as it was when deleted
   * @param previousId the old id of a renamed area, else null
   */
  public MapAreaChangeEvent(Change change, MapArea area, String previousId) {
    this.change = change;
    this.area = area;
    this.previousId = previousId;
  }

  public Change getChange() {
    return change;
  }

  public MapArea getArea() {
    return area;
  }

  /** The old id of a renamed area, else null. */
  public String getPreviousId() {
    return previousId;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
