package com.nearvanilla.iceCream.modules.mapTools.commands;

import static com.nearvanilla.iceCream.testutil.CommandTestUtils.clickCommands;
import static com.nearvanilla.iceCream.testutil.CommandTestUtils.clickSuggestions;
import static com.nearvanilla.iceCream.testutil.CommandTestUtils.hoverTexts;
import static com.nearvanilla.iceCream.testutil.CommandTestUtils.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.modules.mapTools.commands.MapToolsCommand.Kind;
import com.nearvanilla.iceCream.testutil.CommandTestUtils;
import com.nearvanilla.iceCream.testutil.CommandTestUtils.TeleportingPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Listing claims and markers, and teleporting to them. */
class MapToolsCommandListTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private Scenario scenario;
  private DialogPlayer alex;
  private ConsoleCommandSenderMock console;
  private final MapToolsCommand command = new MapToolsCommand();

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    console = scenario.server().getConsoleSender();
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private MapArea store(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private CommandSourceStack source() {
    return stack(alex);
  }

  /** Every message the player received so far, as plain text. */
  private List<String> messages() {
    List<String> messages = new ArrayList<>();
    String message;
    while ((message = MessageTestUtils.nextMessage(alex)) != null) messages.add(message);
    return messages;
  }

  private List<Component> components() {
    List<Component> components = new ArrayList<>();
    Component message;
    while ((message = alex.nextComponentMessage()) != null) components.add(message);
    return components;
  }

  private void allowTeleporting() {
    alex.addAttachment(IceCream.instance, MapToolsUtils.TP_PERMISSION, true);
  }

  // What is listed

  @Test
  void withNoClaimsTheListSaysSo() {
    command.listClaims(source(), null);

    assertEquals(List.of("No claims found."), messages());
  }

  @Test
  void withNoMarkersTheListSaysSo() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listMarkers(source(), null);

    assertEquals(List.of("No markers found."), messages());
  }

  @Test
  void claimsAreListedWithTheirCountAndASearchHint() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.circle("pond", WORLD, 40, 40, 5));

    command.listClaims(source(), null);

    assertEquals(
        List.of(
            "Claims (2)",
            "Search with /maptools list claims <text>",
            " ■ Label home · home",
            " ■ Label pond · pond"),
        messages());
  }

  @Test
  void theSearchHintSuggestsTheCommandWhenClicked() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listClaims(source(), null);

    List<Component> shown = components();
    assertEquals(List.of("/maptools list claims "), clickSuggestions(shown.get(1)));
    assertEquals(List.of("Click to search"), hoverTexts(shown.get(1)));
  }

  @Test
  void markersAreListedWithoutTheClaims() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    command.listMarkers(source(), null);

    assertEquals(
        List.of("Markers (1)", "Search with /maptools list markers <text>", " ■ Label spot · spot"),
        messages());
  }

  @Test
  void claimsAreListedWithoutTheMarkers() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    command.listClaims(source(), null);

    assertEquals(3, messages().size());
  }

  @Test
  void hoveringAClaimShowsItsOwnersAndShape() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withOwners(List.of("Alex", "Sam")));
    store(MapToolsTestUtils.circle("pond", WORLD, 40, 40, 5));

    command.listClaims(source(), null);

    List<Component> shown = components();
    assertEquals(List.of("Owner(s): Alex, Sam\nShape: rect"), hoverTexts(shown.get(2)));
    assertEquals(List.of("Owner(s): none\nShape: circle"), hoverTexts(shown.get(3)));
  }

  @Test
  void hoveringAMarkerShowsItsOwnersAndIcon() {
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));
    store(MapToolsTestUtils.marker("flag", WORLD, 8, 8).withIcon("pin"));

    command.listMarkers(source(), null);

    List<Component> shown = components();
    assertEquals(List.of("Owner(s): none\nIcon: none"), hoverTexts(shown.get(2)));
    assertEquals(List.of("Owner(s): none\nIcon: pin"), hoverTexts(shown.get(3)));
  }

  // Limiting and searching

  private void claims(int count) {
    for (int i = 1; i <= count; i++) {
      store(MapToolsTestUtils.rect("c%02d".formatted(i), WORLD, i * 20, 0, i * 20 + 9, 9));
    }
  }

  @Test
  void aListWithoutASearchShowsTheFirstTenAndCountsTheRest() {
    claims(12);

    command.listClaims(source(), null);

    List<String> messages = messages();
    assertEquals("Claims (12)", messages.getFirst());
    assertEquals(1 + 1 + 10 + 1, messages.size());
    assertEquals(" ■ Label c01 · c01", messages.get(2));
    assertEquals(" ■ Label c10 · c10", messages.get(11));
    assertEquals("...and 2 more. Search to narrow it down.", messages.getLast());
  }

  @Test
  void exactlyTenClaimsAreAllShownWithoutAMoreLine() {
    claims(10);

    command.listClaims(source(), null);

    List<String> messages = messages();
    assertEquals(1 + 1 + 10, messages.size());
    assertEquals(" ■ Label c10 · c10", messages.getLast());
  }

  @Test
  void aSearchShowsEveryMatchAndNoHint() {
    claims(12);

    command.listClaims(source(), "c");

    List<String> messages = messages();
    assertEquals("Claims (12) matching \"c\"", messages.getFirst());
    assertEquals(1 + 12, messages.size());
    assertTrue(messages.stream().noneMatch(m -> m.startsWith("Search with")));
    assertTrue(messages.stream().noneMatch(m -> m.contains("more")));
  }

  @Test
  void theSearchLooksAtTheIdTheLabelAndTheOwners() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9).withLabel("Bakery"));
    store(MapToolsTestUtils.rect("farm", WORLD, 40, 0, 49, 9).withOwners(List.of("Alex")));

    command.listClaims(source(), "home");
    assertEquals(List.of("Claims (1) matching \"home\"", " ■ Label home · home"), messages());

    command.listClaims(source(), "bakery");
    assertEquals(List.of("Claims (1) matching \"bakery\"", " ■ Bakery · shop"), messages());

    command.listClaims(source(), "alex");
    assertEquals(List.of("Claims (1) matching \"alex\"", " ■ Label farm · farm"), messages());
  }

  @Test
  void theSearchIgnoresCaseButShowsItAsTyped() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listClaims(source(), "HoMe");

    assertEquals(List.of("Claims (1) matching \"HoMe\"", " ■ Label home · home"), messages());
  }

  @Test
  void aSearchWithoutMatchesSaysNothingWasFound() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listClaims(source(), "castle");

    assertEquals(List.of("No claims found."), messages());
  }

  @Test
  void aMarkerSearchOnlyLooksAtMarkers() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    command.listMarkers(source(), "home");

    assertEquals(List.of("No markers found."), messages());
  }

  @Test
  void aLabelWithMarkupIsShownAsPlainText() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9).withLabel("<red>Big</red> <b>Home"));

    command.listClaims(source(), null);

    List<String> messages = messages();
    assertEquals(" ■ <red>Big</red> <b>Home · home", messages.getLast());
  }

  @Test
  void quotesInALabelOrAnOwnerNameDoNotBreakTheEntry() {
    store(
        MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9)
            .withLabel("Bob's \"Castle\"")
            .withOwners(List.of("O'Brien")));

    command.listClaims(source(), null);

    List<Component> shown = components();
    assertEquals(" ■ Bob's \"Castle\" · home", MessageTestUtils.plainText(shown.get(2)));
    assertEquals(List.of("Owner(s): O'Brien\nShape: rect"), hoverTexts(shown.get(2)));
  }

  // Teleport buttons

  @Test
  void aPlayerWithoutTheTeleportPermissionGetsNoButtons() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listClaims(source(), null);

    List<Component> shown = components();
    assertTrue(clickCommands(shown.get(2)).isEmpty());
    assertEquals(" ■ Label home · home", MessageTestUtils.plainText(shown.get(2)));
  }

  @Test
  void aPlayerWithTheTeleportPermissionGetsAButtonForEveryEntry() {
    allowTeleporting();
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    store(MapToolsTestUtils.circle("pond", WORLD, 40, 40, 5));

    command.listClaims(source(), null);

    List<Component> shown = components();
    assertEquals(" ■ Label home · home   [TP]", MessageTestUtils.plainText(shown.get(2)));
    assertEquals(List.of("/maptools tp claim home"), clickCommands(shown.get(2)));
    assertEquals(List.of("/maptools tp claim pond"), clickCommands(shown.get(3)));
  }

  @Test
  void theTeleportButtonNamesTheKindAndShowsTheLabelWhenHovered() {
    allowTeleporting();
    store(MapToolsTestUtils.marker("spot", WORLD, 3, 3));

    command.listMarkers(source(), null);

    Component entry = components().get(2);
    assertEquals(List.of("/maptools tp marker spot"), clickCommands(entry));
    assertTrue(hoverTexts(entry).contains("Teleport to Label spot"));
  }

  @Test
  void theConsoleIsListedTheSameWay() {
    store(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    command.listClaims(stack(console), null);

    assertEquals("Claims (1)", MessageTestUtils.nextMessage(console));
    assertNotNull(console.nextComponentMessage());
  }

  // Teleporting

  private TeleportingPlayer traveller() {
    return CommandTestUtils.addTeleportingPlayer(scenario.server(), "Kim", scenario.limbo());
  }

  @Test
  void teleportingGoesToTheMiddleOfAClaimOnTheGround() {
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "home");

    assertEquals(1, kim.teleports().size());
    Location where = kim.teleports().getFirst();
    assertEquals(scenario.overworld(), where.getWorld());
    assertEquals(15.0, where.getX(), 1e-9);
    assertEquals(15.0, where.getZ(), 1e-9);
    assertEquals(scenario.overworld().getHighestBlockYAt(15, 15) + 1, where.getY(), 1e-9);
  }

  @Test
  void teleportingKeepsWhereThePlayerIsLooking() {
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "home");

    assertEquals(30f, kim.teleports().getFirst().getYaw());
    assertEquals(10f, kim.teleports().getFirst().getPitch());
  }

  @Test
  void theMiddleOfACircleIsItsCenter() {
    store(MapToolsTestUtils.circle("pond", WORLD, 40, 50, 20));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "pond");

    Location where = kim.teleports().getFirst();
    assertEquals(40.5, where.getX(), 1e-6);
    assertEquals(50.5, where.getZ(), 1e-6);
  }

  @Test
  void aMarkerIsTeleportedToAtItsOwnSpot() {
    store(MapToolsTestUtils.marker("spot", WORLD, -4, -9));
    // The ground differs from one block to the next, so the column has to be the right one: the
    // marker is on the block -4, -9 and not on the block -3, -8 that cutting -3.5 and -8.5 gives.
    scenario.overworld().getBlockAt(-4, 30, -9).setType(Material.STONE);
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.MARKER, "spot");

    Location where = kim.teleports().getFirst();
    assertEquals(-3.5, where.getX(), 1e-9);
    assertEquals(-8.5, where.getZ(), 1e-9);
    assertEquals(31, where.getY(), 1e-9);
  }

  @Test
  void theAverageOfTheCornersIsUsedForAnyOtherShape() {
    store(MapToolsTestUtils.polygon("tri", WORLD, 0.5, 0.5, 30.5, 0.5, 0.5, 30.5));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "tri");

    Location where = kim.teleports().getFirst();
    assertEquals(10.5, where.getX(), 1e-9);
    assertEquals(10.5, where.getZ(), 1e-9);
  }

  @Test
  void teleportingToAnUnknownAreaSaysSo() {
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "nobody");

    assertEquals("There is no claim called nobody.", MessageTestUtils.nextMessage(kim));
    assertTrue(kim.teleports().isEmpty());
  }

  @Test
  void teleportingToAClaimAsAMarkerSaysThereIsNoSuchMarker() {
    store(MapToolsTestUtils.rect("home", WORLD, 10, 10, 19, 19));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.MARKER, "home");

    assertEquals("There is no marker called home.", MessageTestUtils.nextMessage(kim));
    assertTrue(kim.teleports().isEmpty());
  }

  @Test
  void teleportingToAnAreaInAWorldThatIsNotLoadedSaysSo() {
    store(MapToolsTestUtils.rect("lost", "minecraft:the_void", 0, 0, 9, 9));
    TeleportingPlayer kim = traveller();

    command.teleport(stack(kim), Kind.CLAIM, "lost");

    assertEquals("That world is not loaded.", MessageTestUtils.nextMessage(kim));
    assertNull(kim.nextMessage());
    assertTrue(kim.teleports().isEmpty());
  }
}
