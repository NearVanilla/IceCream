package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The rules for new claims: valid details, typed points, and which claims count as nearby. */
class MapToolsRulesTest {

  private static final String WORLD = "minecraft:overworld";

  @TempDir Path folder;
  private MapAreasStore previousStore;
  private FileConfiguration previousConfig;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    previousStore = MapToolsModule.store;
    previousConfig = IceCream.config;
    previousLogger = IceCream.logger;
    MapToolsModule.store = new MapAreasStore(folder.resolve("map-areas.json").toFile());
    IceCream.config = new YamlConfiguration();
    IceCream.logger = Logger.getLogger("MapToolsRulesTest");
  }

  @AfterEach
  void tearDown() {
    MapToolsModule.store = previousStore;
    IceCream.config = previousConfig;
    IceCream.logger = previousLogger;
  }

  private static MapArea claim(String id, int x1, int z1, int x2, int z2, boolean allowNearby) {
    return new MapArea(
            id, WORLD, List.of(), id, null, List.of(), "gold", null, null, null, null, null, null)
        .withGeometry(rect(x1, z1, x2, z2))
        .withAllowNearby(allowNearby);
  }

  private static AreaGeometry rect(int x1, int z1, int x2, int z2) {
    return new AreaGeometry(
        WORLD, MapToolsUtils.rectangle(x1, z1, x2, z2), MapArea.RECT, null, null);
  }

  private static List<String> nearbyIds(AreaGeometry shape, String editingId) {
    return MapToolsUtils.nearbyClaims(shape, editingId).stream()
        .map(near -> near.claim().id())
        .toList();
  }

  // --- validate ---

  @Test
  void validDetailsPass() {
    assertNull(MapToolsUtils.validate("spawn_town-2", "gold", "Spawn Town", true));
    assertNull(MapToolsUtils.validate("spawn", "ff8800", "Spawn", true));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "Spawn", "spawn town", "spawn.town", "abcdefghijklmnopqrstuvwxyz1234567"})
  void badIdsAreRefused(String id) {
    assertNotNull(MapToolsUtils.validate(id, "gold", "Spawn", true));
  }

  @Test
  void takenIdsAreRefusedOnlyForNewAreas() {
    MapToolsModule.store.putArea(claim("spawn", 0, 0, 9, 9, false));
    assertNotNull(MapToolsUtils.validate("spawn", "gold", "Spawn", true));
    assertNull(MapToolsUtils.validate("spawn", "gold", "Spawn", false));
  }

  @Test
  void emptyOrLongLabelsAreRefused() {
    assertNotNull(MapToolsUtils.validate("spawn", "gold", " ", true));
    assertNotNull(MapToolsUtils.validate("spawn", "gold", null, true));
    String tooLong = "a".repeat(MapToolsUtils.LABEL_MAX_LENGTH + 1);
    assertNotNull(MapToolsUtils.validate("spawn", "gold", tooLong, true));
  }

  @Test
  void badColorsAreRefused() {
    assertNotNull(MapToolsUtils.validate("spawn", "not_a_color", "Spawn", true));
    assertNotNull(MapToolsUtils.validate("spawn", "ff88", "Spawn", true));
  }

  // --- parsePoints ---

  @Test
  void parsesTypedPointsAsBlockCenters() {
    AreaGeometry shape = MapToolsUtils.parsePoints(WORLD, "0 0; 10 0 ;10   10");
    assertNotNull(shape);
    assertEquals(MapArea.POLYGON, shape.shape());
    assertEquals(MapToolsUtils.blockCenter(10, 10), shape.points().get(2));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0 0; 10 0",
        "0 0; 10 0; 20 0",
        "0 0; 10 x; 10 10",
        "0 0; 10; 10 10",
        "0 0 0; 10 0; 10 10",
        "0.5 0; 10 0; 10 10"
      })
  void badPointsAreRefused(String points) {
    assertNull(MapToolsUtils.parsePoints(WORLD, points));
  }

  // --- nearby claims ---

  @Test
  void claimsWithinTheDistanceAreListed() {
    MapToolsModule.store.putArea(claim("near", 100, 0, 109, 9, false));
    MapToolsModule.store.putArea(claim("far", 1000, 0, 1009, 9, false));

    assertEquals(List.of("near"), nearbyIds(rect(0, 0, 9, 9), null));
  }

  @Test
  void touchingAClaimThatAllowsNearbyIsFine() {
    MapToolsModule.store.putArea(claim("open", 10, 0, 19, 9, true));

    assertTrue(nearbyIds(rect(0, 0, 9, 9), null).isEmpty());
  }

  @Test
  void overlappingAClaimThatAllowsNearbyIsListed() {
    MapToolsModule.store.putArea(claim("open", 5, 0, 19, 9, true));

    List<MapToolsUtils.NearbyClaim> nearby = MapToolsUtils.nearbyClaims(rect(0, 0, 9, 9), null);
    assertEquals(1, nearby.size());
    assertTrue(nearby.getFirst().overlaps());
  }

  @Test
  void touchingAClaimThatDoesNotAllowNearbyIsListedAsTouching() {
    MapToolsModule.store.putArea(claim("closed", 10, 0, 19, 9, false));

    List<MapToolsUtils.NearbyClaim> nearby = MapToolsUtils.nearbyClaims(rect(0, 0, 9, 9), null);
    assertEquals(1, nearby.size());
    assertEquals(0, nearby.getFirst().distance());
    assertFalse(nearby.getFirst().overlaps());
  }

  @Test
  void theClaimBeingEditedAndOtherWorldsAreLeftOut() {
    MapToolsModule.store.putArea(claim("self", 0, 0, 9, 9, false));
    MapArea nether =
        claim("nether", 0, 0, 9, 9, false)
            .withGeometry(
                new AreaGeometry(
                    "minecraft:the_nether",
                    MapToolsUtils.rectangle(0, 0, 9, 9),
                    MapArea.RECT,
                    null,
                    null));
    MapToolsModule.store.putArea(nether);

    assertTrue(nearbyIds(rect(0, 0, 9, 9), "self").isEmpty());
  }

  @Test
  void closestComeFirstAndTheDistanceIsConfigurable() {
    MapToolsModule.store.putArea(claim("farther", 200, 0, 209, 9, false));
    MapToolsModule.store.putArea(claim("closer", 50, 0, 59, 9, false));

    assertEquals(List.of("closer", "farther"), nearbyIds(rect(0, 0, 9, 9), null));
    IceCream.config.set("modules.maptools.nearby-claim-distance", 100);
    assertEquals(List.of("closer"), nearbyIds(rect(0, 0, 9, 9), null));
  }
}
