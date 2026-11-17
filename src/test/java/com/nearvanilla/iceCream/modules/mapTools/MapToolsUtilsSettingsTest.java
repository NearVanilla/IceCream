package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The colors, opacity, distance, icon and validation settings of the map tools logic. */
class MapToolsUtilsSettingsTest {
  private static final double DELTA = 1e-9;

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MapToolsTestUtils.reset();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String yaml) {
    IceCreamFixture.install(server, yaml);
    MapToolsTestUtils.installStore();
  }

  private static MapArea withColor(String color) {
    return MapToolsTestUtils.rect("a", MapToolsTestUtils.OVERWORLD, 0, 0, 9, 9).withColor(color);
  }

  private static MapArea withOpacity(Double opacity) {
    return MapToolsTestUtils.rect("a", MapToolsTestUtils.OVERWORLD, 0, 0, 9, 9)
        .withOpacity(opacity);
  }

  @ParameterizedTest
  @CsvSource({
    "gold, gold",
    "GOLD, gold",
    "'  dark_red  ', dark_red",
    "light_purple, light_purple",
    "ff8800, #ff8800",
    "#ff8800, #ff8800",
    "#FF8800, #ff8800",
    "FF8800, #ff8800",
    "'  #00FfAa ', #00ffaa",
    "000000, #000000"
  })
  void normalizeColorGivesTheStoredForm(String input, String expected) {
    assertEquals(expected, MapToolsUtils.normalizeColor(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "#",
        "banana",
        "ff88",
        "fff",
        "ff88000",
        "#ff88000",
        "gg0000",
        "##ff8800",
        "#ff 880",
        "gold!",
        "dark red",
        "0xff8800"
      })
  void normalizeColorRejectsAnythingThatIsNotAColor(String input) {
    assertNull(MapToolsUtils.normalizeColor(input));
  }

  @Test
  void parseColorReadsHexCodes() {
    assertEquals(TextColor.color(0xff8800), MapToolsUtils.parseColor("ff8800"));
    assertEquals(TextColor.color(0x00ffaa), MapToolsUtils.parseColor("#00FFAA"));
    assertEquals(TextColor.color(0x000000), MapToolsUtils.parseColor("000000"));
  }

  @Test
  void parseColorReadsColorNames() {
    assertSame(NamedTextColor.GOLD, MapToolsUtils.parseColor("gold"));
    assertSame(NamedTextColor.DARK_RED, MapToolsUtils.parseColor(" Dark_Red "));
    assertSame(NamedTextColor.LIGHT_PURPLE, MapToolsUtils.parseColor("light_purple"));
  }

  @Test
  void parseColorReturnsNullForAnythingElse() {
    assertNull(MapToolsUtils.parseColor(null));
    assertNull(MapToolsUtils.parseColor(""));
    assertNull(MapToolsUtils.parseColor("banana"));
    assertNull(MapToolsUtils.parseColor("#12345"));
  }

  @Test
  void defaultColorIsGoldWithoutAConfiguredColor() {
    config("");

    assertEquals("gold", MapToolsUtils.defaultColor());
  }

  @Test
  void defaultColorIsTheConfiguredColor() {
    config("modules:\n  maptools:\n    claim-color: red\n");

    assertEquals("red", MapToolsUtils.defaultColor());
  }

  @Test
  void defaultColorCanBeAHexCode() {
    config("modules:\n  maptools:\n    claim-color: '#00ff00'\n");

    assertEquals("#00ff00", MapToolsUtils.defaultColor());
  }

  @Test
  void defaultColorFallsBackToGoldWhenTheConfiguredColorIsInvalid() {
    config("modules:\n  maptools:\n    claim-color: banana\n");

    assertEquals("gold", MapToolsUtils.defaultColor());
  }

  @Test
  void colorOrDefaultParsesAValidColor() {
    config("modules:\n  maptools:\n    claim-color: red\n");

    assertSame(NamedTextColor.AQUA, MapToolsUtils.colorOrDefault("aqua"));
    assertEquals(TextColor.color(0x123456), MapToolsUtils.colorOrDefault("123456"));
  }

  @Test
  void colorOrDefaultFallsBackToTheConfiguredColor() {
    config("modules:\n  maptools:\n    claim-color: red\n");

    assertSame(NamedTextColor.RED, MapToolsUtils.colorOrDefault("banana"));
    assertSame(NamedTextColor.RED, MapToolsUtils.colorOrDefault(null));
  }

  @Test
  void colorOrDefaultFallsBackToGoldWhenTheConfiguredColorIsInvalidToo() {
    config("modules:\n  maptools:\n    claim-color: banana\n");

    assertSame(NamedTextColor.GOLD, MapToolsUtils.colorOrDefault("also banana"));
  }

  @Test
  void areaColorIsTheColorOfTheArea() {
    config("");

    assertSame(NamedTextColor.GREEN, MapToolsUtils.areaColor(withColor("green")));
    assertEquals(TextColor.color(0x445566), MapToolsUtils.areaColor(withColor("#445566")));
  }

  @Test
  void areaColorOfAnAreaWithAnUnparseableColorIsTheDefaultColor() {
    config("modules:\n  maptools:\n    claim-color: blue\n");

    // The record keeps a color that cannot be parsed as it was written.
    MapArea broken = withColor("banana");
    assertEquals("banana", broken.color());
    assertSame(NamedTextColor.BLUE, MapToolsUtils.areaColor(broken));
    assertSame(NamedTextColor.BLUE, MapToolsUtils.areaColor(withColor(null)));
  }

  @Test
  void defaultFillOpacityIsAFifthWithoutAConfiguredValue() {
    config("");

    assertEquals(0.2, MapToolsUtils.defaultFillOpacity(), DELTA);
  }

  @Test
  void defaultFillOpacityIsTheConfiguredValue() {
    config("modules:\n  maptools:\n    claim-fill-opacity: 0.65\n");

    assertEquals(0.65, MapToolsUtils.defaultFillOpacity(), DELTA);
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "1, 1", "5, 1", "-0.5, 0", "1.0001, 1", "0.0001, 0.0001"})
  void defaultFillOpacityStaysBetweenZeroAndOne(String configured, double expected) {
    config("modules:\n  maptools:\n    claim-fill-opacity: " + configured + "\n");

    assertEquals(expected, MapToolsUtils.defaultFillOpacity(), DELTA);
  }

  @Test
  void fillOpacityIsTheDefaultWhenTheAreaHasNone() {
    config("modules:\n  maptools:\n    claim-fill-opacity: 0.4\n");

    assertEquals(0.4, MapToolsUtils.fillOpacity(withOpacity(null)), DELTA);
  }

  @Test
  void fillOpacityIsTheOpacityOfTheArea() {
    config("modules:\n  maptools:\n    claim-fill-opacity: 0.4\n");

    assertEquals(0.75, MapToolsUtils.fillOpacity(withOpacity(0.75)), DELTA);
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "1, 1", "1.5, 1", "-0.3, 0", "0.999, 0.999"})
  void fillOpacityOfAnAreaStaysBetweenZeroAndOne(double opacity, double expected) {
    config("");

    assertEquals(expected, MapToolsUtils.fillOpacity(withOpacity(opacity)), DELTA);
  }

  @Test
  void nearbyClaimDistanceIsThreeHundredBlocksWithoutAConfiguredValue() {
    config("");

    assertEquals(300, MapToolsUtils.nearbyClaimDistance());
  }

  @ParameterizedTest
  @CsvSource({"50, 50", "0, 0", "-1, 0", "-500, 0", "100000, 100000"})
  void nearbyClaimDistanceIsTheConfiguredValueButNeverNegative(int configured, int expected) {
    config("modules:\n  maptools:\n    nearby-claim-distance: " + configured + "\n");

    assertEquals(expected, MapToolsUtils.nearbyClaimDistance());
  }

  @Test
  void iconItemIsTheItemWithTheNameOfTheIcon() {
    config("");

    assertEquals(Material.CHEST, MapToolsUtils.iconItem("chest"));
    assertEquals(Material.LODESTONE, MapToolsUtils.iconItem("lodestone"));
  }

  @Test
  void iconItemIsPaperForAnIconThatIsNoItem() {
    config("");

    assertEquals(Material.PAPER, MapToolsUtils.iconItem("pin"));
    assertEquals(Material.PAPER, MapToolsUtils.iconItem("water"));
    assertEquals(Material.PAPER, MapToolsUtils.iconItem("air"));
  }

  @Test
  void iconItemIsTheItemConfiguredForTheIcon() {
    config(
        "modules:\n  maptools:\n    marker-icon-items:\n      pin: lodestone\n      chest: barrel\n");

    assertEquals(Material.LODESTONE, MapToolsUtils.iconItem("pin"));
    // The configured item wins over the item with the name of the icon.
    assertEquals(Material.BARREL, MapToolsUtils.iconItem("chest"));
  }

  @Test
  void iconItemIsPaperWhenTheConfiguredItemIsNotValid() {
    config(
        "modules:\n"
            + "  maptools:\n"
            + "    marker-icon-items:\n"
            + "      chest: not_an_item\n"
            + "      pin: water\n"
            + "      flag: air\n");

    // The name of the icon is not used when something is configured for it.
    assertEquals(Material.PAPER, MapToolsUtils.iconItem("chest"));
    assertEquals(Material.PAPER, MapToolsUtils.iconItem("pin"));
    assertEquals(Material.PAPER, MapToolsUtils.iconItem("flag"));
  }

  @Test
  void validateAcceptsAValidNewArea() {
    config("");

    assertNull(MapToolsUtils.validate("home-1", "gold", "Home", true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "abcdefghijklmnopqrstuvwxyz012345", "under_score-and-dash", "0-9"})
  void validateAcceptsIdsOfOneToThirtyTwoValidCharacters(String id) {
    config("");

    assertNull(MapToolsUtils.validate(id, "gold", "Label", true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "Home",
        "my home",
        "home!",
        "home.",
        "ho/me",
        "abcdefghijklmnopqrstuvwxyz0123456",
        "häuschen",
        " home"
      })
  void validateRejectsIdsThatAreEmptyTooLongOrUseOtherCharacters(String id) {
    config("");

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        MapToolsUtils.validate(id, "gold", "Label", true));
  }

  @Test
  void validateRejectsAnIdThatIsTakenForANewArea() {
    config("");
    MapToolsModule.store.putArea(
        MapToolsTestUtils.rect("taken", "minecraft:overworld", 0, 0, 1, 1));

    assertEquals(
        "A claim with id taken already exists.",
        MapToolsUtils.validate("taken", "gold", "Label", true));
  }

  @Test
  void validateAcceptsAnIdThatIsTakenWhenTheAreaIsNotNew() {
    config("");
    MapToolsModule.store.putArea(
        MapToolsTestUtils.rect("taken", "minecraft:overworld", 0, 0, 1, 1));

    assertNull(MapToolsUtils.validate("taken", "gold", "Label", false));
  }

  @Test
  void validateStillChecksTheIdFormatOfAnAreaThatIsNotNew() {
    config("");

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        MapToolsUtils.validate("Bad Id", "gold", "Label", false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n"})
  void validateRejectsAnEmptyLabel(String label) {
    config("");

    assertEquals("The label cannot be empty.", MapToolsUtils.validate("id", "gold", label, true));
  }

  @Test
  void validateAllowsALabelOfSixtyFourCharacters() {
    config("");

    assertNull(MapToolsUtils.validate("id", "gold", "x".repeat(64), true));
  }

  @Test
  void validateRejectsALabelOfSixtyFiveCharacters() {
    config("");

    assertEquals(
        "Labels can be up to 64 characters.",
        MapToolsUtils.validate("id", "gold", "x".repeat(65), true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"banana", "#12345", "gold2"})
  void validateRejectsAColorThatCannotBeParsed(String color) {
    config("");

    assertEquals(
        "Use a color name such as gold, or a hex code such as ff8800.",
        MapToolsUtils.validate("id", color, "Label", true));
  }

  @Test
  void validateAcceptsAHexColorWithoutTheHash() {
    config("");

    assertNull(MapToolsUtils.validate("id", "ff8800", "Label", true));
  }

  @Test
  void validateReportsTheIdBeforeTheOtherProblems() {
    config("");
    MapToolsModule.store.putArea(
        MapToolsTestUtils.rect("taken", "minecraft:overworld", 0, 0, 1, 1));

    assertEquals(
        "Ids may only use a-z, 0-9, _ and -, up to 32 characters.",
        MapToolsUtils.validate("BAD", "banana", "", true));
  }

  @Test
  void validateReportsATakenIdBeforeTheLabelAndColor() {
    config("");
    MapToolsModule.store.putArea(
        MapToolsTestUtils.rect("taken", "minecraft:overworld", 0, 0, 1, 1));

    assertEquals(
        "A claim with id taken already exists.",
        MapToolsUtils.validate("taken", "banana", "", true));
  }

  @Test
  void validateReportsTheLabelBeforeTheColor() {
    config("");

    assertEquals("The label cannot be empty.", MapToolsUtils.validate("id", "banana", " ", true));
    assertEquals(
        "Labels can be up to 64 characters.",
        MapToolsUtils.validate("id", "banana", "x".repeat(65), true));
  }

  @Test
  void deletedMessageNamesTheAreaAndHowToCreateItAgain() {
    config("");
    MapArea area = MapToolsTestUtils.rect("home", "minecraft:overworld", 0, 0, 9, 9);

    assertEquals(
        "Deleted home (Label home). rect 0 0 9 9 in minecraft:overworld",
        MessageTestUtils.plainText(MapToolsUtils.deletedMessage(area)));
  }

  @Test
  void deletedMessageShowsTextThatLooksLikeMarkupAsItIs() {
    config("");
    MapArea area =
        MapToolsTestUtils.circle("ring", "minecraft:the_nether", 5, -3, 10)
            .withLabel("<red>Hot</red> <bold>spot");

    assertEquals(
        "Deleted ring (<red>Hot</red> <bold>spot). circle 5 -3 10 in minecraft:the_nether",
        MessageTestUtils.plainText(MapToolsUtils.deletedMessage(area)));
  }

  @Test
  void deletedMessageOfAMarkerGivesItsBlock() {
    config("");
    MapArea area = MapToolsTestUtils.marker("spot", "minecraft:overworld", -7, 12);

    assertEquals(
        "Deleted spot (Label spot). marker -7 12 in minecraft:overworld",
        MessageTestUtils.plainText(MapToolsUtils.deletedMessage(area)));
  }
}
