package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.AreaForms.Values;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The manage form only saves what this player changed, so two people do not undo each other. */
class AreaFormsTest {

  private FileConfiguration previousConfig;

  @BeforeEach
  void setUp() {
    previousConfig = IceCream.config;
    IceCream.config = new YamlConfiguration();
  }

  @AfterEach
  void tearDown() {
    IceCream.config = previousConfig;
  }

  private static MapArea town() {
    return new MapArea(
            "town",
            "minecraft:overworld",
            List.of(),
            "Spawn Town",
            "Old description",
            List.of("Dynant"),
            "gold",
            null,
            null,
            null,
            0.2,
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

  /** The values with only the label changed, as if typed into the form. */
  private static Values withLabel(Values v, String label) {
    return new Values(
        v.marker(),
        v.id(),
        label,
        v.color(),
        v.owner(),
        v.description(),
        v.opacity(),
        v.icon(),
        v.typed(),
        v.coordinates(),
        v.allowNearby());
  }

  @Test
  void nothingChangedKeepsTheArea() {
    MapArea area = town();
    Values shown = Values.of(area);
    assertSame(area, AreaForms.changed(area, shown, shown, null));
  }

  @Test
  void onlyTheChangedFieldIsApplied() {
    MapArea area = town();
    Values shown = Values.of(area);

    MapArea result = AreaForms.changed(area, withLabel(shown, "New Town"), shown, null);

    assertEquals("New Town", result.label());
    assertEquals(area.withLabel("New Town"), result);
  }

  /** Someone else changed the color while this player had the form open: both changes stay. */
  @Test
  void othersChangesMadeMeanwhileAreKept() {
    MapArea opened = town();
    Values shown = Values.of(opened);
    MapArea current = opened.withColor("aqua").withOwners(List.of("Alex"));

    MapArea result = AreaForms.changed(current, withLabel(shown, "New Town"), shown, null);

    assertEquals("New Town", result.label());
    assertEquals("aqua", result.color());
    assertEquals(List.of("Alex"), result.owners());
  }

  @Test
  void aNewShapeReplacesTheOldOne() {
    MapArea area = town();
    Values shown = Values.of(area);
    AreaGeometry bigger =
        new AreaGeometry(
            "minecraft:overworld", MapToolsUtils.rectangle(0, 0, 19, 19), MapArea.RECT, null, null);

    MapArea result = AreaForms.changed(area, shown, shown, bigger);

    assertEquals(bigger.points(), result.points());
    assertEquals("Spawn Town", result.label());
  }

  @Test
  void ticksNearbyAndChangesOpacity() {
    MapArea area = town();
    Values shown = Values.of(area);
    Values entered =
        new Values(
            shown.marker(),
            shown.id(),
            shown.label(),
            shown.color(),
            shown.owner(),
            shown.description(),
            shown.opacity() + 10,
            shown.icon(),
            shown.typed(),
            shown.coordinates(),
            true);

    MapArea result = AreaForms.changed(area, entered, shown, null);

    assertTrue(result.allowsNearby());
    assertEquals(0.3, result.opacity(), 1e-9);
  }
}
