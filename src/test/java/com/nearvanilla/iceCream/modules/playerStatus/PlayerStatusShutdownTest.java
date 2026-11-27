package com.nearvanilla.iceCream.modules.playerStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Stopping the module while the plugin shuts down must not try to send plugin messages. */
class PlayerStatusShutdownTest {

  /** A player behind the proxy, recording the status messages it is sent. */
  private static class ProxiedPlayer extends PlayerMock {
    final List<byte[]> received = new ArrayList<>();

    ProxiedPlayer(ServerMock server) {
      super(server, "Player", UUID.randomUUID());
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
      return Set.of(PlayerStatusManager.STATUS_CHANNEL);
    }

    @Override
    public void sendPluginMessage(
        @NotNull Plugin source, @NotNull String channel, byte @NotNull [] message) {
      received.add(message);
    }
  }

  private IceCream previousInstance;
  private IceCream plugin;
  private ProxiedPlayer player;
  private PlayerStatusManager manager;

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    player = new ProxiedPlayer(server);
    server.addPlayer(player);
    previousInstance = IceCream.instance;
    plugin = mock(IceCream.class);
    IceCream.instance = plugin;
    manager = new PlayerStatusManager();
    manager.set(player, PlayerStatus.STREAMING);
    player.received.clear();
  }

  @AfterEach
  void tearDown() {
    IceCream.instance = previousInstance;
    MockBukkit.unmock();
  }

  @Test
  void stopClearsStatusesWhileThePluginRuns() {
    when(plugin.isEnabled()).thenReturn(true);
    manager.stop();

    assertEquals(1, player.received.size());
  }

  @Test
  void stopSendsNothingWhileThePluginShutsDown() {
    when(plugin.isEnabled()).thenReturn(false);

    assertDoesNotThrow(manager::stop);
    assertEquals(0, player.received.size());
  }
}
