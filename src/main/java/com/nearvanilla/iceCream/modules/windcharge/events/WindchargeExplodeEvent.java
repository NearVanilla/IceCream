package com.nearvanilla.iceCream.modules.windcharge.events;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class WindchargeExplodeEvent implements Listener {

  @EventHandler
  public void onExplode(EntityExplodeEvent event) {
    event.blockList().removeIf(block -> block.getType() == Material.PLAYER_HEAD);
  }
}
