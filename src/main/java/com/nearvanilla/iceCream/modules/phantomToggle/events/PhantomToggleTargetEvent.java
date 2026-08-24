package com.nearvanilla.iceCream.modules.phantomToggle.events;

import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * PhantomToggleTargetEvent stops phantoms from targeting players who have disabled phantoms. This
 * covers phantoms that spawned before the player disabled them, or that spawned for someone else.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-18
 */
public class PhantomToggleTargetEvent implements Listener {

  /** Cancels the targeting when a phantom targets a player who has phantoms disabled. */
  @EventHandler
  public void onPhantomTarget(EntityTargetLivingEntityEvent event) {
    if (!(event.getEntity() instanceof Phantom)) {
      return;
    }
    if (!(event.getTarget() instanceof Player player)) {
      return;
    }
    if (PhantomToggleUtils.isPhantomDisabled(player)) {
      event.setCancelled(true);
    }
  }
}
