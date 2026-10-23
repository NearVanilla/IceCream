package com.nearvanilla.iceCream.modules.mapTools.events;

import com.nearvanilla.iceCream.modules.mapTools.AreaDisplays;
import com.nearvanilla.iceCream.modules.mapTools.EditActions;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.IconMenu;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Turns left clicks into edits for players in edit mode, keeps them from changing blocks or
 * interacting with entities while editing, adds the areas layer to worlds loaded after startup, and
 * cleans up when players change world or log out.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class MapToolsListener implements Listener {

  /** Runs at MONITOR so squaremap has registered its own map world for the new world first. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldLoad(WorldLoadEvent event) {
    MapToolsUtils.registerWorld(event.getWorld());
  }

  /**
   * Turns left clicks into edits; shift + left click is the second action. Clicks never use items
   * or break blocks.
   */
  @EventHandler(priority = EventPriority.LOW)
  public void onClick(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    if (!MapToolsUtils.isEditing(player)) return;
    event.setCancelled(true);
    // Stepping on farmland, pressure plates and tripwires does nothing while editing.
    if (event.getAction() == Action.PHYSICAL) return;

    // Only left click is used: the client always reports it, at any distance. Right clicks with
    // an empty hand are not sent beyond normal reach.
    if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isLeftClick()) return;
    boolean secondary = player.isSneaking();

    // Reach as far as outlines are drawn, and stop at water so lakes and the sea can be clicked.
    int reach = (int) AreaDisplays.VIEW_DISTANCE;

    Block block = event.getClickedBlock();
    if (block == null) block = player.getTargetBlockExact(reach, FluidCollisionMode.ALWAYS);

    // Ghost blocks have no hitbox, so check the line of sight against the corners of the area
    // being edited, or the points of a new polygon, before falling back to the block that was hit.
    // A corner behind the hit block does not count, so clicks near the player are not taken by a
    // corner far away in the same direction.
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    if (session != null
        && (session.isEditingArea()
            || (session.isNew() && session.getMode() == EditSession.Mode.POLYGON))) {
      Location eye = player.getEyeLocation();
      Location corner = session.cornerInSight(eye, reach);
      if (corner != null && (block == null || !isBehind(eye, corner, block))) {
        EditActions.click(player, corner, secondary);
        return;
      }
    }

    if (block == null) {
      EditActions.clickNothing(player, secondary);
      return;
    }
    // Always use the top of the clicked column (water counts, leaves do not), so clicking the side
    // of a cliff or the bottom of a lake still selects the surface.
    Block top =
        block
            .getWorld()
            .getHighestBlockAt(block.getX(), block.getZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
    EditActions.click(player, top.getLocation(), secondary);
  }

  /**
   * The swap hands key (F by default) undoes the last change while drawing or editing; with shift
   * it redoes.
   */
  @EventHandler(priority = EventPriority.LOW)
  public void onSwapHands(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
    // Only while drawing or editing a shape; in view mode the key swaps hands as usual.
    if (session == null || session.isViewing()) return;
    event.setCancelled(true);
    if (player.isSneaking()) {
      EditActions.redo(player);
    } else {
      EditActions.undo(player);
    }
  }

  /**
   * Whether a corner, where its ghost block is drawn, is further from the eye than the hit block.
   */
  private static boolean isBehind(Location eye, Location corner, Block block) {
    Location drawn = AreaDisplays.drawnGhost(corner).toCenterLocation();
    // Allow a little slack so a corner on the hit block, or right next to it, still counts.
    return eye.distance(drawn) > eye.distance(block.getLocation().toCenterLocation()) + 1.5;
  }

  /** Creative players break blocks instantly, so make sure clicks never break anything. */
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  /** Right clicking entities, such as item frames, villagers and armor stands. */
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onArmorStand(PlayerArmorStandManipulateEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  /** Hitting or killing entities. */
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onAttack(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player player && MapToolsUtils.isEditing(player)) {
      event.setCancelled(true);
    }
  }

  /** Breaking paintings and item frames. */
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onHangingBreak(HangingBreakByEntityEvent event) {
    if (event.getRemover() instanceof Player player && MapToolsUtils.isEditing(player)) {
      event.setCancelled(true);
    }
  }

  /** Breaking boats and minecarts. */
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onVehicleDamage(VehicleDamageEvent event) {
    if (event.getAttacker() instanceof Player player && MapToolsUtils.isEditing(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBucketFill(PlayerBucketFillEvent event) {
    if (MapToolsUtils.isEditing(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    AreaDisplays.refresh(event.getPlayer());
  }

  /** Clicks in the icon menu pick an icon and never move items. */
  @EventHandler(priority = EventPriority.LOW)
  public void onIconMenuClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder(false) instanceof IconMenu menu)) return;
    event.setCancelled(true);
    if (event.getWhoClicked() instanceof Player player
        && event.getClickedInventory() == event.getInventory()) {
      menu.click(player, event.getSlot());
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onIconMenuDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder(false) instanceof IconMenu) event.setCancelled(true);
  }

  @EventHandler
  public void onIconMenuClose(InventoryCloseEvent event) {
    if (event.getInventory().getHolder(false) instanceof IconMenu menu
        && event.getPlayer() instanceof Player player) {
      menu.closed(player);
    }
  }

  /** Edit mode ends on logout. */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    MapToolsUtils.stopEditing(event.getPlayer());
  }
}
