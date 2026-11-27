package com.nearvanilla.iceCream.modules.playerStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.nearvanilla.iceCream.modules.playerStatus.events.PlayerStatusListener;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Spectator mode shows the player as gone, so they have no status. */
class PlayerStatusSpectatorTest {

  /** A player behind the proxy, recording the statuses it is sent. */
  private static class ProxiedPlayer extends PlayerMock {
    final List<String> received = new ArrayList<>();

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
      ByteArrayDataInput in = ByteStreams.newDataInput(message);
      in.readUTF();
      received.add(in.readUTF());
    }
  }

  private ProxiedPlayer player;
  private PlayerStatusManager manager;

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    player = new ProxiedPlayer(server);
    server.addPlayer(player);
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    manager = new PlayerStatusManager();
    server
        .getPluginManager()
        .registerEvents(new PlayerStatusListener(manager), MockBukkit.createMockPlugin());
  }

  @AfterEach
  void tearDown() {
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    MockBukkit.unmock();
  }

  private void spectate(boolean on) {
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, on);
  }

  /** Spectator mode sets its flag, then switches the gamemode. */
  private void enterSpectatorMode() {
    spectate(true);
    player.setGameMode(GameMode.SPECTATOR);
  }

  @Test
  void goingIntoSpectatorModeDropsTheStatus() {
    manager.set(player, PlayerStatus.STREAMING);
    enterSpectatorMode();

    assertNull(manager.status(player));
    assertEquals("", player.received.getLast());
  }

  @Test
  void leavingSpectatorModeDoesNotBringItBack() {
    manager.set(player, PlayerStatus.STREAMING);
    enterSpectatorMode();
    spectate(false);
    player.setGameMode(GameMode.SURVIVAL);

    assertNull(manager.status(player));
  }

  /** Only IceCream's spectator mode hides the player; the plain gamemode keeps the status. */
  @Test
  void plainSpectatorGamemodeKeepsTheStatus() {
    manager.set(player, PlayerStatus.STREAMING);
    player.setGameMode(GameMode.SPECTATOR);

    assertEquals(PlayerStatus.STREAMING, manager.status(player));
  }

  @Test
  void rejoiningInSpectatorModeSendsNoStatus() {
    spectate(true);
    manager.channelRegistered(player);

    assertEquals("", player.received.getLast());
  }
}
