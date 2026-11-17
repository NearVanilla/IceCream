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
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.StoredData;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.core.JacksonException;

/** Saving and loading the areas file. */
class MapAreasStoreFileTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private File file;

  @BeforeEach
  void setUp() {
    IceCreamFixture.install(MockBukkit.mock(), "");
    file = MapToolsTestUtils.storeFile();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private MapAreasStore store() {
    return new MapAreasStore(file);
  }

  private MapAreasStore reloaded() throws IOException {
    MapAreasStore fresh = store();
    fresh.load();
    return fresh;
  }

  private String read(File target) throws IOException {
    return Files.readString(target.toPath());
  }

  // The file always writes the hidden flag, so an area that never set it comes back with false
  // instead of null. Both mean the area is shown, which is what the comparison looks at.
  private static void assertSameArea(MapArea expected, MapArea actual) {
    assertNotNull(actual);
    assertEquals(expected.isHidden(), actual.isHidden());
    assertEquals(expected.withHidden(false), actual.withHidden(false));
  }

  private File backup() {
    return new File(file.getParentFile(), file.getName() + ".bak");
  }

  private File temporary() {
    return new File(file.getParentFile(), file.getName() + ".tmp");
  }

  // Saves are told apart by the file's modification time. The tests move it on instead of waiting
  // for the clock, since two saves in a row can land in the same clock tick.
  private void touch(File target) {
    assertTrue(target.setLastModified(target.lastModified() + 60_000));
  }

  @Test
  void loadWithoutAFileLeavesTheStoreEmpty() throws IOException {
    MapAreasStore store = store();

    store.load();

    assertEquals(List.of(), List.copyOf(store.getAreas()));
    assertFalse(file.exists());
  }

  @Test
  void loadWithoutAFileForgetsTheAreasHeldInMemory() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 9, 9));
    assertTrue(file.delete());

    store.load();

    assertNull(store.getArea("a"));
  }

  @Test
  void aSavedRectangleComesBackEqual() throws IOException {
    MapArea area = MapToolsTestUtils.rect("home", WORLD, -20, 5, 14, 40);
    MapAreasStore store = store();

    store.putArea(area);

    assertSameArea(area, reloaded().getArea("home"));
  }

  @Test
  void aSavedPolygonComesBackEqual() throws IOException {
    MapArea area = MapToolsTestUtils.polygon("shape", WORLD, 0.5, 0.5, 30.5, 0.5, 15.5, -20.5);
    MapAreasStore store = store();

    store.putArea(area);

    assertSameArea(area, reloaded().getArea("shape"));
  }

  @Test
  void aSavedCircleComesBackEqualWithItsOutlineRebuilt() throws IOException {
    MapArea area = MapToolsTestUtils.circle("pond", WORLD, 10, -30, 25);
    MapAreasStore store = store();

    store.putArea(area);

    MapArea loaded = reloaded().getArea("pond");
    assertSameArea(area, loaded);
    assertEquals(MapToolsUtils.circle(10.5, -29.5, 25), loaded.points());
  }

  @Test
  void aSavedMarkerComesBackEqualWithItsSpotAsItsOutline() throws IOException {
    MapArea area = MapToolsTestUtils.marker("spot", WORLD, -3, 8).withIcon("pin");
    MapAreasStore store = store();

    store.putArea(area);

    MapArea loaded = reloaded().getArea("spot");
    assertSameArea(area, loaded);
    assertEquals(List.of(new MapPoint(-2.5, 8.5)), loaded.points());
  }

  @Test
  void circlesAndMarkersAreWrittenWithoutTheirPoints() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.circle("pond", WORLD, 10, -30, 25));
    store.putArea(MapToolsTestUtils.marker("spot", WORLD, -3, 8));
    store.putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    StoredData stored = JsonLoader.load(file, StoredData.class);

    assertEquals(List.of(), stored.areas().get(0).points());
    assertEquals(List.of(), stored.areas().get(1).points());
    assertEquals(4, stored.areas().get(2).points().size());
  }

  @Test
  void everyDetailOfAnAreaSurvivesASaveAndLoad() throws IOException {
    MapArea area =
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)
            .withLabel("Home sweet home")
            .withDescription("Line one\nLine two with \"quotes\" and <tags>")
            .withOwners(List.of("Alex", "Sam Smith"))
            .withColor("#FF8800")
            .withOpacity(0.35)
            .withHidden(true);
    MapAreasStore store = store();

    store.putArea(area);

    MapArea loaded = reloaded().getArea("home");
    assertSameArea(area, loaded);
    assertEquals("#ff8800", loaded.color());
    assertTrue(loaded.isHidden());
    assertEquals(0.35, loaded.opacity());
    assertEquals(List.of("Alex", "Sam Smith"), loaded.owners());
  }

  @Test
  void anAreaThatIsShownAgainIsNotHiddenAfterReloading() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withHidden(true));
    assertTrue(reloaded().getArea("home").isHidden());

    store.putArea(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withHidden(false));

    assertFalse(reloaded().getArea("home").isHidden());
  }

  @Test
  void areasKeepTheirOrderAcrossASaveAndLoad() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    MapAreasStore loaded = reloaded();

    assertEquals(List.of("c", "a", "b"), loaded.getAreas().stream().map(MapArea::id).toList());
  }

  @Test
  void aRenamedAreaIsOnlyInTheFileUnderItsNewId() throws IOException {
    MapAreasStore store = store();
    MapArea area = MapToolsTestUtils.rect("old", WORLD, 0, 0, 9, 9);
    store.putArea(area);

    store.renameArea("old", area.withId("new"));

    MapAreasStore loaded = reloaded();
    assertNull(loaded.getArea("old"));
    assertSameArea(area.withId("new"), loaded.getArea("new"));
  }

  @Test
  void aRemovedAreaIsGoneFromTheFile() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    store.removeArea("a");

    MapAreasStore loaded = reloaded();
    assertNull(loaded.getArea("a"));
    assertNotNull(loaded.getArea("b"));
  }

  @Test
  void removingAnAreaThatIsNotThereDoesNotTouchTheFile() throws IOException {
    MapAreasStore store = store();

    assertNull(store.removeArea("missing"));

    assertFalse(file.exists());
  }

  @Test
  void loadReplacesWhatIsInMemoryWithWhatIsOnDisk() throws IOException {
    MapAreasStore writer = store();
    writer.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    MapAreasStore reader = reloaded();
    // The reader saves its own area, which the writer then overwrites with its own two.
    reader.putArea(MapToolsTestUtils.rect("local", WORLD, 0, 0, 1, 1));
    writer.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    reader.load();

    assertEquals(List.of("a", "b"), reader.getAreas().stream().map(MapArea::id).toList());
    reader.load();
    assertEquals(2, reader.getAreas().size());
  }

  @Test
  void savingWritesTheCurrentVersionOfTheFormat() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertEquals(1, JsonLoader.load(file, StoredData.class).version());
  }

  @Test
  void savingTwiceInARowWritesTheSameFile() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    String first = read(file);

    store.save();

    assertEquals(first, read(file));
  }

  @Test
  void savingCreatesTheFolderOfTheFile() throws IOException {
    file = new File(IceCreamFixture.dataFolder(), "deeper/still/map-areas.json");
    MapAreasStore store = store();

    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertTrue(file.isFile());
    assertNotNull(reloaded().getArea("a"));
  }

  @Test
  void savingLeavesNoTemporaryFileBehind() {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    assertFalse(temporary().exists());
  }

  @Test
  void loadingAnEmptyListOfAreasIsFine() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":[]}");

    assertEquals(List.of(), List.copyOf(reloaded().getAreas()));
  }

  @Test
  void loadingAFileWithoutAnAreasListIsFine() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":1}");

    assertEquals(List.of(), List.copyOf(reloaded().getAreas()));
  }

  @Test
  void loadingAFileWithANullAreasListIsFine() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":null}");

    assertEquals(List.of(), List.copyOf(reloaded().getAreas()));
  }

  @Test
  void loadingAFileWithoutAVersionReadsItAsTheOldestFormat() throws IOException {
    Files.writeString(
        file.toPath(),
        """
        {"areas":[{"id":"a","world":"minecraft:overworld","points":[{"x":0,"z":0},{"x":5,"z":0},{"x":5,"z":5}],"label":"A","color":"gold","shape":"polygon"}]}
        """);

    assertNotNull(reloaded().getArea("a"));
  }

  @Test
  void loadingAFileFromAnOlderPluginFillsInWhatIsMissing() throws IOException {
    // No owners, and a color that is not in the stored form.
    Files.writeString(
        file.toPath(),
        """
        {"version":1,"areas":[{"id":"a","world":"minecraft:overworld","points":[{"x":0,"z":0},{"x":5,"z":0},{"x":5,"z":5}],"label":"A","color":"FF8800","shape":"polygon"}]}
        """);

    MapArea loaded = reloaded().getArea("a");

    assertEquals(List.of(), loaded.owners());
    assertEquals("#ff8800", loaded.color());
    assertNull(loaded.description());
    assertNull(loaded.opacity());
    assertFalse(loaded.isHidden());
  }

  @Test
  void loadingAFileFromANewerPluginFailsAndNamesTheVersions() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":2,\"areas\":[]}");

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reloaded());

    assertEquals(
        "map-areas.json has version 2, but this plugin only supports up to version 1",
        failure.getMessage());
  }

  @Test
  void loadingAFileFromANewerPluginLeavesTheFileAlone() throws IOException {
    String newer = "{\"version\":7,\"areas\":[],\"extra\":true}";
    Files.writeString(file.toPath(), newer);

    assertThrows(IllegalStateException.class, () -> reloaded());

    assertEquals(newer, read(file));
  }

  @Test
  void loadingABrokenFileFails() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":[{\"id\":");

    assertThrows(JacksonException.class, () -> reloaded());
  }

  @Test
  void loadingAnEmptyFileFails() throws IOException {
    Files.writeString(file.toPath(), "");

    assertThrows(JacksonException.class, () -> reloaded());
  }

  @Test
  void loadingAFileThatIsNotJsonFails() throws IOException {
    Files.writeString(file.toPath(), "these are not the areas you are looking for");

    assertThrows(JacksonException.class, () -> reloaded());
  }

  @Test
  void loadingAFileWithTheWrongTypesFails() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":\"one\",\"areas\":\"none\"}");

    assertThrows(JacksonException.class, () -> reloaded());
  }

  @Test
  void aStoreThatFailedToLoadHoldsNoAreas() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    Files.writeString(file.toPath(), "not json");

    assertThrows(JacksonException.class, store::load);

    assertEquals(List.of(), List.copyOf(store.getAreas()));
  }

  @Test
  void theFirstSaveHasNothingToBackUp() {
    MapAreasStore store = store();

    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertFalse(backup().exists());
  }

  @Test
  void eachSaveKeepsThePreviousVersionAsTheBackup() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    String firstVersion = read(file);

    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));
    String secondVersion = read(file);

    assertEquals(firstVersion, read(backup()));
    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));
    assertEquals(secondVersion, read(backup()));
  }

  @Test
  void aFileEditedByHandDoesNotReplaceTheBackup() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));
    String goodBackup = read(backup());
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":[] this is a broken edit");
    touch(file);

    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));

    assertEquals(goodBackup, read(backup()));
    assertNotNull(reloaded().getArea("c"));
  }

  @Test
  void afterAHandEditedFileIsReplacedTheNextSaveBacksUpAgain() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));
    touch(file);
    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));
    String savedVersion = read(file);

    store.putArea(MapToolsTestUtils.rect("d", WORLD, 0, 0, 1, 1));

    assertEquals(savedVersion, read(backup()));
  }

  @Test
  void aFileEditedAfterItWasLoadedDoesNotReplaceTheBackup() throws IOException {
    MapAreasStore writer = store();
    writer.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    writer.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));
    String goodBackup = read(backup());
    MapAreasStore store = reloaded();
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":[]}");
    touch(file);

    store.putArea(MapToolsTestUtils.rect("c", WORLD, 0, 0, 1, 1));

    assertEquals(goodBackup, read(backup()));
  }

  @Test
  void aFileLeftAloneSinceItWasLoadedIsBackedUpBeforeTheNextSave() throws IOException {
    MapAreasStore writer = store();
    writer.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    String loadedVersion = read(file);
    MapAreasStore store = reloaded();

    store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

    assertEquals(loadedVersion, read(backup()));
  }

  @Test
  void aStoreThatNeverLoadedTheFileDoesNotBackItUp() throws IOException {
    Files.writeString(file.toPath(), "{\"version\":1,\"areas\":[]}");

    store().putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));

    assertFalse(backup().exists());
    assertNotNull(reloaded().getArea("a"));
  }

  @Test
  void aFailedSaveIsLoggedAndKeepsTheAreaInMemory() throws IOException {
    MapAreasStore store = store();
    store.putArea(MapToolsTestUtils.rect("a", WORLD, 0, 0, 1, 1));
    String before = read(file);
    // A folder where the temporary file belongs makes writing it fail.
    assertTrue(temporary().mkdir());
    Files.writeString(new File(temporary(), "blocker").toPath(), "x");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      store.putArea(MapToolsTestUtils.rect("b", WORLD, 0, 0, 1, 1));

      List<String> severe = log.messages(Level.SEVERE);
      assertEquals(1, severe.size());
      assertTrue(severe.getFirst().startsWith("Failed to save map-areas.json: "));
    }
    assertNotNull(store.getArea("b"));
    assertEquals(before, read(file));
  }
}
