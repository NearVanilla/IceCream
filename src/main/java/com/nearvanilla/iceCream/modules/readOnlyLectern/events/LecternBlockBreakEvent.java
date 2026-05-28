package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class LecternBlockBreakEvent implements Listener {

  private static final Component READ_ONLY_MESSAGE =
      LecternUtils.buildMessage("<red>This lectern is read-only.</red>");

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    if (LecternUtils.isReadOnly(event.getBlock())) {
      event.setCancelled(true);
      if (event.getPlayer() != null) {
        event.getPlayer().sendMessage(READ_ONLY_MESSAGE);
      }
    }
  }
}
