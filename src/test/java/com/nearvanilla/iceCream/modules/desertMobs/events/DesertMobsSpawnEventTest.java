package com.nearvanilla.iceCream.modules.desertMobs.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parched;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DesertMobsSpawnEventTest {
  private static final String ALWAYS_CONVERT =
      """
      modules:
        desertmobs:
          spawn:
            zombie-to-husk-chance: 1.0
            skeleton-to-parched-chance: 1.0
      """;

  private ServerMock server;
  private World world;
  private Location desert;
  private Location plains;
  private final DesertMobsSpawnEvent listener = new DesertMobsSpawnEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    desert = new Location(world, 10, 64, 10);
    plains = new Location(world, 500, 64, 500);
    world.setBiome(desert, Biome.DESERT);
    world.setBiome(plains, Biome.PLAINS);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String yaml) {
    IceCreamFixture.install(server, yaml);
  }

  private CreatureSpawnEvent spawn(Class<? extends LivingEntity> type, Location where) {
    CreatureSpawnEvent event =
        new CreatureSpawnEvent(world.spawn(where, type), SpawnReason.NATURAL);
    listener.onCreatureSpawn(event);
    return event;
  }

  private <T extends LivingEntity> Collection<T> all(Class<T> type) {
    return world.getEntitiesByClass(type);
  }

  @Test
  void zombieInTheDesertBecomesAHuskWhenTheChanceIsCertain() {
    config(ALWAYS_CONVERT);

    CreatureSpawnEvent event = spawn(Zombie.class, desert);

    assertTrue(event.isCancelled());
    Collection<Husk> husks = all(Husk.class);
    assertEquals(1, husks.size());
    assertEquals(desert, husks.iterator().next().getLocation());
  }

  @Test
  void skeletonInTheDesertBecomesAParchedWhenTheChanceIsCertain() {
    config(ALWAYS_CONVERT);

    CreatureSpawnEvent event = spawn(Skeleton.class, desert);

    assertTrue(event.isCancelled());
    Collection<Parched> parched = all(Parched.class);
    assertEquals(1, parched.size());
    assertEquals(desert, parched.iterator().next().getLocation());
  }

  @Test
  void replacementsAreSpawnedWithTheNaturalReason() {
    config(ALWAYS_CONVERT);
    List<SpawnReason> reasons = new ArrayList<>();
    server
        .getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onSpawn(CreatureSpawnEvent event) {
                if (event.getEntityType() == EntityType.HUSK) {
                  reasons.add(event.getSpawnReason());
                }
              }
            },
            IceCream.instance);

    spawn(Zombie.class, desert);

    assertEquals(List.of(SpawnReason.NATURAL), reasons);
  }

  @Test
  void zombieStaysAZombieWhenTheChanceIsZero() {
    config("modules:\n  desertmobs:\n    spawn:\n      zombie-to-husk-chance: 0.0\n");

    CreatureSpawnEvent event = spawn(Zombie.class, desert);

    assertFalse(event.isCancelled());
    assertTrue(all(Husk.class).isEmpty());
  }

  @Test
  void skeletonStaysASkeletonWhenTheChanceIsZero() {
    config("modules:\n  desertmobs:\n    spawn:\n      skeleton-to-parched-chance: 0.0\n");

    CreatureSpawnEvent event = spawn(Skeleton.class, desert);

    assertFalse(event.isCancelled());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void negativeChancesNeverConvert() {
    config(
        """
        modules:
          desertmobs:
            spawn:
              zombie-to-husk-chance: -1.0
              skeleton-to-parched-chance: -0.5
        """);

    CreatureSpawnEvent zombie = spawn(Zombie.class, desert);
    CreatureSpawnEvent skeleton = spawn(Skeleton.class, desert);

    assertFalse(zombie.isCancelled());
    assertFalse(skeleton.isCancelled());
    assertTrue(all(Husk.class).isEmpty());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void chancesAboveOneAlwaysConvert() {
    config(
        """
        modules:
          desertmobs:
            spawn:
              zombie-to-husk-chance: 7.5
              skeleton-to-parched-chance: 2.0
        """);

    assertTrue(spawn(Zombie.class, desert).isCancelled());
    assertTrue(spawn(Skeleton.class, desert).isCancelled());
  }

  @Test
  void missingChancesDefaultToNeverConverting() {
    config("");

    assertFalse(spawn(Zombie.class, desert).isCancelled());
    assertFalse(spawn(Skeleton.class, desert).isCancelled());
    assertTrue(all(Husk.class).isEmpty());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void zombieAndSkeletonChancesAreIndependent() {
    config(
        """
        modules:
          desertmobs:
            spawn:
              zombie-to-husk-chance: 1.0
              skeleton-to-parched-chance: 0.0
        """);

    assertTrue(spawn(Zombie.class, desert).isCancelled());
    assertFalse(spawn(Skeleton.class, desert).isCancelled());
    assertEquals(1, all(Husk.class).size());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void mobsOutsideTheDesertAreLeftAlone() {
    config(ALWAYS_CONVERT);

    assertFalse(spawn(Zombie.class, plains).isCancelled());
    assertFalse(spawn(Skeleton.class, plains).isCancelled());
    assertTrue(all(Husk.class).isEmpty());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void otherMobsInTheDesertAreLeftAlone() {
    config(ALWAYS_CONVERT);

    CreatureSpawnEvent event = spawn(Creeper.class, desert);

    assertFalse(event.isCancelled());
    assertTrue(all(Husk.class).isEmpty());
    assertTrue(all(Parched.class).isEmpty());
  }

  @Test
  void fractionalChanceConvertsSomeZombiesButNotAll() {
    config("modules:\n  desertmobs:\n    spawn:\n      zombie-to-husk-chance: 0.5\n");

    int converted = 0;
    int trials = 200;
    for (int i = 0; i < trials; i++) {
      if (spawn(Zombie.class, desert).isCancelled()) {
        converted++;
      }
    }

    // With a fair coin the odds of all 200 trials landing on one side are about 2^-199.
    assertTrue(converted > 0, "no zombie was converted");
    assertTrue(converted < trials, "every zombie was converted");
    assertEquals(converted, all(Husk.class).size());
  }
}
