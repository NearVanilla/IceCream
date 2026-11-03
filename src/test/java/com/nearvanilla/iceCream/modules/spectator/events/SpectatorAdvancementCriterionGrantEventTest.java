package com.nearvanilla.iceCream.modules.spectator.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.testutil.AdvancementTestUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SpectatorAdvancementCriterionGrantEventTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    server
        .getPluginManager()
        .registerEvents(
            new SpectatorAdvancementCriterionGrantEvent(), MockBukkit.createMockPlugin());
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
  }

  /** Adds a player with the given spectator state, or without the key when null. */
  private PlayerMock player(Boolean spectatorEnabled) {
    PlayerMock player = AdvancementTestUtils.addPlayer(server, "Spectator");
    if (spectatorEnabled != null) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, spectatorEnabled);
    }
    return player;
  }

  private PlayerAdvancementCriterionGrantEvent fire(PlayerMock player, boolean visible) {
    PlayerAdvancementCriterionGrantEvent event =
        new PlayerAdvancementCriterionGrantEvent(
            player, AdvancementTestUtils.advancement(visible), "criterion");
    server.getPluginManager().callEvent(event);
    return event;
  }

  @Test
  void cancelsVisibleAdvancementInSpectatorMode() {
    assertTrue(fire(player(true), true).isCancelled());
  }

  @Test
  void allowsHiddenAdvancementInSpectatorMode() {
    assertFalse(fire(player(true), false).isCancelled());
  }

  @Test
  void allowsVisibleAdvancementWhenSpectatorDisabled() {
    assertFalse(fire(player(false), true).isCancelled());
  }

  @Test
  void allowsVisibleAdvancementWhenKeyMissing() {
    assertFalse(fire(player(null), true).isCancelled());
  }
}
