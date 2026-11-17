package com.nearvanilla.iceCream.testutil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.AreaDisplays;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;

/**
 * Helpers for testing the map tools module with MockBukkit: worlds with real keys, a store backed
 * by a file in the fixture's data folder, editing players that record the dialogs they are shown
 * and builders for areas of every shape.
 *
 * <p>The module keeps its state in static fields ({@code MapToolsModule.store}, {@code
 * MapToolsModule.sessions} and the squaremap layers), so a test calls {@link #reset()} in its
 * teardown, before {@code IceCreamFixture.uninstall()}.
 *
 * <p>Players stand in a separate world ({@link #LIMBO}) while the areas are in the overworld. The
 * outlines in game are drawn for the world a player is in, and MockBukkit cannot draw all of the
 * display entities that the module spawns, so keeping the players elsewhere keeps the drawing out
 * of tests of the logic.
 */
public final class MapToolsTestUtils {
  /** The key of the first world, which typed claims are made in. */
  public static final String OVERWORLD = "minecraft:overworld";

  /** The key of a second world, to test areas in worlds that do not match. */
  public static final String NETHER = "minecraft:the_nether";

  /** The key of the world the editing players stand in. */
  public static final String LIMBO = "minecraft:the_end";

  private MapToolsTestUtils() {}

  /**
   * A mock server with the three worlds, the plugin fixture and an empty store. Open it in a setup
   * method and close it in a teardown method.
   */
  public static final class Scenario implements AutoCloseable {
    private final ServerMock server;
    private final KeyedWorld overworld;
    private final KeyedWorld nether;
    private final KeyedWorld limbo;
    private MapAreasStore store;

    private Scenario(String configYaml) {
      server = MockBukkit.mock();
      overworld = addWorld(server, OVERWORLD);
      nether = addWorld(server, NETHER);
      limbo = addWorld(server, LIMBO);
      configure(configYaml);
    }

    /**
     * Opens a scenario.
     *
     * @param configYaml the plugin configuration as YAML text
     * @return the scenario
     */
    public static Scenario open(String configYaml) {
      return new Scenario(configYaml);
    }

    /**
     * Replaces the plugin configuration and the store, which moves to a new data folder.
     *
     * @param configYaml the plugin configuration as YAML text
     */
    public void configure(String configYaml) {
      IceCreamFixture.install(server, configYaml);
      store = installStore();
    }

    /**
     * Returns the mock server.
     *
     * @return the server
     */
    public ServerMock server() {
      return server;
    }

    /**
     * Returns the first world, where typed claims are made.
     *
     * @return the overworld
     */
    public KeyedWorld overworld() {
      return overworld;
    }

    /**
     * Returns the second world.
     *
     * @return the nether
     */
    public KeyedWorld nether() {
      return nether;
    }

    /**
     * Returns the world the editing players stand in.
     *
     * @return the limbo world
     */
    public KeyedWorld limbo() {
      return limbo;
    }

    /**
     * Returns the installed store.
     *
     * @return the store
     */
    public MapAreasStore store() {
      return store;
    }

    /**
     * Adds a player that may manage areas, standing in the limbo world.
     *
     * @param name the player name
     * @return the added player
     */
    public DialogPlayer editor(String name) {
      return addEditor(server, name, limbo);
    }

    /**
     * Builds a block in the overworld.
     *
     * @return the location
     */
    public Location overworldBlock(int x, int y, int z) {
      return block(overworld, x, y, z);
    }

    @Override
    public void close() {
      reset();
      IceCreamFixture.uninstall();
      MockBukkit.unmock();
    }
  }

  /** A world that reports the key it was created with; MockBukkit gives every world the same. */
  public static final class KeyedWorld extends WorldMock {
    private final NamespacedKey key;

    private KeyedWorld(String key) {
      this.key = NamespacedKey.fromString(key);
      setName(this.key.getKey());
    }

    @Override
    public NamespacedKey getKey() {
      return key;
    }
  }

  /**
   * Adds a world with the given key to the server.
   *
   * @param server the mock server
   * @param key the world key, such as {@code minecraft:overworld}
   * @return the added world
   */
  public static KeyedWorld addWorld(ServerMock server, String key) {
    KeyedWorld world = new KeyedWorld(key);
    server.addWorld(world);
    return world;
  }

  /**
   * Returns the file the store of {@link #installStore()} saves to.
   *
   * @return the file in the fixture's data folder
   */
  public static File storeFile() {
    return new File(IceCreamFixture.dataFolder(), "map-areas.json");
  }

  /**
   * Writes one small PNG icon per name into the {@code map-icons} folder of the fixture's data
   * folder, creating the folder if it is missing. The folder is where the module looks for icons,
   * so they are loaded by {@code MapToolsUtils.init} or {@code MapIcons.load}.
   *
   * @param names the icon names, without the {@code .png} extension
   * @return the icon folder
   * @throws IOException if an icon cannot be written
   */
  public static File writeIcons(String... names) throws IOException {
    File folder = new File(IceCreamFixture.dataFolder(), "map-icons");
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new IOException("Could not create " + folder);
    }
    for (String name : names) {
      ImageIO.write(
          new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
          "png",
          new File(folder, name + ".png"));
    }
    return folder;
  }

  /**
   * Creates a squaremap API as a Mockito mock that has an icon registry and maps no world, so
   * {@code MapToolsUtils.init} loads the icons and adds no layers. A test that wants a world mapped
   * stubs {@code getWorldIfEnabled} on the returned mock.
   *
   * @return the squaremap mock
   */
  @SuppressWarnings("unchecked")
  public static Squaremap emptySquaremap() {
    Squaremap api = mock(Squaremap.class);
    when(api.iconRegistry()).thenReturn(mock(Registry.class));
    when(api.getWorldIfEnabled(any(WorldIdentifier.class))).thenReturn(Optional.empty());
    return api;
  }

  /**
   * Installs an empty store in {@code MapToolsModule.store}, saving to {@link #storeFile()}.
   *
   * @return the installed store
   */
  public static MapAreasStore installStore() {
    MapToolsModule.store = new MapAreasStore(storeFile());
    return MapToolsModule.store;
  }

  /**
   * Adds a player that may manage areas and records the dialogs shown to it.
   *
   * @param server the mock server
   * @param name the player name
   * @param home the world the player stands in
   * @return the added player
   */
  public static DialogPlayer addEditor(ServerMock server, String name, World home) {
    DialogPlayer player = DialogTestUtils.addPlayer(server, name);
    player.addAttachment(IceCream.instance, MapToolsUtils.PERMISSION, true);
    player.setLocation(new Location(home, 0, 64, 0));
    return player;
  }

  /** Clears the module's static state after a test. */
  public static void reset() {
    AreaDisplays.clearAll();
    MapToolsUtils.shutdown();
    MapToolsModule.sessions.clear();
    MapToolsModule.store = null;
  }

  /**
   * Builds a block location.
   *
   * @param world the world
   * @param x the block x
   * @param y the block y
   * @param z the block z
   * @return the location
   */
  public static Location block(World world, int x, int y, int z) {
    return new Location(world, x, y, z);
  }

  /**
   * Builds a rectangular claim covering the blocks between two corners.
   *
   * @param id the area id
   * @param world the world key
   * @return the area, labelled {@code Label <id>} and colored gold
   */
  public static MapArea rect(String id, String world, int x1, int z1, int x2, int z2) {
    return area(id, world, MapToolsUtils.rectangle(x1, z1, x2, z2), MapArea.RECT, null, null);
  }

  /**
   * Builds a polygon claim.
   *
   * @param id the area id
   * @param world the world key
   * @param coordinates the corner coordinates as x, z pairs
   * @return the area
   */
  public static MapArea polygon(String id, String world, double... coordinates) {
    List<MapPoint> points = new ArrayList<>();
    for (int i = 0; i + 1 < coordinates.length; i += 2) {
      points.add(new MapPoint(coordinates[i], coordinates[i + 1]));
    }
    return area(id, world, points, MapArea.POLYGON, null, null);
  }

  /**
   * Builds a circular claim around the middle of a block.
   *
   * @param id the area id
   * @param world the world key
   * @return the area
   */
  public static MapArea circle(String id, String world, int x, int z, int radius) {
    MapPoint center = MapToolsUtils.blockCenter(x, z);
    return area(
        id,
        world,
        MapToolsUtils.circle(center.x(), center.z(), radius),
        MapArea.CIRCLE,
        center,
        (double) radius);
  }

  /**
   * Builds a marker on the middle of a block.
   *
   * @param id the area id
   * @param world the world key
   * @return the area
   */
  public static MapArea marker(String id, String world, int x, int z) {
    MapPoint center = MapToolsUtils.blockCenter(x, z);
    return area(id, world, List.of(center), MapArea.MARKER, center, null);
  }

  private static MapArea area(
      String id,
      String world,
      List<MapPoint> points,
      String shape,
      MapPoint center,
      Double radius) {
    return new MapArea(
        id,
        world,
        points,
        "Label " + id,
        null,
        List.of(),
        "gold",
        shape,
        center,
        radius,
        null,
        null,
        null);
  }
}
