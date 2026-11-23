package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.libs.JsonLoader;
import com.nearvanilla.iceCream.modules.mapTools.ClaimsExport.ExportedData;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The export is written off the main thread; these check what ends up on disk. */
class ClaimsExportWriterTest {

  @TempDir Path webDir;
  private FileConfiguration previousConfig;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    previousConfig = IceCream.config;
    previousLogger = IceCream.logger;
    IceCream.config = new YamlConfiguration();
    IceCream.logger = Logger.getLogger("ClaimsExportWriterTest");
  }

  @AfterEach
  void tearDown() {
    ClaimsExport.finishWrites();
    IceCream.config = previousConfig;
    IceCream.logger = previousLogger;
  }

  private static MapArea claim(String id, String label) {
    return new MapArea(
            id,
            "minecraft:overworld",
            List.of(),
            label,
            null,
            List.of(),
            "gold",
            null,
            null,
            null,
            null,
            null,
            null)
        .withGeometry(
            new AreaGeometry(
                "minecraft:overworld",
                MapToolsUtils.rectangle(0, 0, 9, 9),
                MapArea.RECT,
                null,
                null));
  }

  private ExportedData written() throws IOException {
    return JsonLoader.load(webDir.resolve(ClaimsExport.PATH).toFile(), ExportedData.class);
  }

  @Test
  void writesTheAreasByShutdown() throws IOException {
    ClaimsExport.writeLater(
        webDir, List.of(claim("town", "Spawn Town"), claim("farm", "Farm")), List.of());
    ClaimsExport.finishWrites();

    ExportedData data = written();
    assertEquals(ClaimsExport.VERSION, data.version());
    assertEquals(List.of("town", "farm"), List.copyOf(data.areas().keySet()));
    assertEquals("Spawn Town", data.areas().get("town").label());
  }

  @Test
  void writesTheIconNames() throws IOException {
    ClaimsExport.writeLater(webDir, List.of(), List.of("pin", "shop"));
    ClaimsExport.finishWrites();

    assertEquals(List.of("pin", "shop"), written().icons());
  }

  /** A burst of changes ends with the newest state on disk, whichever writes were skipped. */
  @Test
  void theNewestStateWins() throws IOException {
    for (int i = 0; i < 200; i++) {
      ClaimsExport.writeLater(webDir, List.of(claim("town", "Version " + i)), List.of());
    }
    ClaimsExport.finishWrites();

    assertEquals("Version 199", written().areas().get("town").label());
  }

  /** The data is taken when the write is asked for, not when it happens. */
  @Test
  void laterChangesDoNotLeakIntoAQueuedWrite() throws IOException {
    List<MapArea> areas = new java.util.ArrayList<>(List.of(claim("town", "Spawn Town")));
    ClaimsExport.writeLater(webDir, areas, List.of());
    areas.add(claim("farm", "Farm"));
    ClaimsExport.finishWrites();

    assertEquals(List.of("town"), List.copyOf(written().areas().keySet()));
  }

  @Test
  void writesAgainAfterAShutdown() throws IOException {
    ClaimsExport.writeLater(webDir, List.of(claim("town", "Before")), List.of());
    ClaimsExport.finishWrites();
    ClaimsExport.writeLater(webDir, List.of(claim("town", "After")), List.of());
    ClaimsExport.finishWrites();

    assertEquals("After", written().areas().get("town").label());
  }

  @Test
  void leavesNoTemporaryFileBehind() throws IOException {
    ClaimsExport.writeLater(webDir, List.of(claim("town", "Spawn Town")), List.of());
    ClaimsExport.finishWrites();

    try (var files = Files.list(webDir.resolve(ClaimsExport.PATH).getParent())) {
      assertEquals(List.of("map-areas.json"), files.map(p -> p.getFileName().toString()).toList());
    }
  }

  /** A failed write is logged, and does not stop later writes. */
  @Test
  void aFailedWriteDoesNotStopTheNextOne() throws IOException {
    Path notAFolder = webDir.resolve("blocked");
    Files.writeString(notAFolder, "a file where the folder should be");

    assertDoesNotThrow(
        () -> {
          ClaimsExport.writeLater(notAFolder, List.of(claim("town", "Lost")), List.of());
          ClaimsExport.finishWrites();
        });
    assertFalse(Files.isDirectory(notAFolder));

    ClaimsExport.writeLater(webDir, List.of(claim("town", "Saved")), List.of());
    ClaimsExport.finishWrites();
    assertTrue(Files.exists(webDir.resolve(ClaimsExport.PATH)));
    assertEquals("Saved", written().areas().get("town").label());
  }
}
