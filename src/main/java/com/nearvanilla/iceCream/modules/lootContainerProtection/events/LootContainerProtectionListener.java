package com.nearvanilla.iceCream.modules.lootContainerProtection.events;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

public class LootContainerProtectionListener implements Listener {
  private static final Component SNEAK_MESSAGE =
      Component.text("You need to sneak to break loot containers!", NamedTextColor.RED);

  private final NamespacedKey originalLootTableKey;

  public LootContainerProtectionListener(NamespacedKey originalLootTableKey) {
    this.originalLootTableKey = originalLootTableKey;
  }

  private boolean isLootContainer(Object holder) {
    if (!(holder instanceof Lootable lootable)
        || !(holder instanceof PersistentDataHolder persistent)
        || !(holder instanceof StorageMinecart
            || holder instanceof TileState && holder instanceof InventoryHolder)) {
      return false;
    }
    return lootable.getLootTable() != null
        || persistent
            .getPersistentDataContainer()
            .has(originalLootTableKey, PersistentDataType.STRING);
  }

  private boolean isLootContainer(Block block) {
    return isLootContainer(block.getState(false));
  }

  // Native generation can consume the assigned table even when another plugin cancels the loot.
  @EventHandler(priority = EventPriority.MONITOR)
  public void onLootGenerate(LootGenerateEvent event) {
    InventoryHolder holder = event.getInventoryHolder();
    // Plugin-filled ordinary inventories are not world loot containers.
    if (event.isPlugin() && (event.isCancelled() || !isLootContainer(holder))) return;

    if (holder instanceof TileState state && holder instanceof Lootable && state.isPlaced()) {
      // Use the live tile, not the event snapshot: updating a snapshot here can restore stale loot
      // tables or inventory contents while vanilla is still generating loot.
      if (state.getBlock().getState(false) instanceof TileState live
          && live instanceof Lootable
          && live instanceof InventoryHolder
          && live.getType() == state.getType()) {
        if (saveOriginalTable(live, event.getLootTable().getKey())) {
          live.update(false, false);
        }
      }
    } else if (holder instanceof StorageMinecart cart) {
      saveOriginalTable(cart, event.getLootTable().getKey());
    }
  }

  private boolean saveOriginalTable(PersistentDataHolder holder, NamespacedKey table) {
    var data = holder.getPersistentDataContainer();
    if (data.has(originalLootTableKey, PersistentDataType.STRING)) return false;
    data.set(originalLootTableKey, PersistentDataType.STRING, table.toString());
    return true;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    if (isLootContainer(event.getBlock())) {
      restrictDestruction(event, event.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onVehicleDestroy(VehicleDestroyEvent event) {
    if (isLootContainer(event.getVehicle())) {
      restrictDestruction(event, event.getAttacker());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onVehicleDamage(VehicleDamageEvent event) {
    if (isLootContainer(event.getVehicle())) {
      restrictDestruction(event, event.getAttacker());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityDamage(EntityDamageEvent event) {
    if (isLootContainer(event.getEntity())) {
      restrictDestruction(event, event.getDamageSource().getDirectEntity());
    }
  }

  private void restrictDestruction(Cancellable event, Entity attacker) {
    Player player = null;
    if (attacker instanceof Player attackingPlayer) {
      player = attackingPlayer;
    } else if (attacker instanceof Projectile projectile
        && projectile.getShooter() instanceof Player shooter) {
      player = shooter;
    }
    if (player != null && player.isSneaking()) return;
    event.setCancelled(true);
    if (player != null) player.sendMessage(SNEAK_MESSAGE);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityExplode(EntityExplodeEvent event) {
    event.blockList().removeIf(this::isLootContainer);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockExplode(BlockExplodeEvent event) {
    event.blockList().removeIf(this::isLootContainer);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockBurn(BlockBurnEvent event) {
    if (isLootContainer(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityChangeBlock(EntityChangeBlockEvent event) {
    if (isLootContainer(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockDestroy(BlockDestroyEvent event) {
    if (isLootContainer(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    for (Block block : event.getBlocks()) {
      if (isLootContainer(block)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    for (Block block : event.getBlocks()) {
      if (isLootContainer(block)) {
        event.setCancelled(true);
        return;
      }
    }
  }
}
