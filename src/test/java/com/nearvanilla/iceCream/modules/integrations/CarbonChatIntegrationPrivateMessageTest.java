package com.nearvanilla.iceCream.modules.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.draycia.carbon.api.event.CarbonEventSubscriber;
import net.draycia.carbon.api.event.events.CarbonPrivateChatEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** The CarbonChat integration's interception of private messages with a hidden player. */
class CarbonChatIntegrationPrivateMessageTest extends CarbonChatTestBase {
  // Private messages

  @Test
  void privateMessagesAreSkippedSilentlyWhenCarbonChatIsNotInstalled() {
    new CarbonChatIntegration().initPrivateMessage(this::isHidden, this::canBypass, blocked());

    assertTrue(events.subscriptions.isEmpty());
    assertTrue(
        log.records().stream().noneMatch(r -> r.getLevel().intValue() >= Level.INFO.intValue()));
  }

  @Test
  void privateMessagesWarnWhenTheIntegrationWasNotInitialised() {
    carbonPresent();

    new CarbonChatIntegration().initPrivateMessage(this::isHidden, this::canBypass, blocked());

    assertEquals(
        List.of("CarbonChat private message interception skipped: integration not initialised."),
        log.messages(Level.WARNING));
    assertEquals(0, events.active(CarbonPrivateChatEvent.class));
  }

  @Test
  void privateMessagesAreSubscribedOnce() {
    CarbonChatIntegration integration = withPrivateMessages();

    integration.initPrivateMessage(this::isHidden, this::canBypass, blocked());

    assertEquals(1, events.active(CarbonPrivateChatEvent.class));
  }

  @Test
  void privateMessagesWarnWhenSubscribingFails() {
    CarbonChatIntegration integration = initialised();
    events.failure = new IllegalStateException("closed");

    integration.initPrivateMessage(this::isHidden, this::canBypass, blocked());

    assertEquals(
        List.of("Failed to hook into CarbonChat private messages: closed"),
        log.messages(Level.WARNING));
    assertEquals(0, events.active(CarbonPrivateChatEvent.class));
  }

  @Test
  void aPrivateMessageToAHiddenPlayerIsBlocked() {
    withPrivateMessages();
    hidden.add(sam);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    verify(event).cancelled(true);
    assertEquals(blocked(), alex.nextComponentMessage());
    assertNull(alex.nextMessage());
    assertNull(sam.nextMessage());
  }

  @Test
  void aPrivateMessageFromAHiddenPlayerIsBlocked() {
    withPrivateMessages();
    hidden.add(alex);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    verify(event).cancelled(true);
    assertEquals(blocked(), alex.nextComponentMessage());
  }

  @Test
  void aPrivateMessageBetweenVisiblePlayersIsLeftAlone() {
    withPrivateMessages();
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @Test
  void aSenderWhoMayBypassCanMessageAHiddenPlayer() {
    withPrivateMessages();
    hidden.add(sam);
    bypassing.add(alex);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @Test
  void aHiddenPlayerCanMessageSomeoneWhoMayBypass() {
    withPrivateMessages();
    hidden.add(alex);
    bypassing.add(sam);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @Test
  void bypassingOnlyHelpsTheSideThatHasIt() {
    withPrivateMessages();
    hidden.add(alex);
    hidden.add(sam);
    bypassing.add(alex);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    // Alex may reach the hidden Sam, but Alex is hidden too and Sam cannot bypass.
    verify(event).cancelled(true);
    assertEquals(blocked(), alex.nextComponentMessage());
  }

  @Test
  void aHiddenPlayerCannotMessageSomeoneWhoIsNotOnTheServer() {
    withPrivateMessages();
    hidden.add(alex);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(UUID.randomUUID()));

    events.emit(event);

    verify(event).cancelled(true);
    assertEquals(blocked(), alex.nextComponentMessage());
  }

  @Test
  void aVisiblePlayerMayMessageSomeoneWhoIsNotOnTheServer() {
    withPrivateMessages();
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(UUID.randomUUID()));

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
  }

  @Test
  void aPrivateMessageFromSomeoneWhoIsNotOnTheServerIsIgnored() {
    withPrivateMessages();
    hidden.add(sam);
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(UUID.randomUUID()), carbonPlayer(sam));

    events.emit(event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(sam.nextMessage());
  }

  @Test
  void privateMessagesUseTheChecksGivenToThem() {
    CarbonChatIntegration integration = initialised();
    hidden.add(sam);
    Set<Player> otherHidden = new HashSet<>();
    integration.initPrivateMessage(otherHidden::contains, this::canBypass, blocked());
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    events.emit(event);

    // Sam is hidden for chat, but the private message check says nobody is.
    verify(event, never()).cancelled(anyBoolean());
  }

  @Test
  void aMessageThatArrivesAfterCleanupIsIgnored() {
    CarbonChatIntegration integration = withPrivateMessages();
    hidden.add(sam);
    CarbonEventSubscriber<CarbonPrivateChatEvent> late =
        events.subscriptions.stream()
            .filter(s -> s.type == CarbonPrivateChatEvent.class)
            .map(FakeSubscription::subscriber)
            .map(this::asPrivateSubscriber)
            .findFirst()
            .orElseThrow();
    integration.cleanup();
    CarbonPrivateChatEvent event = privateChat(carbonPlayer(alex), carbonPlayer(sam));

    assertDoesNotThrowOn(late, event);

    verify(event, never()).cancelled(anyBoolean());
    assertNull(alex.nextMessage());
  }

  @SuppressWarnings("unchecked")
  private CarbonEventSubscriber<CarbonPrivateChatEvent> asPrivateSubscriber(
      CarbonEventSubscriber<?> subscriber) {
    return (CarbonEventSubscriber<CarbonPrivateChatEvent>) subscriber;
  }

  private static void assertDoesNotThrowOn(
      CarbonEventSubscriber<CarbonPrivateChatEvent> subscriber, CarbonPrivateChatEvent event) {
    try {
      subscriber.on(event);
    } catch (Throwable e) {
      throw new AssertionError("The late event threw " + e, e);
    }
  }
}
