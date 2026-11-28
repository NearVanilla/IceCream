package com.nearvanilla.iceCream.modules.coordinatesHud.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.utils.ActionBarUtils;
import io.papermc.paper.block.bed.BedEnterAction;
import io.papermc.paper.block.bed.BedRuleResult;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.JukeboxPlayable;
import io.papermc.paper.event.block.BlockLockCheckEvent;
import io.papermc.paper.event.player.PlayerBedFailEnterEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.JukeboxSong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class VanillaActionBarListenerTest {
  private static final long PAUSE_MILLIS = 3000;
  private static final Sound LOCKED_SOUND =
      Sound.sound(Key.key("block.chest.locked"), Sound.Source.BLOCK, 1f, 1f);

  private ServerMock server;
  private WorldMock world;
  private WorldMock nether;
  private long now;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    nether = server.addSimpleWorld("world_nether");
    now = 1_000;
    ActionBarUtils.useClock(() -> now);
    server
        .getPluginManager()
        .registerEvents(new VanillaActionBarListener(), MockBukkit.createMockPlugin());
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    MockBukkit.unmock();
  }

  /** A music disc. MockBukkit leaves out items' default components, so it gets its own. */
  private static ItemStack disc() {
    ItemStack disc = new ItemStack(Material.MUSIC_DISC_CAT);
    disc.setData(
        DataComponentTypes.JUKEBOX_PLAYABLE,
        JukeboxPlayable.jukeboxPlayable(JukeboxSong.CAT).build());
    return disc;
  }

  /** How getting into a bed goes, as far as the world's bed rule is concerned. */
  private static BedEnterAction action(BedRuleResult canSleep) {
    BedEnterAction action = mock(BedEnterAction.class);
    when(action.canSleep()).thenReturn(canSleep);
    return action;
  }

  /**
   * Adds a player at a place. Adding a player fires a join, so add everyone before anyone sleeps.
   */
  private PlayerMock player(World in, double x, double z) {
    PlayerMock player = server.addPlayer();
    player.setLocation(new Location(in, x, 64, z));
    return player;
  }

  private PlayerMock player() {
    return player(world, 0, 0);
  }

  private static boolean paused(Player player) {
    return ActionBarUtils.sentWithin(player, PAUSE_MILLIS);
  }

  private void call(Event event) {
    server.getPluginManager().callEvent(event);
  }

  private Block bed(boolean occupied) {
    Block foot = world.getBlockAt(10, 64, 10);
    Block head = foot.getRelative(BlockFace.NORTH);
    for (Block part : new Block[] {foot, head}) {
      Bed data = (Bed) Material.RED_BED.createBlockData();
      data.setFacing(BlockFace.NORTH);
      data.setPart(part == head ? Bed.Part.HEAD : Bed.Part.FOOT);
      data.setOccupied(occupied && part == head);
      part.setBlockData(data);
    }
    return foot;
  }

  private static PlayerInteractEvent rightClick(Player player, ItemStack item, Block block) {
    return new PlayerInteractEvent(
        player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP, EquipmentSlot.HAND);
  }

  private Block jukebox(boolean playing) {
    Block block = world.getBlockAt(0, 64, 0);
    block.setType(Material.JUKEBOX);
    if (playing) {
      Jukebox state = (Jukebox) block.getState();
      state.setRecord(disc());
      state.update(true);
    }
    return block;
  }

  /** A chest, mocked because MockBukkit does not implement locks. */
  private static Block chest(boolean locked) {
    Chest state = mock(Chest.class);
    when(state.isLocked()).thenReturn(locked);
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(Material.CHEST);
    when(block.getState()).thenReturn(state);
    return block;
  }

  private static InventoryOpenEvent opened(Player player) {
    InventoryView view = mock(InventoryView.class);
    when(view.getPlayer()).thenReturn(player);
    return new InventoryOpenEvent(view);
  }

  private static PlayerGameModeChangeEvent modeChange(Player player, GameMode mode) {
    return new PlayerGameModeChangeEvent(
        player, mode, PlayerGameModeChangeEvent.Cause.COMMAND, Component.empty());
  }

  // Beds

  @Test
  void bedRefusalWithMessagePauses() {
    PlayerMock player = player();

    call(
        new PlayerBedFailEnterEvent(
            player,
            PlayerBedFailEnterEvent.FailReason.NOT_POSSIBLE_NOW,
            bed(false),
            false,
            Component.text("You can sleep only at night"),
            mock(BedEnterAction.class)));

    assertTrue(paused(player));
  }

  @Test
  void bedRefusalWithoutMessageDoesNotPause() {
    PlayerMock player = player();

    call(
        new PlayerBedFailEnterEvent(
            player,
            PlayerBedFailEnterEvent.FailReason.OTHER_PROBLEM,
            bed(false),
            false,
            null,
            mock(BedEnterAction.class)));

    assertFalse(paused(player));
  }

  @Test
  void cancelledBedRefusalDoesNotPause() {
    PlayerMock player = player();
    PlayerBedFailEnterEvent event =
        new PlayerBedFailEnterEvent(
            player,
            PlayerBedFailEnterEvent.FailReason.NOT_POSSIBLE_NOW,
            bed(false),
            false,
            Component.text("You can sleep only at night"),
            mock(BedEnterAction.class));
    event.setCancelled(true);

    call(event);

    assertFalse(paused(player));
  }

  @Test
  void clickingBedSomeoneSleepsInPauses() {
    PlayerMock player = player();

    call(rightClick(player, null, bed(true)));

    assertTrue(paused(player));
  }

  @Test
  void clickingFreeBedDoesNotPause() {
    PlayerMock player = player();

    call(rightClick(player, null, bed(false)));

    assertFalse(paused(player));
  }

  @Test
  void sneakingWithAnItemDoesNotUseTheBed() {
    PlayerMock player = player();
    player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    player.setSneaking(true);

    call(rightClick(player, new ItemStack(Material.STICK), bed(true)));

    assertFalse(paused(player));
  }

  @Test
  void clickingBedVillagerSleepsInDoesNotPause() {
    PlayerMock player = player();
    Block foot = bed(true);
    LivingEntityMock villager =
        (LivingEntityMock)
            world.spawn(
                foot.getRelative(BlockFace.NORTH).getLocation().add(0.5, 0.1, 0.5), Villager.class);
    villager.setSleeping(true);

    call(rightClick(player, null, foot));

    assertFalse(paused(player));
  }

  @Test
  void deniedBedClickDoesNotPause() {
    PlayerMock player = player();
    PlayerInteractEvent event = rightClick(player, null, bed(true));
    event.setUseInteractedBlock(Event.Result.DENY);

    call(event);

    assertFalse(paused(player));
  }

  // Sleep status

  @Test
  void gettingIntoBedPausesEveryoneInTheWorld() {
    PlayerMock sleeper = player();
    PlayerMock other = player(world, 500, 500);
    PlayerMock elsewhere = player(nether, 0, 0);

    call(
        new PlayerBedEnterEvent(
            sleeper,
            bed(false),
            PlayerBedEnterEvent.BedEnterResult.OK,
            action(BedRuleResult.ALLOWED)));

    assertTrue(paused(sleeper));
    assertTrue(paused(other));
    assertFalse(paused(elsewhere));
  }

  @Test
  void gettingIntoBedWhenSleepCannotSkipNightPausesOnlySleeper() {
    world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
    PlayerMock sleeper = player();
    PlayerMock other = player(world, 500, 500);

    call(
        new PlayerBedEnterEvent(
            sleeper,
            bed(false),
            PlayerBedEnterEvent.BedEnterResult.OK,
            action(BedRuleResult.ALLOWED)));

    assertTrue(paused(sleeper));
    assertFalse(paused(other));
  }

  @Test
  void refusedBedEnterDoesNotPauseEveryone() {
    PlayerMock sleeper = player();
    PlayerMock other = player(world, 500, 500);

    call(
        new PlayerBedEnterEvent(
            sleeper,
            bed(false),
            PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW,
            action(BedRuleResult.TOO_MUCH_LIGHT)));

    assertFalse(paused(other));
  }

  @Test
  void forcedBedEnterPausesEveryone() {
    PlayerMock sleeper = player();
    PlayerMock other = player(world, 500, 500);
    PlayerBedEnterEvent event =
        new PlayerBedEnterEvent(
            sleeper,
            bed(false),
            PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW,
            action(BedRuleResult.TOO_MUCH_LIGHT));
    event.setUseBed(Event.Result.ALLOW);

    call(event);

    assertTrue(paused(other));
  }

  @Test
  void cancelledBedEnterDoesNotPause() {
    PlayerMock sleeper = player();
    PlayerBedEnterEvent event =
        new PlayerBedEnterEvent(
            sleeper,
            bed(false),
            PlayerBedEnterEvent.BedEnterResult.OK,
            action(BedRuleResult.ALLOWED));
    event.setCancelled(true);

    call(event);

    assertFalse(paused(sleeper));
  }

  @Test
  void gettingOutOfBedWhileOthersSleepPausesEveryoneInTheWorld() {
    PlayerMock waking = player();
    PlayerMock stillAsleep = player(world, 500, 500);
    PlayerMock awake = player(world, -500, -500);
    waking.setSleeping(true);
    stillAsleep.setSleeping(true);

    call(new PlayerBedLeaveEvent(waking, bed(false), true));

    assertTrue(paused(waking));
    assertTrue(paused(stillAsleep));
    assertTrue(paused(awake));
  }

  @Test
  void lastSleeperGettingOutOfBedDoesNotPause() {
    // The game only shows the sleeping count while someone is still asleep.
    PlayerMock waking = player();
    PlayerMock awake = player(world, 500, 500);
    waking.setSleeping(true);

    call(new PlayerBedLeaveEvent(waking, bed(false), true));

    assertFalse(paused(waking));
    assertFalse(paused(awake));
  }

  @Test
  void gettingOutOfBedWhenSleepCannotSkipNightDoesNotPause() {
    world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
    PlayerMock waking = player();
    PlayerMock stillAsleep = player(world, 500, 500);
    waking.setSleeping(true);
    stillAsleep.setSleeping(true);

    call(new PlayerBedLeaveEvent(waking, bed(false), true));

    assertFalse(paused(stillAsleep));
  }

  @Test
  void joiningWhileSomeoneSleepsPausesEveryoneInTheWorld() {
    PlayerMock sleeper = player();
    PlayerMock joining = player(world, 500, 500);
    sleeper.setSleeping(true);

    call(new PlayerJoinEvent(joining, Component.empty()));

    assertTrue(paused(sleeper));
    assertTrue(paused(joining));
  }

  @Test
  void joiningWhileNobodySleepsDoesNotPause() {
    PlayerMock awake = player();
    PlayerMock joining = player(world, 500, 500);

    call(new PlayerJoinEvent(joining, Component.empty()));

    assertFalse(paused(awake));
    assertFalse(paused(joining));
  }

  @Test
  void leavingWhileOthersSleepPausesThem() {
    PlayerMock leaving = player();
    PlayerMock stillAsleep = player(world, 500, 500);
    leaving.setSleeping(true);
    stillAsleep.setSleeping(true);

    call(new PlayerQuitEvent(leaving, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertTrue(paused(stillAsleep));
  }

  @Test
  void lastSleeperLeavingDoesNotPause() {
    PlayerMock leaving = player();
    PlayerMock awake = player(world, 500, 500);
    leaving.setSleeping(true);

    call(new PlayerQuitEvent(leaving, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertFalse(paused(awake));
  }

  @Test
  void leavingPlayerIsNotRemembered() {
    PlayerMock leaving = player();
    PlayerMock stillAsleep = player(world, 500, 500);
    stillAsleep.setSleeping(true);

    call(new PlayerQuitEvent(leaving, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertTrue(paused(stillAsleep));
    assertFalse(paused(leaving));
  }

  @Test
  void switchingToSpectatorWhileSomeoneSleepsPauses() {
    PlayerMock sleeper = player();
    PlayerMock switching = player(world, 500, 500);
    sleeper.setSleeping(true);

    call(modeChange(switching, GameMode.SPECTATOR));

    assertTrue(paused(sleeper));
  }

  @Test
  void switchingBetweenOtherModesDoesNotPause() {
    PlayerMock sleeper = player();
    PlayerMock switching = player(world, 500, 500);
    sleeper.setSleeping(true);
    switching.setGameMode(GameMode.SURVIVAL);

    call(modeChange(switching, GameMode.CREATIVE));

    assertFalse(paused(sleeper));
  }

  @Test
  void changingWorldPausesBothWorldsWhereSomeoneSleeps() {
    PlayerMock leftBehind = player();
    PlayerMock sleeperThere = player(nether, 0, 0);
    PlayerMock moving = player(nether, 5, 5);
    leftBehind.setSleeping(true);
    sleeperThere.setSleeping(true);

    call(new PlayerChangedWorldEvent(moving, world));

    assertTrue(paused(leftBehind));
    assertTrue(paused(sleeperThere));
  }

  // Mounting

  @Test
  void mountingPausesTheRider() {
    PlayerMock player = player();
    Horse horse = world.spawn(player.getLocation(), Horse.class);

    call(new EntityMountEvent(player, horse));

    assertTrue(paused(player));
  }

  @Test
  void cancelledMountDoesNotPause() {
    PlayerMock player = player();
    EntityMountEvent event =
        new EntityMountEvent(player, world.spawn(player.getLocation(), Horse.class));
    event.setCancelled(true);

    call(event);

    assertFalse(paused(player));
  }

  // Jukeboxes

  @Test
  void startingJukeboxPausesPlayersWithin64Blocks() {
    Block jukebox = jukebox(false);
    PlayerMock clicking = player(world, 2, 0);
    PlayerMock near = player(world, 60, 0);
    PlayerMock far = player(world, 70, 0);

    call(rightClick(clicking, disc(), jukebox));

    assertTrue(paused(clicking));
    assertTrue(paused(near));
    assertFalse(paused(far));
  }

  @Test
  void clickingPlayingJukeboxDoesNotPause() {
    Block jukebox = jukebox(true);
    PlayerMock clicking = player(world, 2, 0);

    call(rightClick(clicking, disc(), jukebox));

    assertFalse(paused(clicking));
  }

  @Test
  void clickingJukeboxWithoutDiscDoesNotPause() {
    Block jukebox = jukebox(false);
    PlayerMock clicking = player(world, 2, 0);

    call(rightClick(clicking, new ItemStack(Material.STICK), jukebox));

    assertFalse(paused(clicking));
  }

  @Test
  void hopperFeedingJukeboxPausesNearbyPlayers() {
    Block jukebox = jukebox(false);
    PlayerMock near = player(world, 10, 0);
    Inventory destination = mock(Inventory.class);
    when(destination.getType()).thenReturn(InventoryType.JUKEBOX);
    when(destination.getLocation()).thenReturn(jukebox.getLocation());

    call(new InventoryMoveItemEvent(mock(Inventory.class), disc(), destination, true));

    assertTrue(paused(near));
  }

  @Test
  void hopperFeedingChestDoesNotPause() {
    PlayerMock near = player(world, 10, 0);
    Inventory destination = mock(Inventory.class);
    when(destination.getType()).thenReturn(InventoryType.CHEST);
    when(destination.getLocation()).thenReturn(new Location(world, 0, 64, 0));

    call(new InventoryMoveItemEvent(mock(Inventory.class), disc(), destination, true));

    assertFalse(paused(near));
  }

  // Locked containers

  @Test
  void lockedContainerPauses() {
    PlayerMock player = player();

    call(new BlockLockCheckEvent(chest(true), player, Component.text("Chest"), LOCKED_SOUND));

    assertTrue(paused(player));
  }

  @Test
  void openingLockedContainerWithKeyDoesNotPause() {
    PlayerMock player = player();

    call(new BlockLockCheckEvent(chest(true), player, Component.text("Chest"), LOCKED_SOUND));
    call(opened(player));

    assertFalse(paused(player));
  }

  @Test
  void openingAnotherContainerLaterKeepsPause() {
    PlayerMock player = player();
    call(new BlockLockCheckEvent(chest(true), player, Component.text("Chest"), LOCKED_SOUND));

    server.getScheduler().performOneTick();
    call(opened(player));

    assertTrue(paused(player));
  }

  @Test
  void deniedLockCheckPauses() {
    PlayerMock player = player();
    BlockLockCheckEvent event =
        new BlockLockCheckEvent(chest(false), player, Component.text("Chest"), LOCKED_SOUND);
    event.setResult(Event.Result.DENY);

    call(event);
    call(opened(player));

    assertTrue(paused(player));
  }

  @Test
  void unlockedContainerDoesNotPause() {
    PlayerMock player = player();

    call(new BlockLockCheckEvent(chest(false), player, Component.text("Chest"), LOCKED_SOUND));

    assertFalse(paused(player));
  }

  @Test
  void allowedLockCheckDoesNotPause() {
    PlayerMock player = player();
    BlockLockCheckEvent event =
        new BlockLockCheckEvent(chest(true), player, Component.text("Chest"), LOCKED_SOUND);
    event.setResult(Event.Result.ALLOW);

    call(event);

    assertFalse(paused(player));
  }

  @Test
  void spectatorsAreNotTold() {
    PlayerMock player = player();
    player.setGameMode(GameMode.SPECTATOR);

    call(new BlockLockCheckEvent(chest(true), player, Component.text("Chest"), LOCKED_SOUND));

    assertFalse(paused(player));
  }
}
