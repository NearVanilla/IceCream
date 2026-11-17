package com.nearvanilla.iceCream.modules.phantomToggle.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleModule;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PhantomMock;
import org.mockbukkit.mockbukkit.entity.PigMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

class PhantomToggleTargetEventTest {
  private ServerMock server;
  private PlayerMock player;
  private final PhantomToggleTargetEvent listener = new PhantomToggleTargetEvent();

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

  private PhantomMock phantom() {
    return new PhantomMock(server, UUID.randomUUID());
  }

  private EntityTargetLivingEntityEvent targeting(Entity attacker, LivingEntity target) {
    EntityTargetLivingEntityEvent event =
        new EntityTargetLivingEntityEvent(
            attacker, target, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    listener.onPhantomTarget(event);
    return event;
  }

  @Test
  void cancelsAPhantomTargetingAPlayerWithPhantomsDisabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertTrue(targeting(phantom(), player).isCancelled());
  }

  @Test
  void allowsAPhantomTargetingAPlayerWithNoPreference() {
    assertFalse(targeting(phantom(), player).isCancelled());
  }

  @Test
  void allowsAPhantomTargetingAPlayerWhoEnabledPhantomsAgain() {
    PhantomToggleUtils.setPhantomDisabled(player, true);
    PhantomToggleUtils.setPhantomDisabled(player, false);

    assertFalse(targeting(phantom(), player).isCancelled());
  }

  @Test
  void allowsOtherMobsTargetingAPlayerWithPhantomsDisabled() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertFalse(targeting(new ZombieMock(server, UUID.randomUUID()), player).isCancelled());
  }

  @Test
  void allowsAPhantomTargetingAnotherKindOfEntity() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertFalse(targeting(phantom(), new PigMock(server, UUID.randomUUID())).isCancelled());
  }

  @Test
  void allowsAPhantomLosingItsTarget() {
    PhantomToggleUtils.setPhantomDisabled(player, true);

    assertFalse(targeting(phantom(), null).isCancelled());
  }

  @Test
  void allowsAPhantomTargetingAPlayerWhenAnotherPlayerHasPhantomsDisabled() {
    PlayerMock other = server.addPlayer("Other");
    PhantomToggleUtils.setPhantomDisabled(other, true);

    assertFalse(targeting(phantom(), player).isCancelled());
  }
}
