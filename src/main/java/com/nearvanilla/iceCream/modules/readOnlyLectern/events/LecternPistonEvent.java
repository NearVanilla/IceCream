package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

public class LecternPistonEvent implements Listener {

  @EventHandler
  public void onPistonExtend(BlockPistonExtendEvent event) {
    for (org.bukkit.block.Block block : event.getBlocks()) {
      if (LecternUtils.isReadOnly(block)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void onPistonRetract(BlockPistonRetractEvent event) {
    for (org.bukkit.block.Block block : event.getBlocks()) {
      if (LecternUtils.isReadOnly(block)) {
        event.setCancelled(true);
        return;
      }
    }
  }
}
