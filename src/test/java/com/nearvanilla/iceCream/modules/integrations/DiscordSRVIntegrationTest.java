package com.nearvanilla.iceCream.modules.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.IntegrationTestUtils;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import github.scarsz.discordsrv.DiscordSRV;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

/**
 * The DiscordSRV integration with the plugin present and absent. DiscordSRV itself is a Mockito
 * mock, handed out by a mocked {@code DiscordSRV.getPlugin()}.
 */
class DiscordSRVIntegrationTest {
  private static final String PREFIX = "modules.spectator.messages";
  private static final String ERROR_MESSAGE =
      "Heads up! The Discord leave/join message couldn't be sent.";

  private ServerMock server;
  private MockedStatic<DiscordSRV> discordStatic;
  private DiscordSRV discord;
  private LogCapture log;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    log = LogCapture.of(IceCream.logger);
    discord = mock(DiscordSRV.class);
    discordStatic = mockStatic(DiscordSRV.class);
    discordStatic.when(DiscordSRV::getPlugin).thenReturn(discord);
    player = server.addPlayer("Alex");
  }

  @AfterEach
  void tearDown() {
    discordStatic.close();
    log.close();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  /** Replaces the configuration, which also replaces the logger the capture listens to. */
  private void configure(String yaml) {
    log.close();
    IceCreamFixture.install(server, yaml);
    log = LogCapture.of(IceCream.logger);
  }

  private static DiscordSRVIntegration present() {
    IntegrationTestUtils.installPlugin("DiscordSRV");
    DiscordSRVIntegration integration = new DiscordSRVIntegration();
    integration.init(PREFIX);
    return integration;
  }

  // Initializing

  @Test
  void initIsSkippedWhenDiscordSrvIsNotInstalled() {
    DiscordSRVIntegration integration = new DiscordSRVIntegration();

    integration.init(PREFIX);

    assertEquals(List.of("DiscordSRV not found, skipping integration."), log.messages(Level.INFO));
    discordStatic.verify(DiscordSRV::getPlugin, never());
  }

  @Test
  void initHooksIntoDiscordSrvWhenItIsInstalled() {
    IntegrationTestUtils.installPlugin("DiscordSRV");

    new DiscordSRVIntegration().init(PREFIX);

    assertEquals(List.of("DiscordSRV integration enabled."), log.messages(Level.INFO));
    discordStatic.verify(DiscordSRV::getPlugin);
  }

  // Fake join and leave messages

  @Test
  void withoutDiscordSrvNothingIsSent() {
    DiscordSRVIntegration integration = new DiscordSRVIntegration();
    integration.init(PREFIX);

    integration.sendFakeLeave(player);
    integration.sendFakeJoin(player);

    verifyNoInteractions(discord);
    assertNull(player.nextMessage());
    assertTrue(log.messages(Level.WARNING).isEmpty());
  }

  @Test
  void nothingIsSentBeforeInitEvenWithDiscordSrvInstalled() {
    IntegrationTestUtils.installPlugin("DiscordSRV");
    DiscordSRVIntegration integration = new DiscordSRVIntegration();

    integration.sendFakeLeave(player);
    integration.sendFakeJoin(player);

    verifyNoInteractions(discord);
  }

  @Test
  void aFakeLeaveIsSentWithTheDefaultMessage() {
    DiscordSRVIntegration integration = present();

    integration.sendFakeLeave(player);

    verify(discord).sendLeaveMessage(player, "%player% left the server");
    verify(discord, never()).sendJoinMessage(any(), anyString());
  }

  @Test
  void aFakeJoinIsSentWithTheDefaultMessage() {
    DiscordSRVIntegration integration = present();

    integration.sendFakeJoin(player);

    verify(discord).sendJoinMessage(player, "%player% joined the server");
    verify(discord, never()).sendLeaveMessage(any(), anyString());
  }

  @Test
  void theMessagesComeFromTheConfigurationUnderThePrefix() {
    configure(
        "modules:\n"
            + "  spectator:\n"
            + "    messages:\n"
            + "      discord-join: '%player% appeared'\n"
            + "      discord-leave: '%player% vanished'\n");
    DiscordSRVIntegration integration = present();

    integration.sendFakeJoin(player);
    integration.sendFakeLeave(player);

    verify(discord).sendJoinMessage(player, "%player% appeared");
    verify(discord).sendLeaveMessage(player, "%player% vanished");
  }

  @Test
  void aDifferentPrefixReadsDifferentKeys() {
    configure(
        "modules:\n"
            + "  spectator:\n"
            + "    messages:\n"
            + "      discord-join: spectator join\n"
            + "  other:\n"
            + "    discord-join: other join\n"
            + "    discord-leave: other leave\n");
    IntegrationTestUtils.installPlugin("DiscordSRV");
    DiscordSRVIntegration integration = new DiscordSRVIntegration();
    integration.init("modules.other");

    integration.sendFakeJoin(player);
    integration.sendFakeLeave(player);

    verify(discord).sendJoinMessage(player, "other join");
    verify(discord).sendLeaveMessage(player, "other leave");
  }

  @Test
  void aMissingKeyFallsBackToTheDefaultWhileTheOtherIsConfigured() {
    configure("modules:\n  spectator:\n    messages:\n      discord-join: hello\n");
    DiscordSRVIntegration integration = present();

    integration.sendFakeJoin(player);
    integration.sendFakeLeave(player);

    verify(discord).sendJoinMessage(player, "hello");
    verify(discord).sendLeaveMessage(player, "%player% left the server");
  }

  @Test
  void aFailedFakeLeaveIsLoggedAndTheStaffMemberIsTold() {
    DiscordSRVIntegration integration = present();
    doThrow(new IllegalStateException("bot offline")).when(discord).sendLeaveMessage(any(), any());

    integration.sendFakeLeave(player);

    assertEquals(
        List.of("Failed to send fake leave message to DiscordSRV: bot offline"),
        log.messages(Level.WARNING));
    assertEquals(ERROR_MESSAGE, MessageTestUtils.nextMessage(player));
    assertNull(player.nextMessage());
  }

  @Test
  void aFailedFakeJoinIsLoggedAndTheStaffMemberIsTold() {
    DiscordSRVIntegration integration = present();
    doThrow(new IllegalStateException("bot offline")).when(discord).sendJoinMessage(any(), any());

    integration.sendFakeJoin(player);

    assertEquals(
        List.of("Failed to send fake join message to DiscordSRV: bot offline"),
        log.messages(Level.WARNING));
    assertEquals(ERROR_MESSAGE, MessageTestUtils.nextMessage(player));
    assertNull(player.nextMessage());
  }

  @Test
  void aFailureWithoutAMessageIsStillReported() {
    DiscordSRVIntegration integration = present();
    doThrow(new NullPointerException()).when(discord).sendLeaveMessage(any(), any());

    integration.sendFakeLeave(player);

    assertEquals(1, log.messages(Level.WARNING).size());
    assertEquals(ERROR_MESSAGE, MessageTestUtils.nextMessage(player));
  }

  @Test
  void aFailedSendDoesNotStopTheNextOne() {
    DiscordSRVIntegration integration = present();
    doThrow(new IllegalStateException("bot offline")).when(discord).sendLeaveMessage(any(), any());

    integration.sendFakeLeave(player);
    integration.sendFakeJoin(player);

    verify(discord).sendJoinMessage(player, "%player% joined the server");
  }

  @Test
  void whenDiscordSrvHasNoPluginInstanceNothingIsSentAndNothingThrows() {
    discordStatic.when(DiscordSRV::getPlugin).thenReturn(null);
    DiscordSRVIntegration integration = present();

    integration.sendFakeLeave(player);
    integration.sendFakeJoin(player);

    verifyNoInteractions(discord);
    assertNull(player.nextMessage());
    assertTrue(log.messages(Level.WARNING).isEmpty());
  }

  // The vanished marker

  @Test
  @SuppressWarnings("deprecation")
  void aVanishedPlayerGetsTheMetadataDiscordSrvReads() {
    new DiscordSRVIntegration().setVanishedMetadata(player, true);

    assertTrue(player.hasMetadata("vanished"));
    List<MetadataValue> values = player.getMetadata("vanished");
    assertEquals(1, values.size());
    assertTrue(values.getFirst().asBoolean());
    assertSame(IceCream.instance, values.getFirst().getOwningPlugin());
  }

  @Test
  @SuppressWarnings("deprecation")
  void aPlayerWhoIsNoLongerVanishedLosesTheMetadata() {
    DiscordSRVIntegration integration = new DiscordSRVIntegration();
    integration.setVanishedMetadata(player, true);

    integration.setVanishedMetadata(player, false);

    assertFalse(player.hasMetadata("vanished"));
  }

  @Test
  @SuppressWarnings("deprecation")
  void clearingTheMetadataOfAPlayerWhoWasNeverVanishedIsHarmless() {
    new DiscordSRVIntegration().setVanishedMetadata(player, false);

    assertFalse(player.hasMetadata("vanished"));
  }

  @Test
  void theVanishedMetadataWorksWithoutDiscordSrvInstalled() {
    DiscordSRVIntegration integration = new DiscordSRVIntegration();
    integration.init(PREFIX);

    integration.setVanishedMetadata(player, true);

    assertTrue(player.hasMetadata("vanished"));
  }
}
