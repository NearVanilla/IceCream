package com.nearvanilla.iceCream.modules.spectator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SpectatorUtilsTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.HIDDEN_SINCE_KEY = new NamespacedKey("icecream", "spectator.hidden_since");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.HIDDEN_SINCE_KEY = null;
  }

  @Test
  void recordHiddenSinceSetsTimeWhenNoneStored() {
    PlayerMock player = server.addPlayer("Staff");

    SpectatorUtils.recordHiddenSince(player, 10L);

    assertEquals(10L, SpectatorUtils.getHiddenSince(player));
  }

  @Test
  void recordHiddenSinceKeepsAnEarlierStoredTime() {
    // Auto spectator stores the quit time before spectator mode is enabled on join, and enabling
    // must not replace it with the join time.
    PlayerMock player = server.addPlayer("Staff");
    player
        .getPersistentDataContainer()
        .set(SpectatorModule.HIDDEN_SINCE_KEY, PersistentDataType.LONG, 5L);

    SpectatorUtils.recordHiddenSince(player, 10L);

    assertEquals(5L, SpectatorUtils.getHiddenSince(player));
  }
}
