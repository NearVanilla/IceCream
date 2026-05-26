package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

public class LecternTakeBookEvent implements Listener {

  @EventHandler
  public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
    if (LecternUtils.isReadOnly(event.getLectern().getBlock())) {
      event.setCancelled(true);
    }
  }
}
