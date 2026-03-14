package com.nearvanilla.iceCream.modules.desertMobs.events;

import com.nearvanilla.iceCream.IceCream;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Listens for creature spawns and converts zombies/skeletons in desert biomes to Husks/Parched with
 * a configurable chance.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-02-26
 */
public class DesertMobsSpawnEvent implements Listener {

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    EntityType entityType = event.getEntityType();
    Location location = event.getLocation();
    World world = location.getWorld();

    if (world == null) return;

    // Check if the spawn location is in a desert biome
    Biome biome = world.getBiome(location);
    if (biome != Biome.DESERT) return;

    // Handle zombie -> husk conversion
    if (entityType == EntityType.ZOMBIE) {
      double chance =
          IceCream.config.getDouble("modules.desertmobs.spawn.zombie-to-husk-chance", 0.0);

      if (chance <= 0.0) return;

      if (ThreadLocalRandom.current().nextDouble() < chance) {
        event.setCancelled(true);
        world.spawnEntity(location, EntityType.HUSK, CreatureSpawnEvent.SpawnReason.NATURAL);
      }
      return;
    }

    // Handle skeleton -> parched conversion
    if (entityType == EntityType.SKELETON) {
      double chance =
          IceCream.config.getDouble("modules.desertmobs.spawn.skeleton-to-parched-chance", 0.0);

      if (chance <= 0.0) return;

      if (ThreadLocalRandom.current().nextDouble() < chance) {
        event.setCancelled(true);
        world.spawnEntity(location, EntityType.PARCHED, CreatureSpawnEvent.SpawnReason.NATURAL);
      }
    }
  }
}
