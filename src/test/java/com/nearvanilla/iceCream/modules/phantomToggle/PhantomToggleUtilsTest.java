package com.nearvanilla.iceCream.modules.phantomToggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PhantomToggleUtilsTest {
  private ServerMock server;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    PhantomToggleModule.PHANTOM_DISABLED_KEY =
        new NamespacedKey(IceCream.instance, "phantomtoggle.disabled");
    player = server.addPlayer("Sleeper");
  }

  @AfterEach
  void tearDown() {
    PhantomToggleModule.PHANTOM_DISABLED_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Boolean stored(PlayerMock target) {
    return target
        .getPersistentDataContainer()
        .get(PhantomToggleModule.PHANTOM_DISABLED_KEY, PersistentDataType.BOOLEAN);
  }

  @Test
  void phantomsAreEnabledForAPlayerWithNoPreference() {
    assertFalse(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void checkingAPlayerWithNoPreferenceDoesNotStoreOne() {
    PhantomToggleUtils.isPhantomDisabled(player);

    assertFalse(player.getPersistentDataContainer().has(PhantomToggleModule.PHANTOM_DISABLED_KEY));
  }

  @Test
  void settingDisabledStoresTheKey() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertEquals(Boolean.TRUE, stored(player));
  }

  @Test
  void settingDisabledMakesThePlayerReadAsDisabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void settingEnabledStoresFalseAndReadsAsEnabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    PhantomToggleUtils.setPhantomDisabled(player, false);

    assertEquals(Boolean.FALSE, stored(player));
    assertFalse(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void settingEnabledOnAPlayerWithNoPreferenceReadsAsEnabled() {
    PhantomToggleUtils.setPhantomDisabled(player, false);

    assertFalse(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void settingDisabledTwiceKeepsThePlayerDisabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
  }

  @Test
  void thePreferenceBelongsToOnePlayer() {
    PlayerMock other = server.addPlayer("Other");

    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertTrue(PhantomToggleUtils.isPhantomDisabled(player));
    assertFalse(PhantomToggleUtils.isPhantomDisabled(other));
  }
}
