package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.ClaimsExport.ExportedArea;
import com.nearvanilla.iceCream.modules.mapTools.ClaimsExport.ExportedData;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ClaimsExportTest {

  private static final String WORLD = "minecraft:overworld";

  private static MapArea area(
      String id, String shape, List<MapPoint> points, MapPoint center, Double radius, String icon) {
    return new MapArea(
        id,
        WORLD,
        points,
        "Label " + id,
        "About " + id,
        List.of("Dynant"),
        "gold",
        shape,
        center,
        radius,
        null,
        icon,
        null);
  }

  @Test
  void rectangleKeepsItsPoints() {
    List<MapPoint> points =
        List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10), new MapPoint(0, 10));
    ExportedArea exported =
        ClaimsExport.of(area("town", MapArea.RECT, points, null, null, null), "#ffaa00", 0.2);

    assertEquals("town", exported.id());
    assertEquals("claim", exported.kind());
    assertEquals(MapArea.RECT, exported.shape());
    assertEquals(points, exported.points());
    assertEquals("#ffaa00", exported.color());
    assertEquals(0.2, exported.fillOpacity());
    assertEquals(List.of("Dynant"), exported.owners());
    assertNull(exported.center());
    assertNull(exported.radius());
    assertNull(exported.icon());
    assertFalse(exported.allowNearby());
  }

  @Test
  void claimSaysWhetherOthersMayClaimNearby() {
    MapArea area =
        area("town", MapArea.RECT, List.of(new MapPoint(0, 0)), null, null, null)
            .withAllowNearby(true);
    assertTrue(ClaimsExport.of(area, "#ffaa00", 0.2).allowNearby());
  }

  @Test
  void circleHasCenterAndRadiusOnly() {
    MapPoint center = new MapPoint(5.5, -3.5);
    ExportedArea exported =
        ClaimsExport.of(
            area("ring", MapArea.CIRCLE, List.of(new MapPoint(1, 1)), center, 20.0, null),
            "#ffaa00",
            0.2);

    assertEquals("claim", exported.kind());
    assertEquals(center, exported.center());
    assertEquals(20.0, exported.radius());
    assertNull(exported.points());
  }

  @Test
  void markerHasSpotAndIcon() {
    MapPoint spot = new MapPoint(100, 200);
    ExportedArea exported =
        ClaimsExport.of(
            area("shop", MapArea.MARKER, List.of(spot), spot, null, "chest"), "#ffaa00", 1);

    assertEquals("marker", exported.kind());
    assertEquals(spot, exported.center());
    assertEquals("chest", exported.icon());
    assertNull(exported.radius());
    assertNull(exported.points());
  }

  @Test
  void writesAreasKeyedById(@TempDir Path webDir) throws Exception {
    MapPoint spot = new MapPoint(100, 200);
    ExportedArea marker =
        ClaimsExport.of(
            area("shop", MapArea.MARKER, List.of(spot), spot, null, "chest"), "#ffaa00", 1);
    Path file = webDir.resolve(ClaimsExport.PATH);

    ClaimsExport.write(
        file,
        new ExportedData(
            ClaimsExport.VERSION, 42, List.of("chest", "pin"), Map.of("shop", marker)));

    JsonNode json = new ObjectMapper().readTree(file.toFile());
    assertEquals(ClaimsExport.VERSION, json.get("version").asInt());
    assertEquals(42, json.get("updated").asLong());
    assertEquals("chest", json.get("icons").get(0).asString());
    assertEquals("pin", json.get("icons").get(1).asString());
    JsonNode shop = json.get("areas").get("shop");
    assertEquals("shop", shop.get("id").asString());
    assertEquals("marker", shop.get("kind").asString());
    assertEquals(200, shop.get("center").get("z").asInt());
    assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
    assertTrue(Files.exists(file));
  }
}
