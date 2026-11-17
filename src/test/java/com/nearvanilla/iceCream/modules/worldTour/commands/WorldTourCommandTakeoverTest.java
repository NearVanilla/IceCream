package com.nearvanilla.iceCream.modules.worldTour.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.clickCommands;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.joinedFlag;
import static com.nearvanilla.iceCream.testutil.WorldTourTestUtils.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils;
import com.nearvanilla.iceCream.testutil.WorldTourTestUtils.TourPlayer;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRules;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Tests how the host of a tour hands the tour over to another player. */
class WorldTourCommandTakeoverTest {
  private static final String PLAYERS_ONLY = "✗ This command can only be used by players.";
  private static final String NO_PENDING = "✗ No pending takeover request found.";

  private ServerMock server;
  private WorldMock overworld;
  private WorldTourModule module;
  private WorldTourCommand command;
  private TourPlayer host;
  private TourPlayer requester;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WorldTourTestUtils.CONFIG);
    overworld = server.addSimpleWorld("world");
    module = new WorldTourModule();
    try (LogCapture ignored = LogCapture.of(IceCream.logger)) {
      module.register();
    }
    command = new WorldTourCommand(module);
    host = addPlayer(server, "Host");
    requester = addPlayer(server, "Requester");
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

  /** Starts a tour as the host and has the requester ask to take it over. */
  private void requestTakeover() {
    startTourAs(host);
    command.takeoverCommand(source(requester));
    nextMessage(requester);
    nextMessage(host);
  }

  // Requesting a takeover

  @Test
  void theHostCannotTakeOverFromThemselves() {
    startTourAs(host);

    command.takeoverCommand(source(host));

    assertEquals("✗ You are already the World Tour host.", nextMessage(host));
    assertNull(nextMessage(host));
    assertSame(host, module.getCurrentHost());
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aTakeoverNeedsATourToBeRunning() {
    command.takeoverCommand(source(requester));

    assertEquals("✗ There is no World Tour host set.", nextMessage(requester));
    assertNull(nextMessage(requester));
    assertNull(module.getCurrentHost());
  }

  @Test
  void aTakeoverRequestIsStoredForTheHost() {
    startTourAs(host);

    command.takeoverCommand(source(requester));

    assertEquals(requester.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void theRequesterIsToldTheRequestWasSent() {
    startTourAs(host);

    command.takeoverCommand(source(requester));

    assertEquals(
        "Takeover request sent. Waiting for the current host to respond.", nextMessage(requester));
    assertNull(nextMessage(requester));
  }

  @Test
  void theHostIsAskedToAcceptOrDenyTheRequest() {
    startTourAs(host);

    command.takeoverCommand(source(requester));

    Component message = host.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "Requester wants to take over as World Tour host. [Accept] [Deny]",
        MessageTestUtils.plainText(message));
    assertEquals(
        List.of("/worldtour takeover confirm", "/worldtour takeover deny"), clickCommands(message));
    assertNull(nextMessage(host));
  }

  @Test
  void aTakeoverRequestChangesNothingUntilTheHostAnswers() {
    startTourAs(host);

    command.takeoverCommand(source(requester));

    assertSame(host, module.getCurrentHost());
    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(requester.hasPotionEffect(PotionEffectType.GLOWING));
    assertNull(joinedFlag(requester));
  }

  @Test
  void aSecondRequestReplacesTheFirst() {
    TourPlayer latecomer = addPlayer(server, "Latecomer");
    startTourAs(host);
    command.takeoverCommand(source(requester));

    command.takeoverCommand(source(latecomer));

    assertEquals(latecomer.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aTakeoverFromAnOfflineHostHandsOverTheTourAtOnce() {
    startTourAs(host);
    host.disconnect();

    command.takeoverCommand(source(requester));

    assertSame(requester, module.getCurrentHost());
    assertSame(requester, module.getCurrentGlowing());
    assertTrue(requester.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertEquals(Boolean.TRUE, joinedFlag(requester));
  }

  @Test
  void aTakeoverFromAnOfflineHostTellsTheNewHost() {
    startTourAs(host);
    host.disconnect();

    command.takeoverCommand(source(requester));

    assertEquals("✓ You are now the World Tour host!", nextMessage(requester));
    assertEquals("The previous host was offline. Tour continues.", nextMessage(requester));
    assertNull(nextMessage(requester));
  }

  @Test
  void aTakeoverFromAnOfflineHostStoresNoRequestAndKeepsTheEnvironment() {
    overworld.setStorm(true);
    startTourAs(host);
    host.disconnect();

    command.takeoverCommand(source(requester));

    assertNull(module.removePendingTakeover(host.getUniqueId()));
    assertFalse(overworld.hasStorm());
    assertEquals(1000L, overworld.getTime());
    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void theOfflineHostStaysAParticipantAfterTheTakeover() {
    startTourAs(host);
    host.disconnect();

    command.takeoverCommand(source(requester));

    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void aTakeoverIsRefusedForTheConsole() {
    startTourAs(host);
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.takeoverCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertSame(host, module.getCurrentHost());
  }

  // Confirming

  @Test
  void confirmingWithoutARequestTellsThePlayerThereIsNone() {
    startTourAs(host);

    command.takeoverConfirmCommand(source(host));

    assertEquals(NO_PENDING, nextMessage(host));
    assertNull(nextMessage(host));
    assertSame(host, module.getCurrentHost());
  }

  @Test
  void aPlayerWhoIsNotTheHostCannotConfirmTheirOwnRequest() {
    requestTakeover();

    command.takeoverConfirmCommand(source(requester));

    assertEquals(NO_PENDING, nextMessage(requester));
    assertSame(host, module.getCurrentHost());
    assertEquals(requester.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void confirmingHandsTheTourToTheRequester() {
    requestTakeover();

    command.takeoverConfirmCommand(source(host));

    assertSame(requester, module.getCurrentHost());
    assertSame(requester, module.getCurrentGlowing());
    assertTrue(requester.hasPotionEffect(PotionEffectType.GLOWING));
    assertEquals(Boolean.TRUE, joinedFlag(requester));
  }

  @Test
  void confirmingTakesTheGlowFromThePreviousHostButKeepsThemInTheTour() {
    requestTakeover();

    command.takeoverConfirmCommand(source(host));

    assertFalse(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertEquals(Boolean.TRUE, joinedFlag(host));
  }

  @Test
  void confirmingTellsBothPlayersTheResult() {
    requestTakeover();

    command.takeoverConfirmCommand(source(host));

    assertEquals("✓ You are now the World Tour host!", nextMessage(requester));
    assertNull(nextMessage(requester));
    assertEquals("✓ Requester is now the World Tour host.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void confirmingKeepsTheTourEnvironment() {
    overworld.setStorm(true);
    requestTakeover();

    command.takeoverConfirmCommand(source(host));

    assertFalse(overworld.hasStorm());
    assertEquals(1000L, overworld.getTime());
    assertEquals(Boolean.TRUE, overworld.getGameRuleValue(GameRules.KEEP_INVENTORY));
  }

  @Test
  void aRequestCanOnlyBeConfirmedOnce() {
    requestTakeover();
    command.takeoverConfirmCommand(source(host));
    nextMessage(host);

    command.takeoverConfirmCommand(source(host));

    assertEquals(NO_PENDING, nextMessage(host));
    assertSame(requester, module.getCurrentHost());
  }

  @Test
  void theNewHostCanEndTheTourAndRestoreTheEnvironment() {
    overworld.setStorm(true);
    overworld.setFullTime(50000);
    requestTakeover();
    command.takeoverConfirmCommand(source(host));

    command.endCommand(source(requester));

    assertNull(module.getCurrentHost());
    assertTrue(overworld.hasStorm());
    assertEquals(50000L, overworld.getFullTime());
  }

  @Test
  void thePreviousHostIsNoLongerAllowedToEndTheTour() {
    requestTakeover();
    command.takeoverConfirmCommand(source(host));
    nextMessage(host);

    command.endCommand(source(host));

    assertEquals("✗ Only the World Tour host can end the tour.", nextMessage(host));
    assertSame(requester, module.getCurrentHost());
  }

  @Test
  void confirmingIsRefusedForTheConsole() {
    requestTakeover();
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.takeoverConfirmCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertSame(host, module.getCurrentHost());
  }

  // Denying

  @Test
  void denyingWithoutARequestTellsThePlayerThereIsNone() {
    startTourAs(host);

    command.takeoverDenyCommand(source(host));

    assertEquals(NO_PENDING, nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void aPlayerWhoIsNotTheHostCannotDenyTheirOwnRequest() {
    requestTakeover();

    command.takeoverDenyCommand(source(requester));

    assertEquals(NO_PENDING, nextMessage(requester));
    assertEquals(requester.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void denyingTellsTheRequesterAndTheHost() {
    requestTakeover();

    command.takeoverDenyCommand(source(host));

    assertEquals("✗ Your takeover request was denied.", nextMessage(requester));
    assertNull(nextMessage(requester));
    assertEquals("✓ Takeover request denied.", nextMessage(host));
    assertNull(nextMessage(host));
  }

  @Test
  void denyingLeavesTheHostAndTheGlowAlone() {
    requestTakeover();

    command.takeoverDenyCommand(source(host));

    assertSame(host, module.getCurrentHost());
    assertSame(host, module.getCurrentGlowing());
    assertTrue(host.hasPotionEffect(PotionEffectType.GLOWING));
    assertFalse(requester.hasPotionEffect(PotionEffectType.GLOWING));
    assertNull(joinedFlag(requester));
  }

  @Test
  void denyingUsesUpTheRequest() {
    requestTakeover();
    command.takeoverDenyCommand(source(host));
    nextMessage(host);

    command.takeoverConfirmCommand(source(host));

    assertEquals(NO_PENDING, nextMessage(host));
    assertSame(host, module.getCurrentHost());
  }

  @Test
  void aRequestCanOnlyBeDeniedOnce() {
    requestTakeover();
    command.takeoverDenyCommand(source(host));
    nextMessage(requester);
    nextMessage(host);

    command.takeoverDenyCommand(source(host));

    assertEquals(NO_PENDING, nextMessage(host));
    assertNull(nextMessage(requester));
  }

  @Test
  void denyingStillWorksWhenTheRequesterHasLeft() {
    requestTakeover();
    requester.disconnect();

    command.takeoverDenyCommand(source(host));

    assertEquals("✓ Takeover request denied.", nextMessage(host));
    assertNull(nextMessage(host));
    assertSame(host, module.getCurrentHost());
    assertNull(module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void aDeniedPlayerCanAskAgain() {
    requestTakeover();
    command.takeoverDenyCommand(source(host));
    nextMessage(requester);

    command.takeoverCommand(source(requester));

    assertEquals(requester.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }

  @Test
  void denyingIsRefusedForTheConsole() {
    requestTakeover();
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.takeoverDenyCommand(source(console));

    assertEquals(PLAYERS_ONLY, nextMessage(console));
    assertNull(nextMessage(console));
    assertEquals(requester.getUniqueId(), module.removePendingTakeover(host.getUniqueId()));
  }
}
