package com.nearvanilla.iceCream.modules.vineGrowth.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vineGrowth.VineGrowthUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * VineGrowthListener lets players shear a vine to stop it spreading, and shear it again to allow
 * growth once more.
 *
 * <p>Right clicking a vine with shears is not a vanilla interaction, so claiming it overrides no
 * existing behavior. Only the clicked block is marked: vines extend from their hanging end, so
 * shearing the lowest vine of a strand is what stops it creeping further.
 *
 * <p>Vines reach new blocks through {@link BlockSpreadEvent}, both downwards and sideways, and
 * widen across the block they already occupy through {@link BlockGrowEvent}. A sheared vine is
 * stopped on both paths.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-09-15
 * @see VineGrowthUtils
 */
public class VineGrowthListener implements Listener {
  private static final int TOGGLE_PARTICLES = 4;
  private static final double TOGGLE_SPREAD = 0.12;
  private static final float TOGGLE_DUST_SIZE = 0.8f;
  private static final int TOGGLE_DURABILITY_COST = 1;
  private static final Particle.DustOptions STOPPED_DUST =
      new Particle.DustOptions(Color.fromRGB(176, 74, 66), TOGGLE_DUST_SIZE);
  private static final Particle.DustOptions GROWING_DUST =
      new Particle.DustOptions(Color.fromRGB(112, 186, 106), TOGGLE_DUST_SIZE);

  /**
   * Toggles the sheared marker when a player right clicks a vine with shears.
   *
   * <p>When both hands hold shears the main hand takes precedence, so a single click toggles the
   * marker exactly once.
   *
   * @param event the player interaction to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Block block = event.getClickedBlock();
    ItemStack shears = event.getItem();
    if (block == null || block.getType() != Material.VINE) {
      return;
    }
    if (shears == null || shears.getType() != Material.SHEARS) {
      return;
    }
    Player player = event.getPlayer();
    if (event.getHand() == EquipmentSlot.OFF_HAND
        && player.getInventory().getItemInMainHand().getType() == Material.SHEARS) {
      return;
    }

    event.setUseItemInHand(Event.Result.DENY);
    EquipmentSlot hand = event.getHand();
    if (hand != null) {
      player.swingHand(hand);
    }

    boolean sheared = !VineGrowthUtils.isSheared(block);
    VineGrowthUtils.setSheared(block, sheared);
    if (player.getGameMode() != GameMode.CREATIVE) {
      shears.damage(TOGGLE_DURABILITY_COST, player);
    }
    showState(block, sheared);
    player.sendActionBar(
        MiniMessage.miniMessage()
            .deserialize(
                IceCream.config.getString(
                    sheared
                        ? "modules.vinegrowth.messages.stopped"
                        : "modules.vinegrowth.messages.growing",
                    sheared
                        ? "<gray>Vine growth <red>stopped</red></gray>"
                        : "<gray>Vine growth <green>resumed</green></gray>")));
    block
        .getWorld()
        .playSound(
            block.getLocation().add(0.5, 0.5, 0.5),
            sheared ? Sound.BLOCK_GROWING_PLANT_CROP : Sound.ITEM_SHEARS_SNIP,
            1.0f,
            sheared ? 1.4f : 1.0f);
  }

  /**
   * Shows a vine's current state as coloured dust.
   *
   * <p>The colour describes the state rather than the action, so it reads the same whether the vine
   * was just toggled or only inspected: muted red for a vine that will not grow, muted green for
   * one that will.
   *
   * @param block the vine to mark
   * @param sheared true when the vine is stopped, false when it is free to grow
   */
  private void showState(Block block, boolean sheared) {
    block
        .getWorld()
        .spawnParticle(
            Particle.DUST,
            block.getLocation().add(0.5, 0.5, 0.5),
            TOGGLE_PARTICLES,
            TOGGLE_SPREAD,
            TOGGLE_SPREAD,
            TOGGLE_SPREAD,
            0.0,
            sheared ? STOPPED_DUST : GROWING_DUST);
  }

  /**
   * Stops a sheared vine reaching a neighboring block, downwards or sideways.
   *
   * @param event the pending spread to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockSpread(BlockSpreadEvent event) {
    if (event.getNewState().getType() != Material.VINE) {
      return;
    }
    if (VineGrowthUtils.isSheared(event.getSource())) {
      event.setCancelled(true);
      return;
    }
    VineGrowthUtils.clearStaleMarker(event.getBlock());
  }

  /**
   * Stops a sheared vine widening across the face of the block it sits on.
   *
   * <p>This event fires at the vine's own position as it gains another face, so the block being
   * grown is the origin and is checked directly rather than looking for a neighbour.
   *
   * @param event the pending growth to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockGrow(BlockGrowEvent event) {
    if (event.getNewState().getType() != Material.VINE) {
      return;
    }
    if (VineGrowthUtils.isSheared(event.getBlock())) {
      event.setCancelled(true);
    }
  }

  /**
   * Tidies markers left behind in a chunk by vines that no longer exist.
   *
   * @param event the chunk load to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    VineGrowthUtils.pruneChunk(event.getChunk());
  }

  /**
   * Clears the marker for a vine a player breaks.
   *
   * @param event the break to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    clearMarker(event.getBlock());
  }

  /**
   * Clears the marker when a player builds over a sheared vine.
   *
   * <p>This covers both directions. Vines are replaceable, so building over one never breaks it and
   * fires no break event, which is why the replaced state is inspected. Placing a vine is also the
   * one way a vine can appear without growing, so any marker left at that position is cleared
   * before the new vine could inherit it.
   *
   * @param event the completed placement to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Block block = event.getBlockPlaced();
    if (block.getType() != Material.VINE
        && event.getBlockReplacedState().getType() != Material.VINE) {
      return;
    }
    VineGrowthUtils.clearStaleMarker(block);
  }

  /**
   * Clears a vine's marker, so the position is not left behind in the chunk.
   *
   * @param block the block being destroyed or moved
   */
  private void clearMarker(Block block) {
    if (block.getType() != Material.VINE) {
      return;
    }
    VineGrowthUtils.clearStaleMarker(block);
  }
}
