package com.nearvanilla.iceCream.modules.coordinatesHud.events;

import com.nearvanilla.iceCream.utils.ActionBarUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.block.BlockLockCheckEvent;
import io.papermc.paper.event.player.PlayerBedFailEnterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;

/**
 * Tells {@link ActionBarUtils} when the game shows a player a message in their action bar, so the
 * Coordinates HUD steps aside for it as it does for IceCream's own messages.
 *
 * <p>Paper has no event for action bar messages, so this listens to the events behind the game's
 * own:
 *
 * <ul>
 *   <li>failing to get into bed, such as "You can sleep only at night" or "You may not rest now;
 *       there are monsters nearby"
 *   <li>clicking a bed another player sleeps in: "This bed is occupied"
 *   <li>the sleeping count, such as "1/2 players sleeping", which everyone in the world sees when a
 *       player gets into or out of bed, joins, leaves, changes world or switches to or from
 *       spectator, as long as someone is still asleep afterwards
 *   <li>"No amount of rest can pass this night", which a sleeper sees when sleeping cannot skip the
 *       night
 *   <li>mounting a mob or vehicle, which shows the dismount hint
 *   <li>a jukebox starting a disc, which shows "Now Playing" to players within 64 blocks
 *   <li>trying to open a locked container: "Chest is locked!"
 * </ul>
 *
 * <p>Not covered, because no event comes first: the game's spawn protection, build height, disabled
 * TNT, spectator and debug stick messages, {@code /title ... actionbar}, and other plugins'
 * messages.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-28
 * @see ActionBarUtils#claim
 */
public class VanillaActionBarListener implements Listener {
  /** How far from a jukebox players are told what it starts playing. */
  private static final double JUKEBOX_RANGE = 64;

  /** The sleeping percentage above which sleeping never skips the night. */
  private static final int MAX_SKIPPING_PERCENTAGE = 100;

  /** Players just told a container is locked, with the tick, in case their key opens it. */
  private final Map<UUID, Integer> toldLocked = new HashMap<>();

  /** Pauses for a bed refusal, such as "You can sleep only at night". */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBedFail(PlayerBedFailEnterEvent event) {
    if (event.getMessage() != null) {
      ActionBarUtils.claim(event.getPlayer());
    }
  }

  /**
   * Pauses for the sleeping count, or "No amount of rest can pass this night". Whether the player
   * gets into bed is worked out as the server does: {@link PlayerBedEnterEvent#isCancelled} only
   * looks at the world's bed rule, not at problems such as the bed being too far away.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onBedEnter(PlayerBedEnterEvent event) {
    boolean entering =
        event.useBed() == Event.Result.ALLOW
            || (event.useBed() == Event.Result.DEFAULT
                && event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK);
    if (!entering) return;
    Player player = event.getPlayer();
    if (canSkipNight(player.getWorld())) {
      claimWorld(player.getWorld(), null);
    } else {
      ActionBarUtils.claim(player);
    }
  }

  /** Pauses for the sleeping count, which changes when a player gets out of bed. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBedLeave(PlayerBedLeaveEvent event) {
    Player player = event.getPlayer();
    claimSleepingCount(player.getWorld(), player, null);
  }

  /** Pauses for the sleeping count, which changes when a player joins while someone sleeps. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    claimSleepingCount(event.getPlayer().getWorld(), null, null);
  }

  /** Pauses for the sleeping count, which changes when a player leaves while someone sleeps. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    toldLocked.remove(player.getUniqueId());
    claimSleepingCount(player.getWorld(), player, player);
  }

  /** Pauses for the sleeping count in the world left and the world entered. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    claimSleepingCount(event.getFrom(), null, null);
    claimSleepingCount(event.getPlayer().getWorld(), null, null);
  }

  /** Pauses for the sleeping count, which leaves out spectators. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
    Player player = event.getPlayer();
    boolean wasSpectator = player.getGameMode() == GameMode.SPECTATOR;
    if (wasSpectator != (event.getNewGameMode() == GameMode.SPECTATOR)) {
      claimSleepingCount(player.getWorld(), null, null);
    }
  }

  /** Pauses for the dismount hint, which the game shows a player who starts riding. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMount(EntityMountEvent event) {
    if (event.getEntity() instanceof Player player) {
      ActionBarUtils.claim(player);
    }
  }

  /** Pauses for "This bed is occupied", and for "Now Playing" when a disc goes in a jukebox. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK
        || event.useInteractedBlock() == Event.Result.DENY) {
      return;
    }
    Block block = event.getClickedBlock();
    Player player = event.getPlayer();
    if (block == null || skipsBlockUse(player)) return;
    if (block.getBlockData() instanceof Bed bed) {
      if (event.getHand() == EquipmentSlot.HAND && isTakenByPlayer(block, bed)) {
        ActionBarUtils.claim(player);
      }
    } else if (block.getType() == Material.JUKEBOX
        && isDisc(event.getItem())
        && block.getState() instanceof Jukebox jukebox
        && jukebox.getRecord().isEmpty()) {
      claimNear(block.getLocation());
    }
  }

  /** Pauses for "Now Playing" when a hopper puts a disc in a jukebox. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInventoryMove(InventoryMoveItemEvent event) {
    Inventory destination = event.getDestination();
    Location location = destination.getLocation();
    if (destination.getType() == InventoryType.JUKEBOX
        && location != null
        && isDisc(event.getItem())) {
      claimNear(location);
    }
  }

  /**
   * Pauses for "Chest is locked!". When the game still has to try the player's key, the pause is
   * given back if the container opens.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onLockCheck(BlockLockCheckEvent event) {
    Player player = event.getPlayer();
    Event.Result result = event.getResult();
    if (result == Event.Result.ALLOW) return;
    if (result == Event.Result.DEFAULT) {
      // Spectators are never stopped by a lock.
      if (player.getGameMode() == GameMode.SPECTATOR || !event.getBlockState().isLocked()) return;
      toldLocked.put(player.getUniqueId(), Bukkit.getCurrentTick());
    }
    ActionBarUtils.claim(player);
  }

  /** Gives the action bar back when a key opened the locked container after all. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryOpen(InventoryOpenEvent event) {
    Integer tick = toldLocked.remove(event.getPlayer().getUniqueId());
    if (tick != null
        && tick == Bukkit.getCurrentTick()
        && event.getPlayer() instanceof Player player) {
      ActionBarUtils.release(player);
    }
  }

  /** Whether sleeping can skip the night in a world, which is when the game counts sleepers. */
  private static boolean canSkipNight(World world) {
    Integer percentage = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
    return percentage == null || percentage <= MAX_SKIPPING_PERCENTAGE;
  }

  /**
   * Pauses everyone in a world for the sleeping count. The game only shows it while someone is
   * still asleep once the change is done, so a player getting out of bed or leaving does not count.
   *
   * @param world the world
   * @param notCounted a player getting out of bed or leaving, who no longer counts as asleep, or
   *     null
   * @param leaving a player leaving the server, who is not paused either, or null
   */
  private static void claimSleepingCount(World world, Player notCounted, Player leaving) {
    boolean someoneStillAsleep =
        world.getPlayers().stream()
            .anyMatch(player -> player.isSleeping() && !player.equals(notCounted));
    if (canSkipNight(world) && someoneStillAsleep) {
      claimWorld(world, leaving);
    }
  }

  /** Pauses everyone in a world, except a player who is leaving. */
  private static void claimWorld(World world, Player except) {
    for (Player player : world.getPlayers()) {
      if (!player.equals(except)) ActionBarUtils.claim(player);
    }
  }

  /** Pauses everyone close enough to a jukebox to be told what it plays. */
  private static void claimNear(Location jukebox) {
    for (Player player : jukebox.getWorld().getPlayers()) {
      if (player.getLocation().distanceSquared(jukebox) < JUKEBOX_RANGE * JUKEBOX_RANGE) {
        ActionBarUtils.claim(player);
      }
    }
  }

  /** Whether a click skips the block, as it does when sneaking with something in either hand. */
  private static boolean skipsBlockUse(Player player) {
    PlayerInventory inventory = player.getInventory();
    return player.isSneaking()
        && (!inventory.getItemInMainHand().isEmpty() || !inventory.getItemInOffHand().isEmpty());
  }

  /**
   * Whether a bed is taken by someone the game cannot wake to make room. A villager sleeping there
   * is woken instead of the message showing.
   */
  private static boolean isTakenByPlayer(Block clicked, Bed bed) {
    Block head = bed.getPart() == Bed.Part.HEAD ? clicked : clicked.getRelative(bed.getFacing());
    if (!(head.getBlockData() instanceof Bed headBed) || !headBed.isOccupied()) return false;
    return head.getWorld()
        .getNearbyEntities(
            BoundingBox.of(head),
            entity -> entity instanceof Villager villager && villager.isSleeping())
        .isEmpty();
  }

  /** Whether an item plays in a jukebox. */
  private static boolean isDisc(ItemStack item) {
    return item != null && item.hasData(DataComponentTypes.JUKEBOX_PLAYABLE);
  }
}
