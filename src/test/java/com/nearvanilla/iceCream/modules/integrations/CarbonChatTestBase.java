package com.nearvanilla.iceCream.modules.integrations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.IntegrationTestUtils;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.event.CarbonEvent;
import net.draycia.carbon.api.event.CarbonEventHandler;
import net.draycia.carbon.api.event.CarbonEventSubscriber;
import net.draycia.carbon.api.event.CarbonEventSubscription;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.draycia.carbon.api.event.events.CarbonPrivateChatEvent;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * What the CarbonChat integration tests share: a mock server, CarbonChat's API as Mockito mocks and
 * a small recording event handler that delivers the events the tests emit, and the players that
 * chat.
 */
abstract class CarbonChatTestBase {
  static final String PREFIX = "modules.spectator";
  static final String STATE = "in spectator mode";

  ServerMock server;
  LogCapture log;
  FakeEvents events;
  CarbonChat carbon;
  PlayerMock alex;
  PlayerMock sam;
  final Set<Player> hidden = new HashSet<>();
  final Set<Player> bypassing = new HashSet<>();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    log = LogCapture.of(IceCream.logger);
    events = new FakeEvents();
    carbon = mock(CarbonChat.class);
    when(carbon.eventHandler()).thenReturn(events);
    alex = server.addPlayer("Alex");
    sam = server.addPlayer("Sam");
  }

  @AfterEach
  void tearDown() {
    IntegrationTestUtils.registerCarbonChat(null);
    log.close();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  /** Replaces the configuration, which also replaces the logger the capture listens to. */
  void configure(String yaml) {
    log.close();
    IceCreamFixture.install(server, yaml);
    log = LogCapture.of(IceCream.logger);
  }

  void carbonPresent() {
    IntegrationTestUtils.installPlugin("CarbonChat");
    IntegrationTestUtils.registerCarbonChat(carbon);
  }

  CarbonChatIntegration initialised() {
    carbonPresent();
    CarbonChatIntegration integration = new CarbonChatIntegration();
    integration.init(PREFIX, this::isHidden, STATE);
    return integration;
  }

  CarbonChatIntegration withPrivateMessages() {
    CarbonChatIntegration integration = initialised();
    integration.initPrivateMessage(this::isHidden, this::canBypass, blocked());
    return integration;
  }

  // The checks refuse to look at a player that is not there, as the real ones would.
  boolean isHidden(Player player) {
    return hidden.contains(Objects.requireNonNull(player));
  }

  boolean canBypass(Player player) {
    return bypassing.contains(Objects.requireNonNull(player));
  }

  static Component blocked() {
    return Component.text("You cannot message them.", NamedTextColor.RED);
  }

  // Test doubles for CarbonChat's events

  static CarbonPlayer carbonPlayer(Player player) {
    CarbonPlayer carbonPlayer = mock(CarbonPlayer.class);
    when(carbonPlayer.uuid()).thenReturn(player.getUniqueId());
    return carbonPlayer;
  }

  static CarbonPlayer carbonPlayer(UUID id) {
    CarbonPlayer carbonPlayer = mock(CarbonPlayer.class);
    when(carbonPlayer.uuid()).thenReturn(id);
    return carbonPlayer;
  }

  static CarbonChatEvent chat(CarbonPlayer sender, String channel, String message) {
    CarbonChatEvent event = mock(CarbonChatEvent.class);
    ChatChannel chatChannel = mock(ChatChannel.class);
    when(chatChannel.key()).thenReturn(Key.key("carbon", channel));
    when(event.sender()).thenReturn(sender);
    when(event.chatChannel()).thenReturn(chatChannel);
    when(event.originalMessage()).thenReturn(Component.text(message));
    return event;
  }

  static CarbonPrivateChatEvent privateChat(CarbonPlayer sender, CarbonPlayer recipient) {
    CarbonPrivateChatEvent event = mock(CarbonPrivateChatEvent.class);
    when(event.sender()).thenReturn(sender);
    when(event.recipient()).thenReturn(recipient);
    return event;
  }

  /** Records subscriptions and delivers the events that tests emit to them. */
  static final class FakeEvents implements CarbonEventHandler {
    final List<FakeSubscription<?>> subscriptions = new ArrayList<>();
    RuntimeException failure;

    @Override
    public <T extends CarbonEvent> CarbonEventSubscription<T> subscribe(
        Class<T> type, CarbonEventSubscriber<T> subscriber) {
      if (failure != null) throw failure;
      FakeSubscription<T> subscription = new FakeSubscription<>(type, subscriber);
      subscriptions.add(subscription);
      return subscription;
    }

    @Override
    public <T extends CarbonEvent> CarbonEventSubscription<T> subscribe(
        Class<T> type, int priority, boolean ignoreCancelled, CarbonEventSubscriber<T> subscriber) {
      return subscribe(type, subscriber);
    }

    @Override
    public <T extends CarbonEvent> void emit(T event) {
      for (FakeSubscription<?> subscription : List.copyOf(subscriptions)) {
        if (!subscription.disposed && subscription.type.isInstance(event)) {
          subscription.deliver(event);
        }
      }
    }

    int active(Class<? extends CarbonEvent> type) {
      return (int) subscriptions.stream().filter(s -> s.type == type && !s.disposed).count();
    }
  }

  static final class FakeSubscription<T extends CarbonEvent> implements CarbonEventSubscription<T> {
    final Class<T> type;
    final CarbonEventSubscriber<T> subscriber;
    boolean disposed;

    FakeSubscription(Class<T> type, CarbonEventSubscriber<T> subscriber) {
      this.type = type;
      this.subscriber = subscriber;
    }

    @SuppressWarnings("unchecked")
    void deliver(CarbonEvent event) {
      try {
        subscriber.on((T) event);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public Class<T> event() {
      return type;
    }

    @Override
    public CarbonEventSubscriber<T> subscriber() {
      return subscriber;
    }

    @Override
    public void dispose() {
      disposed = true;
    }
  }
}
