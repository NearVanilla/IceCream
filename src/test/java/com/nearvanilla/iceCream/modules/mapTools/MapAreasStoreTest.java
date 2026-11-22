package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapAreasStoreTest {

  private static final String WORLD = "minecraft:overworld";

  @TempDir Path folder;
  private File file;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    file = folder.resolve("map-areas.json").toFile();
    previousLogger = IceCream.logger;
    IceCream.logger = Logger.getLogger("MapAreasStoreTest");
  }

  @AfterEach
  void tearDown() {
    IceCream.logger = previousLogger;
  }

  private static MapArea rect(String id, String label) {
    return new MapArea(
        id,
        WORLD,
        MapToolsUtils.rectangle(0, 0, 9, 9),
        label,
        "A description",
        List.of("Dynant", "Alex"),
        "gold",
        MapArea.RECT,
        null,
        null,
        0.4,
        null,
        true);
  }

  private static MapArea circle(String id) {
    MapPoint center = MapToolsUtils.blockCenter(100, -50);
    return new MapArea(
        id,
        WORLD,
        MapToolsUtils.circle(center.x(), center.z(), 20),
        "Ring",
        null,
        List.of(),
        "#ff8800",
        MapArea.CIRCLE,
        center,
        20.0,
        null,
        null,
        null);
  }

  private MapAreasStore reloaded() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    store.load();
    return store;
  }

  private void write(String json) throws IOException {
    Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
  }

  @Test
  void missingFileLoadsEmpty() throws IOException {
    assertTrue(reloaded().getAreas().isEmpty());
    assertFalse(file.exists());
  }

  @Test
  void savedAreasLoadTheSame() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    MapArea town = rect("town", "Spawn Town");
    store.putArea(town);

    assertEquals(town, reloaded().getArea("town"));
  }

  @Test
  void circlesAreSavedWithoutPointsAndRebuilt() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    MapArea ring = circle("ring");
    store.putArea(ring);

    MapAreasStore.StoredData stored = JsonLoader.load(file, MapAreasStore.StoredData.class);
    assertTrue(stored.areas().getFirst().points().isEmpty());
    MapArea loaded = reloaded().getArea("ring");
    assertEquals(ring.center(), loaded.center());
    assertEquals(20.0, loaded.radius());
    assertEquals(ring.points(), loaded.points());
  }

  @Test
  void eachSaveKeepsThePreviousVersionAsBackup() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    store.putArea(rect("town", "First"));
    store.putArea(rect("town", "Second"));

    MapAreasStore backup = new MapAreasStore(new File(folder.toFile(), "map-areas.json.bak"));
    backup.load();
    assertEquals("First", backup.getArea("town").label());
    assertEquals("Second", reloaded().getArea("town").label());
  }

  /** A hand-edited file may be broken, so it must not replace a good backup. */
  @Test
  void handEditedFileDoesNotReplaceTheBackup() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    store.putArea(rect("town", "First"));
    store.putArea(rect("town", "Second"));
    File backupFile = new File(folder.toFile(), "map-areas.json.bak");
    String backupBefore = Files.readString(backupFile.toPath());

    write("{\"version\":1,\"areas\":[]}");
    assertTrue(file.setLastModified(file.lastModified() + 5_000));
    store.putArea(rect("town", "Third"));

    assertEquals(backupBefore, Files.readString(backupFile.toPath()));
  }

  @Test
  void renameReplacesTheOldId() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    MapArea town = rect("town", "Spawn Town");
    store.putArea(town);
    store.renameArea("town", town.withId("spawn"));

    MapAreasStore loaded = reloaded();
    assertNull(loaded.getArea("town"));
    assertEquals("Spawn Town", loaded.getArea("spawn").label());
  }

  @Test
  void removeDeletesAndReturnsTheArea() throws IOException {
    MapAreasStore store = new MapAreasStore(file);
    store.putArea(rect("town", "Spawn Town"));

    assertEquals("town", store.removeArea("town").id());
    assertNull(store.removeArea("town"));
    assertNull(reloaded().getArea("town"));
  }

  /** Files from before a field was removed still load. */
  @Test
  void loadsFilesWithKeysNoLongerUsed() throws IOException {
    write(
        """
        {"version":1,"areas":[{"id":"old","world":"minecraft:overworld",
        "points":[{"x":0,"z":0},{"x":10,"z":0},{"x":10,"z":10}],"label":"Old","color":"gold",
        "shape":"polygon","hidden":true}]}
        """);

    MapArea old = reloaded().getArea("old");
    assertNotNull(old);
    assertEquals("Old", old.label());
    assertEquals(List.of(), old.owners());
    assertFalse(old.allowsNearby());
  }

  @Test
  void refusesFilesFromANewerVersion() throws IOException {
    write("{\"version\":99,\"areas\":[]}");
    assertThrows(IllegalStateException.class, () -> reloaded());
  }

  /** A broken file stops the module and is left as it was. */
  @Test
  void brokenFileThrowsAndIsLeftAlone() throws IOException {
    write("{\"version\":1,\"areas\":[");
    // Jackson reports parse errors unchecked; the module catches any exception and stays off.
    assertThrows(Exception.class, () -> reloaded());
    assertEquals("{\"version\":1,\"areas\":[", Files.readString(file.toPath()));
  }

  @Test
  void parsesOwners() {
    assertEquals(List.of("Dynant", "Alex"), MapAreasStore.parseOwners(" Dynant , ,Alex,"));
    assertEquals(List.of(), MapAreasStore.parseOwners(null));
    assertEquals(List.of(), MapAreasStore.parseOwners("  "));
  }
}
