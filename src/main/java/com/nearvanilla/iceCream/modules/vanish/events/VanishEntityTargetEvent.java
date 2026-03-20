package com.nearvanilla.iceCream.modules.vanish.events;

import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class VanishEntityTargetEvent implements Listener {

  @EventHandler
  public void onEntityTarget(EntityTargetLivingEntityEvent event) {
    if (!(event.getTarget() instanceof Player player)) return;
    if (VanishUtils.isVanished(player)) {
      event.setCancelled(true);
    }
  }
}
