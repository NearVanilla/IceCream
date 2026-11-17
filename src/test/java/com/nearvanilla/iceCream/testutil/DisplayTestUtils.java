package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.BlockDisplayMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Helpers for testing code that draws with block display entities. MockBukkit spawns display
 * entities but throws an {@code UnimplementedOperationException} from {@code setVisibleByDefault},
 * which the map tools call on every display they spawn, so a test draws in a {@link DisplayWorld}
 * instead, whose displays accept it.
 */
public final class DisplayTestUtils {
  private DisplayTestUtils() {}

  /**
   * Returns the color a display glows in. MockBukkit keeps the glow color in an int where -1 means
   * "not set", which is also what white is, so a display glowing in white reads back as no color
   * there. This reads a glowing display without a color as white, as a real server does.
   *
   * @param display the display
   * @return the glow color, or null if the display does not glow
   */
  public static Color glowColor(BlockDisplay display) {
    if (!display.isGlowing()) return null;
    Color color = display.getGlowColorOverride();
    return color == null ? Color.WHITE : color;
  }

  /**
   * Adds a world that keeps the block displays spawned in it and reports the given key.
   *
   * @param server the mock server
   * @param key the world key, such as {@code icecream:drawing}
   * @return the added world
   */
  public static DisplayWorld addWorld(ServerMock server, String key) {
    DisplayWorld world = new DisplayWorld(key);
    server.addWorld(world);
    return world;
  }

  /**
   * A world whose block displays can be switched to hidden by default, and that loads the chunks a
   * test asks for, so lines drawn along the ground can find it.
   */
  public static final class DisplayWorld extends WorldMock {
    private final NamespacedKey key;

    private DisplayWorld(String key) {
      this.key = NamespacedKey.fromString(key);
      setName(this.key.getKey());
    }

    @Override
    public NamespacedKey getKey() {
      return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> T spawn(
        Location location,
        Class<T> clazz,
        Consumer<? super T> function,
        SpawnReason reason,
        boolean randomizeData,
        boolean allowPassengers) {
      if (clazz != BlockDisplay.class) {
        return super.spawn(location, clazz, function, reason, randomizeData, allowPassengers);
      }
      ServerMock server = MockBukkit.getMock();
      HideableBlockDisplay display = new HideableBlockDisplay(server, UUID.randomUUID());
      display.setLocation(location);
      display.setSpawnReason(reason);
      T spawned = (T) display;
      function.accept(spawned);
      server.registerEntity(display);
      return spawned;
    }

    /**
     * Returns the block displays that are alive in this world.
     *
     * @return the displays, oldest first
     */
    public List<BlockDisplay> displays() {
      Collection<BlockDisplay> displays = getEntitiesByClass(BlockDisplay.class);
      return List.copyOf(displays);
    }

    /**
     * Returns the live block displays showing a block of the given type.
     *
     * @param block the block type
     * @return the displays
     */
    public List<BlockDisplay> displays(Material block) {
      return displays().stream().filter(d -> d.getBlock().getMaterial() == block).toList();
    }

    /**
     * Loads every chunk that covers the block rectangle, so ground can be found there.
     *
     * @param minX the lowest block x
     * @param minZ the lowest block z
     * @param maxX the highest block x
     * @param maxZ the highest block z
     */
    public void loadChunks(int minX, int minZ, int maxX, int maxZ) {
      for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
        for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
          loadChunk(chunkX, chunkZ);
        }
      }
    }
  }

  /**
   * Adds a player that may manage areas, stands in the given world and remembers which entities
   * were shown to it.
   *
   * @param server the mock server
   * @param name the player name
   * @param home the world the player stands in
   * @return the added player
   */
  public static Viewer addViewer(ServerMock server, String name, World home) {
    Viewer viewer = new Viewer(server, name);
    server.addPlayer(viewer);
    viewer.addAttachment(IceCream.instance, MapToolsUtils.PERMISSION, true);
    viewer.setLocation(new Location(home, 0, 64, 0));
    return viewer;
  }

  /**
   * A player that records the dialogs shown to it and the entities it was made to see, and whose
   * line of sight ends at a block the test chooses, since MockBukkit does not implement {@code
   * getTargetBlockExact}.
   */
  public static final class Viewer extends DialogPlayer {
    private final List<Entity> shown = new ArrayList<>();
    private Block target;
    private int requestedDistance = -1;
    private FluidCollisionMode requestedMode;

    private Viewer(ServerMock server, String name) {
      super(server, name);
    }

    @Override
    public void showEntity(Plugin plugin, Entity entity) {
      shown.add(entity);
      super.showEntity(plugin, entity);
    }

    /**
     * Changes the block the player looks at.
     *
     * @param target the block, or null for a player looking at the sky
     */
    public void lookAt(Block target) {
      this.target = target;
    }

    /**
     * Returns the distance the last {@code getTargetBlockExact} call asked for.
     *
     * @return the distance, or -1 if the player was never asked
     */
    public int requestedDistance() {
      return requestedDistance;
    }

    /**
     * Returns whether the last {@code getTargetBlockExact} call looked through fluids.
     *
     * @return the mode, or null if the player was never asked
     */
    public FluidCollisionMode requestedMode() {
      return requestedMode;
    }

    @Override
    public Block getTargetBlockExact(int maxDistance, FluidCollisionMode fluidCollisionMode) {
      requestedDistance = maxDistance;
      requestedMode = fluidCollisionMode;
      return target;
    }

    /**
     * Returns every entity the player was shown, including ones removed since.
     *
     * @return the entities, oldest first
     */
    public List<Entity> shown() {
      return List.copyOf(shown);
    }

    /**
     * Returns the entities the player was shown that are still alive.
     *
     * @return the living entities, oldest first
     */
    public List<BlockDisplay> visibleDisplays() {
      return shown.stream()
          .filter(entity -> entity instanceof BlockDisplay && entity.isValid())
          .map(BlockDisplay.class::cast)
          .toList();
    }
  }

  /** A block display that remembers whether it is visible by default. */
  public static final class HideableBlockDisplay extends BlockDisplayMock {
    private boolean visibleByDefault = true;

    private HideableBlockDisplay(ServerMock server, UUID id) {
      super(server, id);
    }

    @Override
    public void setVisibleByDefault(boolean visible) {
      visibleByDefault = visible;
    }

    @Override
    public boolean isVisibleByDefault() {
      return visibleByDefault;
    }
  }
}
