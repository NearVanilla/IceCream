package com.nearvanilla.iceCream.modules.wanderful.events;

import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * WanderfulEntityDamageEvent is an event listener that listens for "EntityDamageEvent" and makes
 * item frames visible when they are damaged.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-09-21
 */
public class WanderfulEntityDamageEvent implements Listener {
  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityDamageEvent(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof ItemFrame itemFrame)) return;

    itemFrame.setVisible(true);
  }
}
