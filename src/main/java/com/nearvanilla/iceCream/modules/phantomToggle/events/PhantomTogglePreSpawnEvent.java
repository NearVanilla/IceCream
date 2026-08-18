package com.nearvanilla.iceCream.modules.phantomToggle.events;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * PhantomTogglePreSpawnEvent cancels phantom spawns for players who have disabled phantoms.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-18
 */
public class PhantomTogglePreSpawnEvent implements Listener {

  /** Cancels the spawn when the player the phantom would spawn for has phantoms disabled. */
  @EventHandler
  public void onPhantomPreSpawn(PhantomPreSpawnEvent event) {
    if (!(event.getSpawningEntity() instanceof Player player)) {
      return;
    }
    if (PhantomToggleUtils.isPhantomDisabled(player)) {
      event.setCancelled(true);
    }
  }
}
