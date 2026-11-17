package com.nearvanilla.iceCream.modules.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.testutil.IntegrationTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.draycia.carbon.api.event.events.CarbonPrivateChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

/**
 * The CarbonChat integration with the plugin present and absent: the chat restriction for hidden
 * players, the chat filter and cleaning up.
 */
class CarbonChatIntegrationTest extends CarbonChatTestBase {
  // Chat from hidden players: initializing

  @Test
  void initIsSkippedWhenCarbonChatIsNotInstalled() {
    CarbonChatIntegration integration = new CarbonChatIntegration();

    integration.init(PREFIX, this::isHidden, STATE);

    assertEquals(List.of("CarbonChat not found, skipping integration."), log.messages(Level.INFO));
    assertTrue(events.subscriptions.isEmpty());
  }

  @Test
  void initDoesNotTouchCarbonChatWhenItIsNotInstalledEvenIfItsApiIsLoaded() {
    IntegrationTestUtils.registerCarbonChat(carbon);

    new CarbonChatIntegration().init(PREFIX, this::isHidden, STATE);

    assertTrue(events.subscriptions.isEmpty());
  }

  @Test
  void initSubscribesToChatWhenCarbonChatIsInstalled() {
    initialised();

    assertEquals(1, events.active(CarbonChatEvent.class));
    assertEquals(List.of("CarbonChat integration enabled."), log.messages(Level.INFO));
  }

  @Test
  void initWarnsWhenCarbonChatHasNotLoadedItsApi() {
    IntegrationTestUtils.installPlugin("CarbonChat");

    new CarbonChatIntegration().init(PREFIX, this::isHidden, STATE);

    assertEquals(
        List.of("Failed to hook into CarbonChat: CarbonChat not initialized!"),
        log.messages(Level.WARNING));
    assertTrue(events.subscriptions.isEmpty());
  }

  @Test
  void initWarnsWhenCarbonChatHasNoEventHandler() {
    carbonPresent();
    when(carbon.eventHandler()).thenReturn(null);

    new CarbonChatIntegration().init(PREFIX, this::isHidden, STATE);

    assertEquals(1, log.messages(Level.WARNING).size());
    assertTrue(
        log.messages(Level.WARNING).getFirst().startsWith("Failed to hook into CarbonChat: "));
    assertTrue(log.messages(Level.INFO).isEmpty());
  }

  @Test
  void initWarnsWhenSubscribingFails() {
    carbonPresent();
    events.failure = new IllegalStateException("closed");

    new CarbonChatIntegration().init(PREFIX, this::isHidden, STATE);

    assertEquals(List.of("Failed to hook into CarbonChat: closed"), log.messages(Level.WARNING));
    assertTrue(log.messages(Level.INFO).isEmpty());
  }

  // Chat from hidden players: handling

  @Test
  void aVisiblePlayersChatIsLeftAlone() {
    initialised();
    CarbonChatEvent event = chat(carbonPlayer(alex), "global", "hello");

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @Test
  void aHiddenPlayerCannotChatInAChannelThatIsNotAllowed() {
    initialised();
    hidden.add(alex);
    CarbonChatEvent event = chat(carbonPlayer(alex), "global", "hello");

    events.emit(event);

    verify(event).cancelled(true);
    assertEquals(
        "You cannot use chat while in spectator mode.", MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextMessage());
  }

  @Test
  void theBlockedMessageIsRed() {
    initialised();
    hidden.add(alex);

    events.emit(chat(carbonPlayer(alex), "global", "hello"));

    assertEquals(NamedTextColor.RED, alex.nextComponentMessage().color());
  }

  @Test
  void aHiddenPlayerMayChatInAnAllowedChannel() {
    configure(
        "modules:\n  spectator:\n    carbon-chat:\n      allowed-channels:\n        - staff\n");
    initialised();
    hidden.add(alex);
    CarbonChatEvent event = chat(carbonPlayer(alex), "staff", "hello");

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @Test
  void theBlockedMessageNamesTheAllowedChannel() {
    configure(
        "modules:\n  spectator:\n    carbon-chat:\n      allowed-channels:\n        - staff\n");
    initialised();
    hidden.add(alex);
    CarbonChatEvent event = chat(carbonPlayer(alex), "global", "hello");

    events.emit(event);

    verify(event).cancelled(true);
    assertEquals(
        "You can only use staff while in spectator mode.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void channelNamesAreComparedWithoutRegardToCase() {
    configure(
        "modules:\n  spectator:\n    carbon-chat:\n      allowed-channels:\n        - Staff\n");
    initialised();
    hidden.add(alex);
    CarbonChatEvent event = chat(carbonPlayer(alex), "staff", "hello");

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
  }

  @Test
  void theConfigurationPrefixDecidesWhichChannelsAreAllowed() {
    configure(
        "modules:\n"
            + "  spectator:\n"
            + "    carbon-chat:\n"
            + "      allowed-channels: [staff]\n"
            + "  other:\n"
            + "    carbon-chat:\n"
            + "      allowed-channels: [global]\n");
    carbonPresent();
    new CarbonChatIntegration().init("modules.other", this::isHidden, "vanished");
    hidden.add(alex);
    CarbonChatEvent global = chat(carbonPlayer(alex), "global", "hello");
    CarbonChatEvent staff = chat(carbonPlayer(alex), "staff", "hello");

    events.emit(global);
    events.emit(staff);

    verify(global, never()).cancelled(anyBoolean());
    verify(staff).cancelled(true);
    assertEquals("You can only use global while vanished.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void initAgainReplacesTheAllowedChannels() {
    configure("modules:\n  spectator:\n    carbon-chat:\n      allowed-channels: [staff]\n");
    CarbonChatIntegration integration = initialised();
    configure("modules:\n  spectator:\n    carbon-chat:\n      allowed-channels: [global]\n");
    integration.init(PREFIX, this::isHidden, STATE);
    hidden.add(alex);
    CarbonChatEvent staff = chat(carbonPlayer(alex), "staff", "hello");
    CarbonChatEvent global = chat(carbonPlayer(alex), "global", "hello");

    events.emit(staff);
    events.emit(global);

    // Both subscriptions run the same handler, so a blocked message is cancelled by each.
    verify(staff, atLeastOnce()).cancelled(true);
    verify(global, never()).cancelled(anyBoolean());
  }

  @Test
  void aSenderWhoIsNotOnTheServerIsIgnored() {
    initialised();
    CarbonChatEvent event = chat(carbonPlayer(UUID.randomUUID()), "global", "hello");

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
  }

  @Test
  void onlyTheHiddenPlayersAreBlocked() {
    initialised();
    hidden.add(alex);
    CarbonChatEvent fromSam = chat(carbonPlayer(sam), "global", "hello");

    events.emit(fromSam);

    verify(fromSam, never()).cancelled(anyBoolean());
    assertNull(sam.nextMessage());
  }

  // The chat filter

  @Test
  void theChatFilterIsSkippedWhenCarbonChatIsNotInstalled() {
    new CarbonChatIntegration().initChatFilter((id, text) -> true);

    assertEquals(List.of("CarbonChat not found, skipping chat filter."), log.messages(Level.INFO));
    assertTrue(events.subscriptions.isEmpty());
  }

  @Test
  void theChatFilterWorksWithoutInit() {
    carbonPresent();
    CarbonChatIntegration integration = new CarbonChatIntegration();

    integration.initChatFilter((id, text) -> true);

    assertEquals(1, events.active(CarbonChatEvent.class));
    assertEquals(List.of("CarbonChat chat filter enabled."), log.messages(Level.INFO));
  }

  @Test
  void theChatFilterCancelsTheMessagesItSelects() {
    carbonPresent();
    new CarbonChatIntegration().initChatFilter((id, text) -> text.contains("secret"));
    CarbonChatEvent secret = chat(carbonPlayer(alex), "global", "the secret word");
    CarbonChatEvent plain = chat(carbonPlayer(alex), "global", "good morning");

    events.emit(secret);
    events.emit(plain);

    verify(secret).cancelled(true);
    verify(plain, never()).cancelled(anyBoolean());
  }

  @Test
  void theChatFilterGetsTheSendersIdAndThePlainTextOfTheMessage() {
    carbonPresent();
    List<String> seen = new ArrayList<>();
    new CarbonChatIntegration().initChatFilter((id, text) -> seen.add(id + ":" + text));
    CarbonChatEvent event = chat(carbonPlayer(alex), "global", "ignored");
    when(event.originalMessage())
        .thenReturn(Component.text("hello ", NamedTextColor.RED).append(Component.text("there")));

    events.emit(event);

    assertEquals(List.of(alex.getUniqueId() + ":hello there"), seen);
  }

  @Test
  void theChatFilterAlsoSeesPlayersWhoAreNotOnTheServer() {
    carbonPresent();
    UUID stranger = UUID.randomUUID();
    List<UUID> seen = new ArrayList<>();
    new CarbonChatIntegration().initChatFilter((id, text) -> seen.add(id));

    events.emit(chat(carbonPlayer(stranger), "global", "hello"));

    assertEquals(List.of(stranger), seen);
  }

  @Test
  void theChatFilterIsOnlySubscribedOnce() {
    carbonPresent();
    CarbonChatIntegration integration = new CarbonChatIntegration();

    integration.initChatFilter((id, text) -> true);
    integration.initChatFilter((id, text) -> false);

    assertEquals(1, events.active(CarbonChatEvent.class));
    CarbonChatEvent event = chat(carbonPlayer(alex), "global", "hello");
    events.emit(event);
    verify(event).cancelled(true);
  }

  @Test
  void theChatFilterWarnsWhenCarbonChatHasNotLoadedItsApi() {
    IntegrationTestUtils.installPlugin("CarbonChat");

    new CarbonChatIntegration().initChatFilter((id, text) -> true);

    assertEquals(
        List.of("Failed to hook into CarbonChat: CarbonChat not initialized!"),
        log.messages(Level.WARNING));
  }

  @Test
  void aFailedChatFilterCanBeTriedAgain() {
    carbonPresent();
    events.failure = new IllegalStateException("closed");
    CarbonChatIntegration integration = new CarbonChatIntegration();
    integration.initChatFilter((id, text) -> true);
    events.failure = null;

    integration.initChatFilter((id, text) -> true);

    assertEquals(1, events.active(CarbonChatEvent.class));
  }

  @Test
  void theChatFilterAndTheChannelRestrictionWorkSideBySide() {
    CarbonChatIntegration integration = initialised();
    integration.initChatFilter((id, text) -> text.equals("spam"));
    hidden.add(alex);
    CarbonChatEvent spam = chat(carbonPlayer(sam), "global", "spam");

    events.emit(spam);

    assertEquals(2, events.active(CarbonChatEvent.class));
    verify(spam).cancelled(true);
  }

  // Cleaning up

  @Test
  void cleanupDisposesTheChatAndFilterSubscriptions() {
    CarbonChatIntegration integration = initialised();
    integration.initChatFilter((id, text) -> true);

    integration.cleanup();

    assertEquals(0, events.active(CarbonChatEvent.class));
    assertTrue(events.subscriptions.stream().allMatch(s -> s.disposed));
  }

  @Test
  void cleanupDisposesThePrivateMessageSubscription() {
    CarbonChatIntegration integration = withPrivateMessages();

    integration.cleanup();

    assertEquals(0, events.active(CarbonPrivateChatEvent.class));
    assertEquals(0, events.active(CarbonChatEvent.class));
  }

  @Test
  void cleanupBeforeInitAndTwiceIsHarmless() {
    new CarbonChatIntegration().cleanup();
    CarbonChatIntegration integration = initialised();

    integration.cleanup();
    integration.cleanup();

    assertEquals(0, events.active(CarbonChatEvent.class));
  }

  @Test
  void afterCleanupTheChatFilterCanBeSubscribedAgain() {
    carbonPresent();
    CarbonChatIntegration integration = new CarbonChatIntegration();
    integration.initChatFilter((id, text) -> true);
    integration.cleanup();

    integration.initChatFilter((id, text) -> true);

    assertEquals(1, events.active(CarbonChatEvent.class));
  }

  @Test
  void afterCleanupPrivateMessagesNeedInitAgain() {
    CarbonChatIntegration integration = withPrivateMessages();
    integration.cleanup();

    integration.initPrivateMessage(this::isHidden, this::canBypass, blocked());

    assertEquals(0, events.active(CarbonPrivateChatEvent.class));
    assertEquals(
        List.of("CarbonChat private message interception skipped: integration not initialised."),
        log.messages(Level.WARNING));
  }

  @Test
  void theStateNameGoesIntoTheBlockedMessageOfTheChatRestriction() {
    carbonPresent();
    new CarbonChatIntegration().init(PREFIX, this::isHidden, "vanished");
    hidden.add(sam);

    events.emit(chat(carbonPlayer(sam), "global", "hello"));

    assertEquals("You cannot use chat while vanished.", MessageTestUtils.nextMessage(sam));
  }
}
