package com.nearvanilla.iceCream.modules.staffMode.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.testutil.AdvancementTestUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class StaffModeAdvancementCriterionGrantEventTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = new NamespacedKey("icecream", "staff_mode.enabled");
    server
        .getPluginManager()
        .registerEvents(
            new StaffModeAdvancementCriterionGrantEvent(), MockBukkit.createMockPlugin());
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
  }

  /** Adds a player with the given staff mode state, or without the key when null. */
  private PlayerMock player(Boolean staffModeEnabled) {
    PlayerMock player = AdvancementTestUtils.addPlayer(server, "Staff");
    if (staffModeEnabled != null) {
      player
          .getPersistentDataContainer()
          .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, staffModeEnabled);
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
  void cancelsVisibleAdvancementInStaffMode() {
    assertTrue(fire(player(true), true).isCancelled());
  }

  @Test
  void allowsHiddenAdvancementInStaffMode() {
    assertFalse(fire(player(true), false).isCancelled());
  }

  @Test
  void allowsVisibleAdvancementWhenStaffModeDisabled() {
    assertFalse(fire(player(false), true).isCancelled());
  }

  @Test
  void allowsVisibleAdvancementWhenKeyMissing() {
    assertFalse(fire(player(null), true).isCancelled());
  }
}
