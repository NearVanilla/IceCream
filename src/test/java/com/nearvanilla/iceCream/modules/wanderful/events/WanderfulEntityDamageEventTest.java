package com.nearvanilla.iceCream.modules.wanderful.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class WanderfulEntityDamageEventTest {
  private ServerMock server;
  private World world;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    server.getPluginManager().registerEvents(new WanderfulEntityDamageEvent(), IceCream.instance);
    world = server.addSimpleWorld("world");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private <T extends Entity> T spawn(Class<T> type) {
    return world.spawn(new Location(world, 0, 64, 0), type);
  }

  // Paper marks this constructor for removal, but it is the simplest one that takes a DamageSource.
  @SuppressWarnings("removal")
  private void damage(Entity entity) {
    server
        .getPluginManager()
        .callEvent(
            new EntityDamageEvent(
                entity,
                DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.GENERIC).build(),
                1.0));
  }

  @Test
  void damagedInvisibleItemFrameBecomesVisible() {
    ItemFrame frame = spawn(ItemFrame.class);
    frame.setVisible(false);

    damage(frame);

    assertTrue(frame.isVisible());
  }

  @Test
  void damagedInvisibleGlowItemFrameBecomesVisible() {
    GlowItemFrame frame = spawn(GlowItemFrame.class);
    frame.setVisible(false);

    damage(frame);

    assertTrue(frame.isVisible());
  }

  @Test
  void damagedVisibleItemFrameStaysVisible() {
    ItemFrame frame = spawn(ItemFrame.class);

    damage(frame);

    assertTrue(frame.isVisible());
  }

  @Test
  void otherDamagedEntitiesAreLeftAlone() {
    ArmorStand armorStand = spawn(ArmorStand.class);
    armorStand.setVisible(false);

    damage(armorStand);

    assertFalse(armorStand.isVisible());
  }

  @Test
  void aFrameThatWasNotDamagedStaysInvisible() {
    ItemFrame frame = spawn(ItemFrame.class);
    ItemFrame other = spawn(ItemFrame.class);
    frame.setVisible(false);
    other.setVisible(false);

    damage(other);

    assertFalse(frame.isVisible());
    assertTrue(other.isVisible());
  }
}
