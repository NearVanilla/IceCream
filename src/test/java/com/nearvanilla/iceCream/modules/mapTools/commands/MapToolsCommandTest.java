package com.nearvanilla.iceCream.modules.mapTools.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand.Kind;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The command handlers, called directly as Cloud would after parsing. Claims are kept far apart, so
 * no "Claims nearby" dialog opens.
 */
class MapToolsCommandTest {

  @TempDir Path folder;
  private ServerMock server;
  private PlayerMock player;
  private final MapToolsCommand command = new MapToolsCommand();
  private MapAreasStore previousStore;
  private FileConfiguration previousConfig;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    player = server.addPlayer("Staff");
    previousStore = MapToolsModule.store;
    previousConfig = IceCream.config;
    previousLogger = IceCream.logger;
    MapToolsModule.store = new MapAreasStore(folder.resolve("map-areas.json").toFile());
    IceCream.config = new YamlConfiguration();
    IceCream.logger = Logger.getLogger("MapToolsCommandTest");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    MapToolsModule.store = previousStore;
    IceCream.config = previousConfig;
    IceCream.logger = previousLogger;
  }

  private static CommandSourceStack source(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    return source;
  }

  private CommandSourceStack staff() {
    return source(player);
  }

  /** Everything the player was told since the last call, as plain text. */
  private List<String> messages() {
    List<String> lines = new ArrayList<>();
    for (Component line = player.nextComponentMessage();
        line != null;
        line = player.nextComponentMessage()) {
      lines.add(PlainTextComponentSerializer.plainText().serialize(line));
    }
    return lines;
  }

  private void createTown() {
    command.createRect(staff(), 0, 0, 9, 9, "town", "gold", "Spawn Town");
    messages();
  }

  private MapArea area(String id) {
    return MapToolsModule.store.getArea(id);
  }

  // --- create ---

  @Test
  void createsARectangleInTheOverworld() {
    command.createRect(staff(), 9, 9, 0, 0, "town", "gold", "Spawn Town");

    MapArea town = area("town");
    assertNotNull(town);
    assertEquals(MapToolsUtils.overworld(), town.world());
    assertEquals(MapArea.RECT, town.shape());
    assertEquals(MapToolsUtils.rectangle(0, 0, 9, 9), town.points());
    assertTrue(messages().contains("Created claim town."));
  }

  @Test
  void refusesRectanglesThatAreTooLarge() {
    command.createRect(staff(), 0, 0, 5000, 10, "huge", "gold", "Huge");

    assertNull(area("huge"));
    assertFalse(messages().isEmpty());
  }

  @Test
  void refusesTakenIds() {
    createTown();
    command.createRect(staff(), 1000, 1000, 1009, 1009, "town", "gold", "Other");

    assertEquals("Spawn Town", area("town").label());
    assertEquals(List.of("A claim with id town already exists."), messages());
  }

  @Test
  void createsAPolygon() {
    command.createPolygon(staff(), "0 0; 20 0; 20 20; 0 20", "square", "aqua", "Square");

    assertEquals(MapArea.POLYGON, area("square").shape());
    assertEquals(4, area("square").points().size());
  }

  @Test
  void explainsBadPolygonPoints() {
    command.createPolygon(staff(), "0 0; 20 0", "line", "aqua", "Line");

    assertNull(area("line"));
    assertTrue(messages().getFirst().contains("3 to 200 points"));
  }

  @Test
  void createsAMarkerWithoutIconInTheOverworld() {
    command.createMarker(staff(), 50, -20, "shop", "none", "Shop");

    MapArea shop = area("shop");
    assertTrue(shop.isMarker());
    assertNull(shop.icon());
    assertEquals(MapToolsUtils.overworld(), shop.world());
    assertEquals(MapToolsUtils.blockCenter(50, -20), shop.center());
  }

  @Test
  void refusesUnknownMarkerIcons() {
    command.createMarker(staff(), 50, -20, "shop", "no_such_icon", "Shop");

    assertNull(area("shop"));
    assertTrue(messages().getFirst().contains("There is no icon called no_such_icon"));
  }

  // --- set ---

  @Test
  void setsTheLabel() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "label", "New Town");

    assertEquals("New Town", area("town").label());
    assertEquals(List.of("Updated the label of town."), messages());
  }

  @Test
  void refusesAnEmptyLabel() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "label", null);

    assertEquals("Spawn Town", area("town").label());
    assertEquals(List.of("The label cannot be empty."), messages());
  }

  @Test
  void setsColorsByNameOrHex() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "color", "FF8800");
    assertEquals("#ff8800", area("town").color());

    command.set(staff(), Kind.CLAIM, "town", "color", "purple-ish");
    assertEquals("#ff8800", area("town").color());
  }

  @Test
  void setsNearby() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "nearby", "yes");
    assertTrue(area("town").allowsNearby());

    command.set(staff(), Kind.CLAIM, "town", "nearby", null);
    assertFalse(area("town").allowsNearby());

    messages();
    command.set(staff(), Kind.CLAIM, "town", "nearby", "maybe");
    assertEquals(List.of("Use yes or no."), messages());
  }

  @Test
  void setsOpacityAsAPercentage() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "opacity", "50%");
    assertEquals(0.5, area("town").opacity());

    command.set(staff(), Kind.CLAIM, "town", "opacity", null);
    assertNull(area("town").opacity());

    messages();
    command.set(staff(), Kind.CLAIM, "town", "opacity", "150");
    assertEquals(List.of("Use a percentage from 0 to 100."), messages());
  }

  @Test
  void setsOwnersFromACommaList() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "owners", "Alex, Sam,");
    assertEquals(List.of("Alex", "Sam"), area("town").owners());
  }

  @Test
  void noneClearsAMarkerIcon() {
    command.createMarker(staff(), 50, -20, "shop", "none", "Shop");
    command.set(staff(), Kind.MARKER, "shop", "icon", " none ");

    assertNull(area("shop").icon());
  }

  @Test
  void setsNewPolygonPoints() {
    command.createPolygon(staff(), "0 0; 20 0; 20 20", "tri", "aqua", "Triangle");
    command.set(staff(), Kind.CLAIM, "tri", "points", "0 0; 30 0; 30 30; 0 30");

    assertEquals(4, area("tri").points().size());
  }

  @Test
  void renamesWithTheIdField() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "id", "spawn");

    assertNull(area("town"));
    assertEquals("Spawn Town", area("spawn").label());
    assertEquals(List.of("Renamed town to spawn."), messages());
  }

  @Test
  void refusesRenamingToATakenId() {
    createTown();
    command.createRect(staff(), 1000, 1000, 1009, 1009, "other", "gold", "Other");
    messages();

    command.set(staff(), Kind.CLAIM, "town", "id", "other");

    assertNotNull(area("town"));
    assertEquals(List.of("A claim with id other already exists."), messages());
  }

  @Test
  void refusesUnknownFields() {
    createTown();
    command.set(staff(), Kind.CLAIM, "town", "size", "10");

    assertTrue(messages().getFirst().startsWith("Use "));
  }

  @Test
  void saysWhenTheAreaDoesNotExist() {
    command.set(staff(), Kind.CLAIM, "missing", "label", "x");
    assertEquals(List.of("There is no claim called missing."), messages());
  }

  @Test
  void claimsAndMarkersAreNotMixedUp() {
    createTown();
    command.set(staff(), Kind.MARKER, "town", "label", "x");

    assertEquals("Spawn Town", area("town").label());
    assertEquals(List.of("There is no marker called town."), messages());
  }

  @Test
  void movesAMarker() {
    command.createMarker(staff(), 50, -20, "shop", "none", "Shop");
    command.setMove(staff(), Kind.MARKER, "shop", 60, -30);

    assertEquals(MapToolsUtils.blockCenter(60, -30), area("shop").center());
  }

  // --- delete and list ---

  @Test
  void consoleDeletesRightAway() {
    createTown();
    command.delete(source(server.getConsoleSender()), Kind.CLAIM, "town");

    assertNull(area("town"));
  }

  @Test
  void deletingSomethingMissingSaysSo() {
    command.delete(staff(), Kind.CLAIM, "missing");
    assertEquals(List.of("There is no claim called missing."), messages());
  }

  @Test
  void listsMatchingClaimsWithTheirIds() {
    createTown();
    command.createRect(staff(), 1000, 1000, 1009, 1009, "farm", "green", "Wheat Farm");
    messages();

    command.listClaims(staff(), "wheat");

    List<String> lines = messages();
    assertEquals("Claims (1) matching \"wheat\"", lines.getFirst());
    assertTrue(
        lines.stream().anyMatch(line -> line.contains("Wheat Farm") && line.contains("farm")));
    assertTrue(lines.stream().noneMatch(line -> line.contains("Spawn Town")));
  }

  @Test
  void saysWhenNothingMatches() {
    command.listClaims(staff(), "nothing");
    assertEquals(List.of("No claims found."), messages());
  }
}
