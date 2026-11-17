package com.nearvanilla.iceCream.modules.worldTour.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.clickCommands;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.setJoined;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Tests the commands that join and leave a tour, and the suggestions the commands offer. */
class WorldTourCommandTest {
  private static final String PLAYERS_ONLY = "✗ This command can only be used by players.";

  private ServerMock server;
  private WorldTourModule module;
  private WorldTourCommand command;
  private TourPlayer host;
  private TourPlayer guest;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    server.addSimpleWorld("world");
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    command = new WorldTourCommand(module);
    host = addPlayer(server, "Host");
    guest = addPlayer(server, "Guest");
  }

  @AfterEach
  void tearDown() {
    WorldTourModule.JOINED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void startTourAs(TourPlayer player) {
    command.startCommand(source(player));
    nextMessage(player);
  }

  // Suggestions

  @Test
  void suggestsTheThreeWeatherTypes() {
    List<String> suggestions = command.suggestWeather(null, null);

    assertEquals(3, suggestions.size());
    assertEquals(Set.of("clear", "rain", "thunder"), Set.copyOf(suggestions));
  }

  @Test
  void suggestsTheFourTimeTypes() {
    List<String> suggestions = command.suggestTime(null, null);

    assertEquals(4, suggestions.size());
    assertEquals(Set.of("day", "noon", "night", "midnight"), Set.copyOf(suggestions));
  }

  // Join

  @Test
  void joiningMarksThePlayerAsAParticipant() {
    command.joinCommand(source(guest));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
  }

  @Test
  void joiningTellsThePlayerTheyJoined() {
    command.joinCommand(source(guest));

    assertEquals("✓ You have joined the World Tour!", nextMessage(guest));
    assertNull(nextMessage(guest));
  }

  @Test
  void joiningAgainTellsThePlayerTheyAlreadyTakePart() {
    command.joinCommand(source(guest));
    nextMessage(guest);

    command.joinCommand(source(guest));

    assertEquals("✗ You are already participating in the World Tour!", nextMessage(guest));
    assertNull(nextMessage(guest));
    assertEquals(Boolean.TRUE, joinedFlag(guest));
  }

  @Test
  void aPlayerWhoOptedOutCanJoinAgain() {
    setJoined(guest, false);

    command.joinCommand(source(guest));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
    assertEquals("✓ You have joined the World Tour!", nextMessage(guest));
  }

  @Test
  void joiningChangesOnlyTheSendersFlag() {
    command.joinCommand(source(guest));

    assertNull(joinedFlag(host));
    assertNull(nextMessage(host));
  }

  @Test
  void joiningDoesNotNeedATourToBeRunning() {
    assertNull(module.getCurrentHost());

    command.joinCommand(source(guest));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
  }

  @Test
  void joiningIsRefusedForTheConsole() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.joinCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
  }

  // Leave

  @Test
  void leavingOptsThePlayerOut() {
    setJoined(guest, true);

    command.leaveCommand(source(guest));

    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void leavingTellsThePlayerTheyLeft() {
    setJoined(guest, true);

    command.leaveCommand(source(guest));

    assertEquals("✓ You have left the World Tour.", nextMessage(guest));
    assertNull(nextMessage(guest));
  }

  @Test
  void leavingWithoutHavingJoinedTellsThePlayerTheyDoNotTakePart() {
    command.leaveCommand(source(guest));

    assertEquals("✗ You are not participating in the World Tour.", nextMessage(guest));
    assertNull(nextMessage(guest));
    assertNull(joinedFlag(guest));
  }

  @Test
  void leavingAfterOptingOutTellsThePlayerTheyDoNotTakePart() {
    setJoined(guest, false);

    command.leaveCommand(source(guest));

    assertEquals("✗ You are not participating in the World Tour.", nextMessage(guest));
    assertEquals(Boolean.FALSE, joinedFlag(guest));
  }

  @Test
  void leavingTwiceOnlyLeavesOnce() {
    setJoined(guest, true);
    command.leaveCommand(source(guest));
    nextMessage(guest);

    command.leaveCommand(source(guest));

    assertEquals("✗ You are not participating in the World Tour.", nextMessage(guest));
  }

  @Test
  void joiningAfterLeavingWorks() {
    command.joinCommand(source(guest));
    command.leaveCommand(source(guest));

    command.joinCommand(source(guest));

    assertEquals(Boolean.TRUE, joinedFlag(guest));
  }

  @Test
  void theHostCannotLeaveAndIsPointedToTheEndCommand() {
    startTourAs(host);

    command.leaveCommand(source(host));

    Component message = host.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "✗ You are the host. Use /worldtour end to conclude the tour. "
            + "This will end the tour for all participants.",
        MessageTestUtils.plainText(message));
    assertEquals(List.of("/worldtour end"), clickCommands(message));
    assertNull(nextMessage(host));
  }

  @Test
  void theHostStaysHostAndParticipantAfterTryingToLeave() {
    startTourAs(host);

    command.leaveCommand(source(host));

    assertSame(host, module.getCurrentHost());
    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void aParticipantCanLeaveWhileATourIsRunning() {
    startTourAs(host);
    command.joinCommand(source(guest));
    nextMessage(guest);

    command.leaveCommand(source(guest));

    assertEquals(Boolean.FALSE, joinedFlag(guest));
    assertEquals("✓ You have left the World Tour.", nextMessage(guest));
    assertSame(host, module.getCurrentHost());
  }

  @Test
  void leavingIsRefusedForTheConsole() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.leaveCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
  }
}
