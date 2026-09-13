package com.nearvanilla.iceCream.modules.bonemealableSporeBlossoms.events;

import io.papermc.paper.event.block.BlockPreDispenseEvent;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * SporeBlossomBonemealEvent gives spore blossoms the renewable bone meal behavior of two-block-tall
 * flowers.
 *
 * It handles player interactions from either hand and automated use from dispensers. Successful
 * uses consume one bone meal outside creative mode, drop one spore blossom, and show the normal
 * growth effects.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-09-12
 */
public class SporeBlossomBonemealEvent implements Listener {
  private static final int BONE_MEAL_PARTICLES = 15;

  /**
   * Duplicates a spore blossom when a player uses bone meal on it.
   *
   * When both hands contain bone meal, the main-hand interaction takes precedence so one click
   * produces only one blossom.
   *
   * @param event the player interaction to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Block block = event.getClickedBlock();
    ItemStack boneMeal = event.getItem();
    if (block == null || block.getType() != Material.SPORE_BLOSSOM) return;
    if (boneMeal == null || boneMeal.getType() != Material.BONE_MEAL) return;
    if (event.getHand() == EquipmentSlot.OFF_HAND
        && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.BONE_MEAL) {
      return;
    }

    event.setUseItemInHand(Event.Result.DENY);
    var hand = event.getHand();
    if (hand != null) {
      event.getPlayer().swingHand(hand);
    }
    if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
      boneMeal.subtract();
    }

    growSporeBlossom(block);
  }

  /**
   * Duplicates a spore blossom when a dispenser applies bone meal to it.
   *
   * @param event the pending dispenser action to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockPreDispense(BlockPreDispenseEvent event) {
    if (event.getBlock().getType() != Material.DISPENSER) return;
    if (event.getItemStack().getType() != Material.BONE_MEAL) return;

    Directional dispenserData = (Directional) event.getBlock().getBlockData();
    Block target = event.getBlock().getRelative(dispenserData.getFacing());
    if (target.getType() != Material.SPORE_BLOSSOM) return;

    Dispenser dispenser = (Dispenser) event.getBlock().getState();
    Inventory inventory = dispenser.getInventory();
    ItemStack boneMeal = inventory.getItem(event.getSlot());
    if (boneMeal == null || boneMeal.getType() != Material.BONE_MEAL) return;

    event.setCancelled(true);
    if (boneMeal.getAmount() == 1) {
      inventory.clear(event.getSlot());
    } else {
      boneMeal.subtract();
      inventory.setItem(event.getSlot(), boneMeal);
    }
    growSporeBlossom(target);
  }

  /**
   * Drops a spore blossom and displays the bone meal growth effects.
   *
   * @param block the spore blossom receiving bone meal
   */
  private void growSporeBlossom(Block block) {
    var location = block.getLocation().add(0.5, 0.5, 0.5);
    block.getWorld().dropItemNaturally(location, ItemStack.of(Material.SPORE_BLOSSOM));
    block.getWorld().playEffect(location, Effect.BONE_MEAL_USE, 0);
    block
        .getWorld()
        .spawnParticle(Particle.HAPPY_VILLAGER, location, BONE_MEAL_PARTICLES, 0.5, 0.5, 0.5, 0);
  }
}
