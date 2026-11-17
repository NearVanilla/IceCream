package com.nearvanilla.iceCream.modules.phantomToggle.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleModule;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PigMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PhantomTogglePreSpawnEventTest {
  private ServerMock server;
  private PlayerMock player;
  private final PhantomTogglePreSpawnEvent listener = new PhantomTogglePreSpawnEvent();

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

  private PhantomPreSpawnEvent spawnFor(Entity spawningEntity) {
    PhantomPreSpawnEvent event =
        new PhantomPreSpawnEvent(
            new Location(null, 0, 64, 0), spawningEntity, CreatureSpawnEvent.SpawnReason.NATURAL);
    listener.onPhantomPreSpawn(event);
    return event;
  }

  @Test
  void cancelsTheSpawnForAPlayerWithPhantomsDisabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertTrue(spawnFor(player).isCancelled());
  }

  @Test
  void allowsTheSpawnForAPlayerWithNoPreference() {
    assertFalse(spawnFor(player).isCancelled());
  }

  @Test
  void allowsTheSpawnForAPlayerWhoEnabledPhantomsAgain() {
    PhantomToggleUtils.setPhantomDisabled(player, true);
    PhantomToggleUtils.setPhantomDisabled(player, false);

    assertFalse(spawnFor(player).isCancelled());
  }

  @Test
  void allowsTheSpawnWhenTheSpawningEntityIsNotAPlayer() {
    assertFalse(spawnFor(new PigMock(server, UUID.randomUUID())).isCancelled());
  }

  @Test
  void allowsTheSpawnForAPlayerWhenAnotherPlayerHasPhantomsDisabled() {
    PlayerMock other = server.addPlayer("Other");
    PhantomToggleUtils.setPhantomDisabled(other, true);

    assertFalse(spawnFor(player).isCancelled());
  }
}
