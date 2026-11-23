package com.nearvanilla.iceCream.modules.mapTools;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockbukkit.mockbukkit.matcher.plugin.PluginManagerFiredEventClassMatcher.hasNotFiredEventInstance;
import static org.mockbukkit.mockbukkit.matcher.plugin.PluginManagerFiredEventFilterMatcher.hasFiredFilteredEvent;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.events.MapAreaChangeEvent;
import com.nearvanilla.iceCream.modules.mapTools.events.MapAreaChangeEvent.Change;
import com.nearvanilla.iceCream.modules.mapTools.events.MapToolsErrorEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Every change and error is announced, so listeners such as the mod channel hear about it. */
class MapToolsEventsTest {

  private static final String WORLD = "minecraft:overworld";

  @TempDir Path folder;
  private ServerMock server;
  private PlayerMock player;
  private MapAreasStore previousStore;
  private FileConfiguration previousConfig;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer("Staff");
    previousStore = MapToolsModule.store;
    previousConfig = IceCream.config;
    previousLogger = IceCream.logger;
    MapToolsModule.store = new MapAreasStore(folder.resolve("map-areas.json").toFile());
    IceCream.config = new YamlConfiguration();
    IceCream.logger = Logger.getLogger("MapToolsEventsTest");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    MapToolsModule.store = previousStore;
    IceCream.config = previousConfig;
    IceCream.logger = previousLogger;
  }

  private static AreaGeometry rect() {
    return new AreaGeometry(WORLD, MapToolsUtils.rectangle(0, 0, 9, 9), MapArea.RECT, null, null);
  }

  private boolean create(String id) {
    return MapToolsUtils.createArea(
        player, id, rect(), "gold", "Spawn", List.of("Dynant"), null, null, null, false);
  }

  private void assertChange(Change change, String id, String previousId) {
    assertThat(
        server.getPluginManager(),
        hasFiredFilteredEvent(
            MapAreaChangeEvent.class,
            event ->
                event.getChange() == change
                    && event.getArea().id().equals(id)
                    && java.util.Objects.equals(event.getPreviousId(), previousId)));
  }

  @Test
  void createFiresCreated() {
    assertTrue(create("spawn"));
    assertChange(Change.CREATED, "spawn", null);
  }

  @Test
  void createCanAllowNearbyInOneSave() {
    MapToolsUtils.createArea(
        player, "spawn", rect(), "gold", "Spawn", List.of(), null, null, null, true);
    assertTrue(MapToolsModule.store.getArea("spawn").allowsNearby());
  }

  @Test
  void refusedCreateFiresAnErrorAndNoChange() {
    assertFalse(create("Not An Id"));
    assertThat(server.getPluginManager(), hasNotFiredEventInstance(MapAreaChangeEvent.class));
    assertThat(
        server.getPluginManager(),
        hasFiredFilteredEvent(MapToolsErrorEvent.class, event -> event.getSender() == player));
  }

  @Test
  void saveFiresUpdated() {
    create("spawn");
    server.getPluginManager().clearEvents();
    MapToolsUtils.saveArea(MapToolsModule.store.getArea("spawn").withLabel("New label"));
    assertChange(Change.UPDATED, "spawn", null);
  }

  @Test
  void renameFiresRenamedWithTheOldId() {
    create("spawn");
    server.getPluginManager().clearEvents();
    MapToolsUtils.renameArea(MapToolsModule.store.getArea("spawn"), "town");
    assertChange(Change.RENAMED, "town", "spawn");
    assertNull(MapToolsModule.store.getArea("spawn"));
  }

  @Test
  void deleteFiresDeleted() {
    create("spawn");
    server.getPluginManager().clearEvents();
    MapToolsUtils.deleteArea("spawn");
    assertChange(Change.DELETED, "spawn", null);
  }

  @Test
  void deletingSomethingMissingFiresNothing() {
    assertNull(MapToolsUtils.deleteArea("missing"));
    assertThat(server.getPluginManager(), hasNotFiredEventInstance(MapAreaChangeEvent.class));
  }

  @Test
  void errorsAreShownAndAnnounced() {
    Component message = Component.text("Something went wrong.");
    MapToolsUtils.error(player, message);

    assertEquals(message, player.nextComponentMessage());
    assertThat(
        server.getPluginManager(),
        hasFiredFilteredEvent(
            MapToolsErrorEvent.class,
            event -> event.getSender() == player && event.getMessage().equals(message)));
  }
}
