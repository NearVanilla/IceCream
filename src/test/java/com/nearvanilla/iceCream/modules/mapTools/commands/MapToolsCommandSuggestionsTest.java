package com.nearvanilla.iceCream.modules.mapTools.commands;

import static com.nearvanilla.iceCream.testutil.CommandTestUtils.context;
import static com.nearvanilla.iceCream.testutil.CommandTestUtils.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.incendo.cloud.context.CommandContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;

/** What the commands suggest while a player types them. */
class MapToolsCommandSuggestionsTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private Scenario scenario;
  private DialogPlayer alex;
  private CommandSourceStack source;
  private final MapToolsCommand command = new MapToolsCommand();

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    source = stack(alex);
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    store(MapToolsTestUtils.polygon("shop", WORLD, 0, 0, 10, 0, 5, 10));
    store(MapToolsTestUtils.circle("pond", WORLD, 40, 40, 20));
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));
    store(MapToolsTestUtils.marker("flag", WORLD, 8, 8));
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private MapArea store(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private CommandContext<CommandSourceStack> typed(String input) {
    return context(source, input);
  }

  private static List<String> sorted(List<String> values) {
    return values.stream().sorted().toList();
  }

  // Ids

  @Test
  void idsAreOfTheKindTypedAfterTheAction() {
    assertEquals(
        List.of("home", "pond", "shop"),
        sorted(command.suggestIds(typed("maptools manage claim "), null)));
    assertEquals(
        List.of("flag", "spot"),
        sorted(command.suggestIds(typed("maptools manage marker "), null)));
  }

  @Test
  void theKindIsReadWithoutRegardToCase() {
    assertEquals(
        List.of("flag", "spot"), sorted(command.suggestIds(typed("maptools tp MARKER s"), null)));
  }

  @Test
  void idsOfEveryKindAreSuggestedWhenNoKindWasTypedYet() {
    assertEquals(
        List.of("flag", "home", "pond", "shop", "spot"),
        sorted(command.suggestIds(typed("maptools manage "), null)));
    assertEquals(
        List.of("flag", "home", "pond", "shop", "spot"),
        sorted(command.suggestIds(typed("maptools manage"), null)));
  }

  @Test
  void idsOfEveryKindAreSuggestedWhenTheKindIsNotAKind() {
    assertEquals(5, command.suggestIds(typed("maptools manage nothing "), null).size());
  }

  @Test
  void extraSpacesBetweenTheWordsDoNotConfuseTheKind() {
    assertEquals(
        List.of("flag", "spot"),
        sorted(command.suggestIds(typed("  maptools   delete   marker  "), null)));
  }

  @Test
  void noIdsAreSuggestedWhenThereAreNoAreas() {
    scenario.configure("");

    assertTrue(command.suggestIds(typed("maptools manage claim "), null).isEmpty());
  }

  @Test
  void theSearchSuggestionsAreTheIdsOfTheKind() {
    assertEquals(
        List.of("home", "pond", "shop"), sorted(command.suggestClaimSearch(typed(""), null)));
    assertEquals(List.of("flag", "spot"), sorted(command.suggestMarkerSearch(typed(""), null)));
  }

  // Fields

  @Test
  void aClaimsFieldsIncludeOpacityAndPointsButNoIcon() {
    assertEquals(
        List.of("id", "label", "color", "owners", "description", "opacity", "points"),
        command.suggestFields(typed("maptools set claim home "), null));
  }

  @Test
  void aMarkersFieldsIncludeTheIconButNoOpacityOrPoints() {
    assertEquals(
        List.of("id", "label", "color", "owners", "description", "icon"),
        command.suggestFields(typed("maptools set marker spot "), null));
  }

  @Test
  void theClaimFieldsAreSuggestedWhenNoKindWasTypedYet() {
    assertEquals(
        List.of("id", "label", "color", "owners", "description", "opacity", "points"),
        command.suggestFields(typed("maptools set"), null));
  }

  // Colors

  @Test
  void colorsStartWithAnExampleHexCodeAndListEveryColorName() {
    List<String> colors = command.suggestColors(typed("maptools create claim rect"), null);

    assertEquals("ff8800", colors.getFirst());
    assertEquals(NamedTextColor.NAMES.keys().size() + 1, colors.size());
    assertTrue(colors.containsAll(NamedTextColor.NAMES.keys()));
  }

  // Coordinates

  @Test
  void blockXIsTheBlockThePlayerStandsOn() {
    when(source.getLocation()).thenReturn(new Location(scenario.overworld(), 12.9, 64, -3.2));

    assertEquals(List.of("12"), command.suggestBlockX(typed("maptools create claim rect "), null));
  }

  @Test
  void blockZIsTheBlockThePlayerStandsOnEvenInNegativeCoordinates() {
    when(source.getLocation()).thenReturn(new Location(scenario.overworld(), 12.9, 64, -3.2));

    assertEquals(
        List.of("-4"), command.suggestBlockZ(typed("maptools create claim rect 1 "), null));
  }

  @Test
  void blockXOfANegativeCoordinateRoundsDown() {
    when(source.getLocation()).thenReturn(new Location(scenario.overworld(), -0.5, 64, 0.5));

    assertEquals(List.of("-1"), command.suggestBlockX(typed("maptools create claim rect "), null));
    assertEquals(List.of("0"), command.suggestBlockZ(typed("maptools create claim rect "), null));
  }

  @Test
  void nothingIsSuggestedWhereNumbersAreNotHelpful() {
    assertTrue(
        command.suggestNothing(typed("maptools set claim home circle 1 1 "), null).isEmpty());
  }

  // The current value of a field

  @Test
  void theValueSuggestionsNeedTheKindIdAndFieldTyped() {
    assertTrue(command.suggestValues(typed("maptools set claim home"), null).isEmpty());
    assertTrue(command.suggestValues(typed("maptools set"), null).isEmpty());
    assertEquals(
        List.of("Label home"), command.suggestValues(typed("maptools set claim home label"), null));
  }

  @Test
  void anUnknownAreaHasNoValueSuggestions() {
    assertTrue(command.suggestValues(typed("maptools set claim nobody label "), null).isEmpty());
  }

  @Test
  void aLabelSuggestsItsCurrentValue() {
    assertEquals(
        List.of("Label home"),
        command.suggestValues(typed("maptools set claim home label "), null));
  }

  @Test
  void theFieldIsReadWithoutRegardToCase() {
    assertEquals(
        List.of("Label home"),
        command.suggestValues(typed("maptools set claim home LABEL "), null));
  }

  @Test
  void aColorSuggestsItsCurrentHexCodeFirstThenTheNames() {
    List<String> colors = command.suggestValues(typed("maptools set claim home color "), null);

    assertEquals("FFAA00", colors.getFirst());
    assertEquals(NamedTextColor.NAMES.keys().size() + 1, colors.size());
    assertTrue(colors.contains("gold"));
  }

  @Test
  void aColorThatCannotBeReadSuggestsTheDefaultColorsHexCode() {
    store(MapToolsTestUtils.rect("odd", WORLD, 0, 0, 1, 1).withColor("not a color"));

    List<String> colors = command.suggestValues(typed("maptools set claim odd color "), null);

    assertEquals("FFAA00", colors.getFirst());
  }

  @Test
  void ownersSuggestTheirCurrentValueJoinedWithCommas() {
    store(MapToolsTestUtils.rect("duo", WORLD, 0, 0, 1, 1).withOwners(List.of("Alex", "Sam")));

    assertEquals(
        List.of("Alex, Sam"), command.suggestValues(typed("maptools set claim duo owners "), null));
  }

  @Test
  void noOwnersSuggestNothing() {
    assertTrue(command.suggestValues(typed("maptools set claim home owners "), null).isEmpty());
  }

  @Test
  void aDescriptionSuggestsItsCurrentValueAndNoDescriptionSuggestsNothing() {
    assertTrue(
        command.suggestValues(typed("maptools set claim home description "), null).isEmpty());
    store(MapToolsTestUtils.rect("noted", WORLD, 0, 0, 1, 1).withDescription("A big place"));

    assertEquals(
        List.of("A big place"),
        command.suggestValues(typed("maptools set claim noted description "), null));
  }

  @Test
  void aPolygonsPointsAreSuggestedAsTypedCoordinates() {
    assertEquals(
        List.of("0 0; 10 0; 5 10"),
        command.suggestValues(typed("maptools set claim shop points "), null));
  }

  @Test
  void otherShapesSuggestNoPoints() {
    assertTrue(command.suggestValues(typed("maptools set claim home points "), null).isEmpty());
    assertTrue(command.suggestValues(typed("maptools set claim pond points "), null).isEmpty());
  }

  @Test
  void anIconSuggestsTheIconNames() throws IOException {
    File folder = new File(IceCreamFixture.dataFolder(), "map-icons");
    assertTrue(folder.mkdirs());
    for (String name : new String[] {"pin", "star"}) {
      ImageIO.write(
          new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
          "png",
          new File(folder, name + ".png"));
    }
    Squaremap api = mock(Squaremap.class);
    @SuppressWarnings("unchecked")
    Registry<BufferedImage> icons = mock(Registry.class);
    when(api.iconRegistry()).thenReturn(icons);
    when(api.getWorldIfEnabled(any(WorldIdentifier.class))).thenReturn(Optional.empty());
    MapToolsUtils.init(api);

    assertEquals(
        List.of("pin", "star"),
        command.suggestValues(typed("maptools set marker spot icon "), null));
  }

  @Test
  void fieldsWithoutAHelpfulCurrentValueSuggestNothing() {
    assertTrue(command.suggestValues(typed("maptools set claim home id "), null).isEmpty());
    assertTrue(command.suggestValues(typed("maptools set claim home opacity "), null).isEmpty());
    assertTrue(command.suggestValues(typed("maptools set claim home unknown "), null).isEmpty());
  }

  @Test
  void theValueIsLookedUpOnTheAreaNamedInTheRawInputNotOnTheKind() {
    assertEquals(
        List.of("Label spot"),
        command.suggestValues(typed("maptools set marker spot label "), null));
  }
}
