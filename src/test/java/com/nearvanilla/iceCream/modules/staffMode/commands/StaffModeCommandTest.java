package com.nearvanilla.iceCream.modules.staffMode.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class StaffModeCommandTest {
  private ServerMock server;
  private final StaffModeCommand command = new StaffModeCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = new NamespacedKey("icecream", "staff_mode.enabled");
    StaffModeModule.STAFF_MODE_INVENTORY_KEY =
        new NamespacedKey("icecream", "staff_mode.inventory");
    StaffModeModule.STAFF_MODE_CONFIRM_KEY = new NamespacedKey("icecream", "staff_mode.confirm");
    StaffModeModule.STAFF_MODE_LOCATION_WORLD = new NamespacedKey("icecream", "staff_mode.world");
    StaffModeModule.STAFF_MODE_LOCATION_X = new NamespacedKey("icecream", "staff_mode.x");
    StaffModeModule.STAFF_MODE_LOCATION_Y = new NamespacedKey("icecream", "staff_mode.y");
    StaffModeModule.STAFF_MODE_LOCATION_Z = new NamespacedKey("icecream", "staff_mode.z");
    StaffModeModule.STAFF_MODE_LOCATION_YAW = new NamespacedKey("icecream", "staff_mode.yaw");
    StaffModeModule.STAFF_MODE_LOCATION_PITCH = new NamespacedKey("icecream", "staff_mode.pitch");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
    StaffModeModule.STAFF_MODE_INVENTORY_KEY = null;
    StaffModeModule.STAFF_MODE_CONFIRM_KEY = null;
    StaffModeModule.STAFF_MODE_LOCATION_WORLD = null;
    StaffModeModule.STAFF_MODE_LOCATION_X = null;
    StaffModeModule.STAFF_MODE_LOCATION_Y = null;
    StaffModeModule.STAFF_MODE_LOCATION_Z = null;
    StaffModeModule.STAFF_MODE_LOCATION_YAW = null;
    StaffModeModule.STAFF_MODE_LOCATION_PITCH = null;
  }

  private PlayerMock player(boolean spectating) {
    PlayerMock player = server.addPlayer("Staff");
    player.getInventory().addItem(new ItemStack(Material.DIRT, 8));
    if (spectating) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    }
    return player;
  }

  private void toggle(PlayerMock player) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(player);
    command.staffModeCommand(source);
  }

  @Test
  void blocksEnablingStaffModeWhileSpectating() {
    PlayerMock player = player(true);

    toggle(player);

    assertFalse(StaffModeUtils.isInStaffMode(player));
    assertTrue(player.getInventory().contains(Material.DIRT));
  }

  @Test
  void blocksDisablingStaffModeWhileSpectating() {
    PlayerMock player = player(true);
    player
        .getPersistentDataContainer()
        .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);

    toggle(player);

    assertTrue(StaffModeUtils.isInStaffMode(player));
  }

  @Test
  void enablesStaffModeWhenNotSpectating() {
    PlayerMock player = player(false);

    toggle(player);

    assertTrue(StaffModeUtils.isInStaffMode(player));
    assertFalse(player.getInventory().contains(Material.DIRT));
  }
}
