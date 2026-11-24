package com.nearvanilla.iceCream.modules.tag.events;

import com.nearvanilla.iceCream.modules.tag.TagGame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.hermitcraft.tag.PlayerListener
 * Adaptations: "it" is shown again when a player logs in.
 */
/**
 * Tags players hit with the tag item, keeps the tag item from being dropped or used on mobs, and
 * restores a player's tag state when they log in.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public class TagListener implements Listener {
  private final TagGame game;

  /**
   * Creates the listener.
   *
   * @param game the tag game
   */
  public TagListener(TagGame game) {
    this.game = game;
  }

  /**
   * Passes "it" on when a player hits another player while holding the tag item. Runs last and
   * skips cancelled hits, so hits that protection or PvP rules stop do not tag anyone.
   *
   * @param event the damage event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerDamage(EntityDamageByEntityEvent event) {
    if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return;
    if (!(event.getDamager() instanceof Player tagger)) return;
    if (!(event.getEntity() instanceof Player target)) return;
    if (!game.isTagItem(tagger.getInventory().getItemInMainHand())) return;
    game.tag(tagger, target);
  }

  /**
   * Stops the tag item being used on mobs, which would rename them.
   *
   * @param event the interact event
   */
  @EventHandler(ignoreCancelled = true)
  public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    if (!game.config().preventTagDestruction()) return;
    if (game.isTagItem(event.getPlayer().getInventory().getItem(event.getHand()))) {
      event.setCancelled(true);
    }
  }

  /**
   * Stops the tag item being dropped.
   *
   * @param event the drop event
   */
  @EventHandler(ignoreCancelled = true)
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    if (!game.config().preventTagDestruction()) return;
    if (game.isTagItem(event.getItemDrop().getItemStack())) {
      event.setCancelled(true);
    }
  }

  /**
   * Restores the player's tag state, showing them as "it" again if they still are.
   *
   * @param event the join event
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    game.restore(event.getPlayer());
  }
}
