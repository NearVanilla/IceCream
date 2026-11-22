package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The popup on the public web map: typed text must never become HTML. */
class MapPopupTest {

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

  private static MapArea area(
      String label, List<String> owners, String description, boolean allowNearby) {
    return new MapArea(
        "town",
        "minecraft:overworld",
        List.of(),
        label,
        description,
        owners,
        "gold",
        MapArea.POLYGON,
        null,
        null,
        null,
        null,
        allowNearby ? true : null);
  }

  @Test
  void escapesEveryHtmlCharacter() {
    assertEquals(
        "&lt;b&gt;Tom &amp; Jerry&#39;s &quot;Base&quot;&lt;/b&gt;",
        MapToolsUtils.escapeHtml("<b>Tom & Jerry's \"Base\"</b>"));
  }

  @Test
  void plainPopupHasLabelOnly() {
    assertEquals(
        "<b>Spawn Town</b>", MapToolsUtils.tooltip(area("Spawn Town", List.of(), null, false)));
  }

  @Test
  void scriptsInLabelsOwnersAndDescriptionsAreEscaped() {
    String popup =
        MapToolsUtils.tooltip(
            area(
                "<script>alert(1)</script>",
                List.of("<img src=x onerror=alert(2)>"),
                "<iframe src=\"https://example.com\"></iframe>",
                false));

    assertFalse(popup.contains("<script"));
    assertFalse(popup.contains("<img"));
    assertFalse(popup.contains("<iframe"));
    assertEquals(
        "<b>&lt;script&gt;alert(1)&lt;/script&gt;</b>"
            + "<br>Owner(s): &lt;img src=x onerror=alert(2)&gt;"
            + "<br>&lt;iframe src=&quot;https://example.com&quot;&gt;&lt;/iframe&gt;",
        popup);
  }

  @Test
  void multilineDescriptionsBreakLinesAfterEscaping() {
    String popup =
        MapToolsUtils.tooltip(
            area("Farm", List.of("Dynant", "Alex"), "Wheat\n<b>and</b>\r\ncarrots", false));

    assertEquals(
        "<b>Farm</b><br>Owner(s): Dynant, Alex<br>Wheat<br>&lt;b&gt;and&lt;/b&gt;<br>carrots",
        popup);
  }

  @Test
  void saysWhenOthersMayClaimNearby() {
    assertEquals(
        "<b>Farm</b><br>Others may claim within 300 blocks",
        MapToolsUtils.tooltip(area("Farm", List.of(), " ", true)));
  }
}
