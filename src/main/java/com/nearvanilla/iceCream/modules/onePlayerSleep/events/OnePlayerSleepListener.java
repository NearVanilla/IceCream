package com.nearvanilla.iceCream.modules.onePlayerSleep.events;

import com.nearvanilla.iceCream.modules.onePlayerSleep.OnePlayerSleepModule;
import io.papermc.paper.block.bed.BedRuleResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class OnePlayerSleepListener implements Listener {
  private static final Component SLEEP_PAUSED =
      Component.text(
          "Sleeping is temporarily paused because someone wants to keep the night.",
          NamedTextColor.GRAY);
  private final OnePlayerSleepModule module;

  public OnePlayerSleepListener(OnePlayerSleepModule module) {
    this.module = module;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void denyDuringCooldown(PlayerBedEnterEvent event) {
    if (module.isCoolingDown()) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(SLEEP_PAUSED);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void confirmEntry(PlayerBedEnterEvent event) {
    if (event.useBed() == Event.Result.ALLOW
        || (event.useBed() == Event.Result.DEFAULT
            && event.enterAction().problem() == null
            && event.enterAction().canSleep() == BedRuleResult.ALLOWED)) {
      module.queueSleepCheck(event.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void leaveBed(PlayerBedLeaveEvent event) {
    module.forgetPlayer(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void quit(PlayerQuitEvent event) {
    module.forgetPlayer(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void die(PlayerDeathEvent event) {
    module.forgetPlayer(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void changeWorld(PlayerChangedWorldEvent event) {
    module.forgetPlayer(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void unloadWorld(WorldUnloadEvent event) {
    module.forgetWorld(event.getWorld().getUID());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void preventNightSkip(TimeSkipEvent event) {
    if (event.getSkipReason() == ClockTimeSkipEvent.SkipReason.NIGHT_SKIP
        && module.shouldBlockNightSkip(event.getWorld())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void observeWorldTime(TimeSkipEvent event) {
    module.observeTimeChange(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void observeClockTime(ClockTimeSkipEvent event) {
    module.observeTimeChange(event);
  }
}
