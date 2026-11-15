package com.nearvanilla.iceCream.modules.spectator.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.spectator.AutoSpectator;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class SpectatorCommandTest {
  private ServerMock server;
  private MockedStatic<SpectatorUtils> spectatorUtils;
  private final SpectatorCommand command = new SpectatorCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
    SpectatorModule.AUTO_SPECTATOR_KEY = new NamespacedKey("icecream", "spectator.auto_enabled");
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = new NamespacedKey("icecream", "staff_mode.enabled");
    AutoSpectator.configure(true, 30);
    // Entering and leaving spectator mode talk to the plugin instance, DiscordSRV and the proxy,
    // none of which exist here, so those two calls are replaced. The state check stays real.
    spectatorUtils = mockStatic(SpectatorUtils.class);
    spectatorUtils.when(() -> SpectatorUtils.isInSpectatorMode(any())).thenCallRealMethod();
  }

  @AfterEach
  void tearDown() {
    spectatorUtils.close();
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
    SpectatorModule.AUTO_SPECTATOR_KEY = null;
    StaffModeModule.STAFF_MODE_TOGGLE_KEY = null;
  }

  private PlayerMock player(boolean inStaffMode, boolean spectating) {
    PlayerMock player = server.addPlayer("Staff");
    if (inStaffMode) {
      player
          .getPersistentDataContainer()
          .set(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    }
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
    command.spectatorCommand(source);
  }

  private void auto(PlayerMock player) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(player);
    command.autoCommand(source);
  }

  @Test
  void blocksEnteringSpectatorWhileInStaffMode() {
    PlayerMock player = player(true, false);

    toggle(player);

    spectatorUtils.verify(() -> SpectatorUtils.enableSpectator(player), never());
    assertEquals(command.spectatorBlockedByStaffMode, player.nextComponentMessage());
  }

  @Test
  void allowsLeavingSpectatorWhileInStaffMode() {
    // A player who was already in both modes before the block existed cannot use /staffmode while
    // spectating, so leaving spectator has to stay open or they are stuck.
    PlayerMock player = player(true, true);

    toggle(player);

    spectatorUtils.verify(() -> SpectatorUtils.disableSpectator(player));
  }

  @Test
  void entersSpectatorWhenNotInStaffMode() {
    PlayerMock player = player(false, false);

    toggle(player);

    spectatorUtils.verify(() -> SpectatorUtils.enableSpectator(player));
  }

  @Test
  void autoCommandTurnsOnThenOff() {
    PlayerMock player = player(false, false);

    auto(player);
    assertEquals(command.autoSpectatorEnabledMessage(), player.nextComponentMessage());
    assertTrue(AutoSpectator.isAutoEnabled(player));

    auto(player);
    assertEquals(command.autoSpectatorDisabled, player.nextComponentMessage());
    assertFalse(AutoSpectator.isAutoEnabled(player));
  }

  @Test
  void autoCommandWarnsWhenFeatureIsOff() {
    AutoSpectator.configure(false, 30);
    PlayerMock player = player(false, false);

    auto(player);

    assertEquals(command.autoSpectatorEnabledMessage(), player.nextComponentMessage());
    assertEquals(command.autoSpectatorFeatureOff, player.nextComponentMessage());
  }

  @Test
  void autoCommandDoesNotChangeSpectatorMode() {
    PlayerMock player = player(false, false);

    auto(player);

    spectatorUtils.verify(() -> SpectatorUtils.enableSpectator(any()), never());
    spectatorUtils.verify(() -> SpectatorUtils.disableSpectator(any()), never());
  }
}
