package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.DisplayWorld;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A map tools scenario for testing what is drawn in game: the usual {@link Scenario}, plus a flat
 * world named {@link #DRAWING} that keeps its block displays, and a viewer standing in it at 0, 64,
 * 0. Areas to draw are stored with {@link #DRAWING} as their world.
 *
 * <p>The module draws four kinds of block display, which the queries tell apart by their size: a
 * ghost block is a little larger than a block ({@link #ghosts()}), an outline piece is {@code 0.15}
 * thick ({@link #lines(Material)}), a grid line is {@code 0.04} thick ({@link #gridLines()}) and a
 * grid dot is {@code 0.2} wide ({@link #dots()}).
 *
 * <p>The ground of the world is flat at height {@link #GROUND}, so lines are drawn at {@link
 * #LINE_HEIGHT}. The world has no chunks loaded until a test calls {@code loadChunks}, which leaves
 * outlines drawn there only as far as ghost blocks.
 */
public final class DisplayScenario implements AutoCloseable {
  /** The key of the world the displays are drawn in. */
  public static final String DRAWING = "icecream:drawing";

  /** The height of the top block of the flat ground. */
  public static final int GROUND = 4;

  /** The height outline pieces are drawn at: one block above the ground plus a little lift. */
  public static final double LINE_HEIGHT = GROUND + 1 + 0.15;

  private static final double TOLERANCE = 1e-4;

  private final Scenario scenario;
  private final DisplayWorld world;
  private final Viewer viewer;

  private DisplayScenario(String configYaml) {
    scenario = Scenario.open(configYaml);
    world = DisplayTestUtils.addWorld(scenario.server(), DRAWING);
    viewer = DisplayTestUtils.addViewer(scenario.server(), "Alex", world);
  }

  /**
   * Opens the scenario.
   *
   * @param configYaml the plugin configuration as YAML text
   * @return the scenario, to close in a teardown method
   */
  public static DisplayScenario open(String configYaml) {
    return new DisplayScenario(configYaml);
  }

  /**
   * Returns the underlying map tools scenario.
   *
   * @return the scenario
   */
  public Scenario scenario() {
    return scenario;
  }

  /**
   * Returns the mock server.
   *
   * @return the server
   */
  public ServerMock server() {
    return scenario.server();
  }

  /**
   * Returns the world the displays are drawn in.
   *
   * @return the drawing world
   */
  public DisplayWorld world() {
    return world;
  }

  /**
   * Returns the player standing in the drawing world.
   *
   * @return the viewer
   */
  public Viewer viewer() {
    return viewer;
  }

  /**
   * Adds another player that may manage areas, standing in the drawing world.
   *
   * @param name the player name
   * @return the added viewer
   */
  public Viewer addViewer(String name) {
    return DisplayTestUtils.addViewer(server(), name, world);
  }

  /**
   * Puts an area in the store.
   *
   * @param area the area
   * @return the same area
   */
  public MapArea stored(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  /**
   * Moves a player to a spot of the drawing world, at height 64.
   *
   * @param viewer the player
   * @param x the x coordinate
   * @param z the z coordinate
   */
  public void moveTo(Viewer viewer, double x, double z) {
    viewer.setLocation(new Location(world, x, 64, z));
  }

  /**
   * Builds a location in the drawing world.
   *
   * @param x the block x
   * @param y the block y
   * @param z the block z
   * @return the location
   */
  public Location block(int x, int y, int z) {
    return new Location(world, x, y, z);
  }

  /**
   * Returns the ghost blocks: the outlined blocks that mark corners and centers.
   *
   * @return the ghost block displays
   */
  public List<BlockDisplay> ghosts() {
    return world.displays().stream().filter(d -> d.getTransformation().getScale().y() > 1).toList();
  }

  /**
   * Returns the ghost blocks showing the given block.
   *
   * @param block the block type of the ghost
   * @return the ghost block displays
   */
  public List<BlockDisplay> ghosts(Material block) {
    return ghosts().stream().filter(d -> d.getBlock().getMaterial() == block).toList();
  }

  /**
   * Returns the outline pieces showing the given block.
   *
   * @param block the block type of the outline
   * @return the outline displays
   */
  public List<BlockDisplay> lines(Material block) {
    return world.displays().stream()
        .filter(d -> isThick(d, 0.15f) && d.getBlock().getMaterial() == block)
        .toList();
  }

  /**
   * Returns every outline piece, whatever block it shows.
   *
   * @return the outline displays
   */
  public List<BlockDisplay> lines() {
    return world.displays().stream().filter(d -> isThick(d, 0.15f)).toList();
  }

  /**
   * Returns the faint grid lines.
   *
   * @return the grid line displays
   */
  public List<BlockDisplay> gridLines() {
    return world.displays().stream().filter(d -> isThick(d, 0.04f)).toList();
  }

  /**
   * Returns the small glowing dots where grid lines cross.
   *
   * @return the dot displays
   */
  public List<BlockDisplay> dots() {
    return world.displays().stream()
        .filter(d -> Math.abs(d.getTransformation().getScale().x() - 0.2f) < TOLERANCE)
        .toList();
  }

  private static boolean isThick(BlockDisplay display, float thickness) {
    var scale = display.getTransformation().getScale();
    return Math.abs(scale.y() - thickness) < TOLERANCE
        && Math.abs(scale.z() - thickness) < TOLERANCE;
  }

  @Override
  public void close() {
    scenario.close();
  }
}
