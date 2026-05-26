package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import java.util.Iterator;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class LecternExplosionEvent implements Listener {

  @EventHandler
  public void onExplosion(EntityExplodeEvent event) {
    Iterator<Block> iterator = event.blockList().iterator();
    while (iterator.hasNext()) {
      Block block = iterator.next();
      if (LecternUtils.isReadOnly(block)) {
        iterator.remove();
      }
    }
  }
}
