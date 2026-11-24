package com.nearvanilla.iceCream.modules.playerStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlayerStatusManagerTest {

  private final UUID playerId = UUID.randomUUID();
  private final Set<String> channels = new HashSet<>();
  private Player player;
  private PlayerStatusManager manager;

  @BeforeEach
  void setUp() {
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getListeningPluginChannels()).thenReturn(channels);
    channels.add(PlayerStatusManager.STATUS_CHANNEL);
    manager = new PlayerStatusManager();
  }

  private List<String> lastSent() {
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(player, atLeastOnce())
        .sendPluginMessage(any(), eq(PlayerStatusManager.STATUS_CHANNEL), captor.capture());
    return decode(captor.getValue());
  }

  private List<String> decode(byte[] data) {
    ByteArrayDataInput in = ByteStreams.newDataInput(data);
    assertEquals(playerId.toString(), in.readUTF());
    int count = in.readInt();
    String[] ids = new String[count];
    for (int i = 0; i < count; i++) ids[i] = in.readUTF();
    return List.of(ids);
  }

  @Test
  void encodeWritesUuidCountAndId() {
    assertEquals(List.of("afk"), decode(PlayerStatusManager.encode(playerId, PlayerStatus.AFK)));
    assertEquals(List.of(), decode(PlayerStatusManager.encode(playerId, null)));
  }

  @Test
  void setAndClearSendTheStatus() {
    assertTrue(manager.set(player, PlayerStatus.DO_NOT_DISTURB));
    assertEquals(List.of("do_not_disturb"), lastSent());

    manager.clear(player);
    assertEquals(List.of(), lastSent());
    assertNull(manager.status(player));
  }

  @Test
  void aNewStatusReplacesTheOldOne() {
    manager.set(player, PlayerStatus.DO_NOT_DISTURB);
    assertTrue(manager.set(player, PlayerStatus.STREAMING));

    assertEquals(PlayerStatus.STREAMING, manager.status(player));
    assertEquals(List.of("streaming"), lastSent());
  }

  @Test
  void settingTheSameStatusAgainKeepsItAndSendsNothing() {
    manager.set(player, PlayerStatus.STREAMING);

    assertFalse(manager.set(player, PlayerStatus.STREAMING));
    assertEquals(PlayerStatus.STREAMING, manager.status(player));
    verify(player, times(1))
        .sendPluginMessage(any(), eq(PlayerStatusManager.STATUS_CHANNEL), any());
  }

  @Test
  void clearingWithoutAStatusSendsNothing() {
    manager.clear(player);
    verify(player, never()).sendPluginMessage(any(), any(), any());
  }

  /** The first message after joining also clears anything the proxy still has for the player. */
  @Test
  void sendsTheStatusWhenTheProxyStartsListening() {
    channels.clear();
    manager.set(player, PlayerStatus.STREAMING);
    verify(player, never()).sendPluginMessage(any(), any(), any());

    channels.add(PlayerStatusManager.STATUS_CHANNEL);
    manager.channelRegistered(player);
    assertEquals(List.of("streaming"), lastSent());
  }

  @Test
  void sendsNoStatusWhenTheProxyStartsListeningWithoutOne() {
    manager.channelRegistered(player);
    assertEquals(List.of(), lastSent());
  }

  @Test
  void forgetDropsTheStatus() {
    manager.set(player, PlayerStatus.STREAMING);
    manager.forget(playerId);

    assertNull(manager.status(player));
  }
}
