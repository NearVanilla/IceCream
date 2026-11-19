package com.nearvanilla.iceCream.modules.growthFreeze.events;

import com.nearvanilla.iceCream.modules.growthFreeze.FreezableBlocks;
import com.nearvanilla.iceCream.modules.growthFreeze.FrozenBlockStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * GrowthFreezeListener lets players freeze a crop, sapling, bamboo stalk or egg at its current
 * stage with a golden dandelion, and unfreeze it by using another.
 *
 * <p>This mirrors the vanilla golden dandelion on baby animals: each use consumes one dandelion
 * outside creative mode and plays the same sounds.
 *
 * <p>Each kind of growth reaches the server through a different event, and a frozen plant is
 * stopped on all of them:
 *
 * <ul>
 *   <li>crops, stems, berries, cocoa and nether wart age through {@link BlockGrowEvent}, which also
 *       places a stem's fruit and advances a sniffer egg's hatch stage
 *   <li>bamboo grows taller through {@link BlockSpreadEvent}, from its top block
 *   <li>saplings become trees through {@link StructureGrowEvent}
 *   <li>frogspawn and sniffer eggs hatch through {@link BlockFadeEvent}
 *   <li>bone meal is refused through {@link BlockFertilizeEvent}, so it is not wasted
 * </ul>
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-19
 * @see FreezableBlocks
 * @see FrozenBlockStore
 */
public class GrowthFreezeListener implements Listener {
  private static final int STATE_PARTICLES = 4;
  private static final double STATE_SPREAD = 0.12;
  private static final float STATE_DUST_SIZE = 0.8f;
  private static final Particle.DustOptions FROZEN_DUST =
      new Particle.DustOptions(Color.fromRGB(122, 184, 214), STATE_DUST_SIZE);
  private static final Particle.DustOptions GROWING_DUST =
      new Particle.DustOptions(Color.fromRGB(112, 186, 106), STATE_DUST_SIZE);
  private static final BlockFace[] STEM_SIDES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private final FrozenBlockStore store;
  private final Component frozenMessage;
  private final Component resumedMessage;

  /**
   * Creates the listener.
   *
   * @param store where frozen markers are kept
   * @param frozenMessage the action bar message shown when a block is frozen
   * @param resumedMessage the action bar message shown when a block is unfrozen
   */
  public GrowthFreezeListener(
      FrozenBlockStore store, Component frozenMessage, Component resumedMessage) {
    this.store = store;
    this.frozenMessage = frozenMessage;
    this.resumedMessage = resumedMessage;
  }

  /**
   * Toggles the frozen marker when a player right clicks a freezable block with a golden dandelion.
   *
   * <p>Both the item and the block interaction are denied, so the click neither places the
   * dandelion nor harvests a berry bush. When both hands hold a dandelion the main hand takes
   * precedence, so a single click toggles the marker exactly once.
   *
   * @param event the player interaction to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Block clicked = event.getClickedBlock();
    ItemStack dandelion = event.getItem();
    if (clicked == null || dandelion == null || dandelion.getType() != Material.GOLDEN_DANDELION) {
      return;
    }
    if (!FreezableBlocks.isFreezable(clicked)) {
      return;
    }
    Player player = event.getPlayer();
    if (event.getHand() == EquipmentSlot.OFF_HAND
        && player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_DANDELION) {
      return;
    }

    event.setUseItemInHand(Event.Result.DENY);
    event.setUseInteractedBlock(Event.Result.DENY);
    EquipmentSlot hand = event.getHand();
    if (hand != null) {
      player.swingHand(hand);
    }

    Block anchor = FreezableBlocks.anchor(clicked);
    boolean frozen = !store.isFrozen(anchor);
    store.setFrozen(anchor, frozen);
    if (!frozen && anchor.getType() == Material.FROGSPAWN) {
      restartHatchTimer(anchor);
    }
    if (player.getGameMode() != GameMode.CREATIVE) {
      dandelion.subtract();
    }

    showState(clicked, frozen);
    player.sendActionBar(frozen ? frozenMessage : resumedMessage);
    clicked
        .getWorld()
        .playSound(
            clicked.getLocation().toCenterLocation(),
            frozen ? Sound.ITEM_GOLDEN_DANDELION_USE : Sound.ITEM_GOLDEN_DANDELION_UNUSE,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f);
  }

  /**
   * Gives frogspawn a new hatch countdown.
   *
   * <p>Frogspawn hatches from a single scheduled tick, and cancelling that hatch does not schedule
   * another, so frogspawn that was frozen when its tick came would otherwise never hatch. Placing
   * the block again schedules a fresh countdown the way vanilla does when frogspawn is laid. If the
   * original tick is still pending it is kept, since the server holds one tick per position.
   *
   * @param frogspawn the frogspawn being unfrozen
   */
  private void restartHatchTimer(Block frogspawn) {
    BlockData data = frogspawn.getBlockData();
    frogspawn.setType(Material.AIR, false);
    frogspawn.setBlockData(data, true);
  }

  /**
   * Shows a block's current state as coloured dust.
   *
   * <p>A frozen crop looks the same as one that is still growing, so this is the only sign which
   * state a click left it in: pale blue for frozen, muted green for growing.
   *
   * @param block the clicked block
   * @param frozen true when the block is now frozen
   */
  private void showState(Block block, boolean frozen) {
    block
        .getWorld()
        .spawnParticle(
            Particle.DUST,
            block.getLocation().toCenterLocation(),
            STATE_PARTICLES,
            STATE_SPREAD,
            STATE_SPREAD,
            STATE_SPREAD,
            0.0,
            frozen ? FROZEN_DUST : GROWING_DUST);
  }

  /**
   * Checks whether a block belongs to a frozen plant.
   *
   * @param block any block of the plant
   * @return true when the block is freezable and its plant is frozen
   */
  private boolean isFrozen(Block block) {
    return FreezableBlocks.isFreezable(block) && store.isFrozen(FreezableBlocks.anchor(block));
  }

  /**
   * Stops a frozen plant aging, and a frozen stem placing its fruit.
   *
   * <p>A stem places its fruit on a neighbouring block, so the event fires at the fruit's position
   * rather than the stem's. The event does not say which stem grew it, so fruit is held back when
   * any adjoining stem of the same kind is frozen.
   *
   * @param event the pending growth to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockGrow(BlockGrowEvent event) {
    Block block = event.getBlock();
    if (isFrozen(block)) {
      event.setCancelled(true);
      return;
    }
    Material stem =
        switch (event.getNewState().getType()) {
          case MELON -> Material.MELON_STEM;
          case PUMPKIN -> Material.PUMPKIN_STEM;
          default -> null;
        };
    if (stem == null) {
      return;
    }
    for (BlockFace side : STEM_SIDES) {
      Block neighbour = block.getRelative(side);
      if (neighbour.getType() == stem && store.isFrozen(neighbour)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  /**
   * Stops a frozen bamboo stalk growing taller.
   *
   * @param event the pending spread to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockSpread(BlockSpreadEvent event) {
    Block source = event.getSource();
    if (FreezableBlocks.isBamboo(source.getType()) && isFrozen(source)) {
      event.setCancelled(true);
    }
  }

  /**
   * Stops a frozen sapling becoming a tree.
   *
   * <p>Large trees grow from a two by two square of saplings, so every sapling the tree would
   * replace is checked, not only the one that triggered the growth.
   *
   * @param event the pending tree growth to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onStructureGrow(StructureGrowEvent event) {
    if (isFrozen(event.getLocation().getBlock())) {
      event.setCancelled(true);
      return;
    }
    for (BlockState state : event.getBlocks()) {
      Block block = state.getBlock();
      if (Tag.SAPLINGS.isTagged(block.getType()) && store.isFrozen(block)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  /**
   * Refuses bone meal on a frozen plant, so it is not used up for nothing.
   *
   * @param event the pending bone meal use to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockFertilize(BlockFertilizeEvent event) {
    if (isFrozen(event.getBlock())) {
      event.setCancelled(true);
    }
  }

  /**
   * Stops frozen frogspawn or a frozen sniffer egg hatching.
   *
   * @param event the pending hatch to inspect
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockFade(BlockFadeEvent event) {
    Block block = event.getBlock();
    if (isEgg(block.getType()) && store.isFrozen(block)) {
      event.setCancelled(true);
    }
  }

  /**
   * Clears the marker of an egg that has hatched.
   *
   * @param event the completed hatch to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFaded(BlockFadeEvent event) {
    Block block = event.getBlock();
    if (isEgg(block.getType())) {
      store.setFrozen(block, false);
    }
  }

  private static boolean isEgg(Material type) {
    return type == Material.FROGSPAWN || type == Material.SNIFFER_EGG;
  }

  /**
   * Clears the marker of a frozen plant a player breaks.
   *
   * <p>Breaking a bamboo stalk above its base leaves the rest standing, so only breaking the base
   * itself clears the stalk's marker. Breaking either half of a pitcher crop removes both.
   *
   * @param event the break to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (!FreezableBlocks.isFreezable(block)) {
      return;
    }
    Block anchor = FreezableBlocks.anchor(block);
    if (block.getType() == Material.BAMBOO && !anchor.equals(block)) {
      return;
    }
    store.setFrozen(anchor, false);
  }

  /**
   * Clears any marker left where a player plants something new.
   *
   * <p>A plant destroyed without being broken, such as a trampled crop, leaves its marker behind
   * until the chunk next loads. Clearing it here stops a new plant inheriting it. Bamboo stacked on
   * a frozen stalk is not its own anchor, so the stalk stays frozen.
   *
   * @param event the completed placement to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Block placed = event.getBlockPlaced();
    if (FreezableBlocks.isFreezable(placed) && FreezableBlocks.anchor(placed).equals(placed)) {
      store.setFrozen(placed, false);
    }
  }

  /**
   * Clears any marker left where a mob places something new, such as a villager planting a crop or
   * a frog laying spawn.
   *
   * <p>Changes that keep the block's type, such as a fox eating berries from a bush, leave the
   * marker alone.
   *
   * @param event the completed change to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityChangeBlock(EntityChangeBlockEvent event) {
    Block block = event.getBlock();
    if (block.getType() != event.getTo()) {
      store.setFrozen(block, false);
    }
  }

  /**
   * Tidies markers left behind in a chunk by plants that no longer exist.
   *
   * @param event the chunk load to inspect
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    store.prune(event.getChunk());
  }
}
