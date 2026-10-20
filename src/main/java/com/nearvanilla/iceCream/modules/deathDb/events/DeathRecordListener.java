package com.nearvanilla.iceCream.modules.deathDb.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.deathDb.InventorySnapshot;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Records every player death. The inventory is snapshotted on the server thread at MONITOR so it
 * reflects the final state other plugins left, then the insert runs on the repository thread.
 */
public class DeathRecordListener implements Listener {
  private final DeathRepository repository;

  public DeathRecordListener(DeathRepository repository) {
    this.repository = repository;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    try {
      Location location = player.getLocation();
      byte[] inventory =
          InventorySnapshot.serialize(InventorySnapshot.capture(player.getInventory()));
      repository
          .insert(
              player.getUniqueId(),
              System.currentTimeMillis(),
              location.getWorld().getName(),
              location.getX(),
              location.getY(),
              location.getZ(),
              inventory)
          .exceptionally(
              error -> {
                IceCream.logger.log(
                    Level.SEVERE, "Failed to store the death of " + player.getName(), error);
                return null;
              });
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to record the death of " + player.getName(), e);
    }
  }
}
