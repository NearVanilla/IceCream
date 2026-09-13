package com.nearvanilla.iceCream.modules.deathLocation.events;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Privately reports the death location without changing the public death message or drops. */
public class DeathLocationListener implements Listener {

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    Location location = player.getLocation();
    String coordinates =
        "<"
            + location.getBlockX()
            + ", "
            + location.getBlockY()
            + ", "
            + location.getBlockZ()
            + ">";
    player.sendMessage(
        MiniMessage.miniMessage()
            .deserialize(
                "<gray>You died at <gold><coordinates></gold></gray>",
                Placeholder.unparsed("coordinates", coordinates)));
  }
}
