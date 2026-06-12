package com.nearvanilla.iceCream.modules.playerHeadDrops.events;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.utils.PlayerProfileUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original file: me.machinemaker.papertweaks.modules.items.playerheaddrops.PlayerListener
 * Adaptations: replaced Guice-injected Config with direct IceCream.config lookups; renamed the
 *   killer permission to icecream.modules.playerheaddrops; dropped the Lectern-annotated Config
 *   class in favour of a flat modules.playerheaddrops.* config.yml schema.
 */
/**
 * Listens for player deaths and adds the victim's head to the drop list with a configurable chance
 * and an optional requirement that the killer be a player.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-06-12
 * @see PlayerDeathEvent
 */
public class PlayerDeathHeadDropEvent implements Listener {

  private static final String CONFIG_PREFIX = "modules.playerheaddrops.";

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    Player killer = player.getKiller();

    if (killer == null && IceCream.config.getBoolean(CONFIG_PREFIX + "require-player-kill", true)) {
      return;
    }
    if (killer != null && !killer.hasPermission("icecream.modules.playerheaddrops")) {
      return;
    }

    double chance = IceCream.config.getDouble(CONFIG_PREFIX + "drop-chance", 1.0);
    if (chance <= 0.0) {
      return;
    }
    if (ThreadLocalRandom.current().nextDouble() >= chance) {
      return;
    }

    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) skull.getItemMeta();
    if (meta == null) {
      return;
    }

    PlayerProfile profile = player.getPlayerProfile();
    PlayerProfileUtils.sanitizeTextures(profile);
    meta.setPlayerProfile(profile);
    if (killer != null) {
      meta.lore(List.of(Component.text("Killed by " + killer.getName())));
    }
    skull.setItemMeta(meta);
    event.getDrops().add(skull);
  }
}
