package com.nearvanilla.iceCream.modules.mapTools.events;

import static com.nearvanilla.iceCream.testutil.DisplayScenario.DRAWING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;

/**
 * What the map tools listener keeps players in edit mode from doing, and the clean up when they
 * change world or leave. The handlers that skip events other plugins already cancelled are fired
 * with an event that is spied on, to see that they never touch it.
 */
class MapToolsListenerGuardsTest {
  private DisplayScenario drawing;
  private Viewer alex;
  private Viewer sam;
  private Block block;

  @BeforeEach
  void setUp() {
    drawing = DisplayScenario.open("");
    alex = drawing.viewer();
    sam = drawing.addViewer("Sam");
    block = drawing.world().getBlockAt(5, 64, 5);
    drawing.server().getPluginManager().registerEvents(new MapToolsListener(), IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private void startEditing() {
    MapToolsUtils.startEditing(alex, EditSession.view());
  }

  private void fire(Event event) {
    drawing.server().getPluginManager().callEvent(event);
  }

  /** The event is cancelled for a player in edit mode and left alone for anyone else. */
  private <E extends Event & Cancellable> void assertCancelledOnlyInEditMode(Supplier<E> factory) {
    E idle = factory.get();
    fire(idle);
    assertFalse(idle.isCancelled(), "cancelled for a player who is not in edit mode");

    startEditing();
    E busy = factory.get();
    fire(busy);
    assertTrue(busy.isCancelled(), "not cancelled for a player in edit mode");
  }

  /** The handler never touches an event that is already cancelled, even in edit mode. */
  private <E extends Event & Cancellable> void assertIgnoresCancelledEvents(Supplier<E> factory) {
    startEditing();
    E event = spy(factory.get());
    event.setCancelled(true);
    clearInvocations(event);

    fire(event);

    verify(event, never()).setCancelled(anyBoolean());
  }

  private static DamageSource source() {
    return mock(DamageSource.class);
  }

  // Blocks

  private BlockBreakEvent breakEvent() {
    return new BlockBreakEvent(block, alex);
  }

  private BlockPlaceEvent placeEvent() {
    return new BlockPlaceEvent(
        block,
        block.getState(),
        drawing.world().getBlockAt(5, 63, 5),
        new ItemStack(Material.STONE),
        alex,
        true);
  }

  @Test
  void breakingBlocksIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(this::breakEvent);
  }

  @Test
  void aBreakAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(this::breakEvent);
  }

  @Test
  void placingBlocksIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(this::placeEvent);
  }

  @Test
  void aPlacementAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(this::placeEvent);
  }

  @Test
  void emptyingABucketIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(this::emptyBucket);
  }

  @Test
  void aBucketEmptyingAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(this::emptyBucket);
  }

  @Test
  void fillingABucketIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(this::fillBucket);
  }

  @Test
  void aBucketFillingAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(this::fillBucket);
  }

  private PlayerBucketEmptyEvent emptyBucket() {
    return new PlayerBucketEmptyEvent(
        alex,
        block,
        block,
        BlockFace.UP,
        Material.WATER_BUCKET,
        new ItemStack(Material.WATER_BUCKET));
  }

  private PlayerBucketFillEvent fillBucket() {
    return new PlayerBucketFillEvent(
        alex, block, block, BlockFace.UP, Material.BUCKET, new ItemStack(Material.BUCKET));
  }

  // Entities

  @Test
  void rightClickingAnEntityIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(() -> new PlayerInteractEntityEvent(alex, sam));
  }

  @Test
  void anEntityClickAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(() -> new PlayerInteractEntityEvent(alex, sam));
  }

  @Test
  void rightClickingAPointOnAnEntityIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(() -> new PlayerInteractAtEntityEvent(alex, sam, new Vector()));
  }

  @Test
  void aClickAtAnEntityAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(() -> new PlayerInteractAtEntityEvent(alex, sam, new Vector()));
  }

  @Test
  void changingAnArmorStandIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(this::armorStandEvent);
  }

  @Test
  void anArmorStandChangeAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(this::armorStandEvent);
  }

  private PlayerArmorStandManipulateEvent armorStandEvent() {
    return new PlayerArmorStandManipulateEvent(
        alex,
        mock(ArmorStand.class),
        new ItemStack(Material.STONE),
        new ItemStack(Material.AIR),
        EquipmentSlot.HEAD);
  }

  // Attacks

  private EntityDamageByEntityEvent hit(Entity damager) {
    return new EntityDamageByEntityEvent(damager, sam, DamageCause.ENTITY_ATTACK, source(), 1.0);
  }

  @Test
  void hittingAnEntityIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(() -> hit(alex));
  }

  @Test
  void aHitAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(() -> hit(alex));
  }

  @Test
  void aHitByAPlayerNotInEditModeIsLeftAloneWhileAnotherIsEditing() {
    startEditing();

    EntityDamageByEntityEvent event = hit(sam);
    fire(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void aHitByAnEntityThatIsNotAPlayerIsLeftAlone() {
    startEditing();
    Zombie zombie = mock(Zombie.class);

    EntityDamageByEntityEvent event = hit(zombie);
    fire(event);

    assertFalse(event.isCancelled());
  }

  // Item frames, paintings, boats and minecarts

  private HangingBreakByEntityEvent breakFrame(Entity remover) {
    return new HangingBreakByEntityEvent(mock(ItemFrame.class), remover, source());
  }

  @Test
  void breakingAnItemFrameIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(() -> breakFrame(alex));
  }

  @Test
  void anItemFrameBreakAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(() -> breakFrame(alex));
  }

  @Test
  void anItemFrameBrokenByAnotherPlayerOrAnythingElseIsLeftAlone() {
    startEditing();

    HangingBreakByEntityEvent byPlayer = breakFrame(sam);
    HangingBreakByEntityEvent byMob = breakFrame(mock(Zombie.class));
    fire(byPlayer);
    fire(byMob);

    assertFalse(byPlayer.isCancelled());
    assertFalse(byMob.isCancelled());
  }

  private VehicleDamageEvent hitCart(Entity attacker) {
    return new VehicleDamageEvent(mock(Minecart.class), source(), attacker, 1.0);
  }

  @Test
  void damagingAVehicleIsCancelledInEditMode() {
    assertCancelledOnlyInEditMode(() -> hitCart(alex));
  }

  @Test
  void aVehicleHitAnotherPluginCancelledIsLeftAlone() {
    assertIgnoresCancelledEvents(() -> hitCart(alex));
  }

  @Test
  void aVehicleDamagedByAnotherPlayerOrAnythingElseIsLeftAlone() {
    startEditing();

    VehicleDamageEvent byPlayer = hitCart(sam);
    VehicleDamageEvent byMob = hitCart(mock(Zombie.class));
    VehicleDamageEvent byNothing = hitCart(null);
    fire(byPlayer);
    fire(byMob);
    fire(byNothing);

    assertFalse(byPlayer.isCancelled());
    assertFalse(byMob.isCancelled());
    assertFalse(byNothing.isCancelled());
  }

  // Changing world and leaving

  @Test
  void changingWorldRedrawsThePlayersAreasForTheNewWorld() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);
    startEditing();
    assertFalse(drawing.world().displays().isEmpty());
    alex.setLocation(new Location(drawing.scenario().overworld(), 0, 64, 0));

    fire(new PlayerChangedWorldEvent(alex, drawing.world()));

    assertTrue(drawing.world().displays().isEmpty());
  }

  @Test
  void changingWorldBackDrawsTheAreasAgain() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);
    startEditing();
    alex.setLocation(new Location(drawing.scenario().overworld(), 0, 64, 0));
    fire(new PlayerChangedWorldEvent(alex, drawing.world()));
    alex.setLocation(new Location(drawing.world(), 0, 64, 0));

    fire(new PlayerChangedWorldEvent(alex, drawing.scenario().overworld()));

    assertEquals(4, drawing.ghosts().size());
  }

  @Test
  void leavingTheServerEndsEditModeAndRemovesTheDrawing() {
    drawing.stored(MapToolsTestUtils.rect("home", DRAWING, 10, 10, 19, 19));
    drawing.world().loadChunks(0, 0, 40, 40);
    startEditing();
    MapToolsUtils.startEditing(sam, EditSession.view());

    fire(new PlayerQuitEvent(alex, "left"));

    assertFalse(MapToolsUtils.isEditing(alex));
    assertTrue(MapToolsUtils.isEditing(sam), "another player's edit mode is left alone");
    assertTrue(alex.visibleDisplays().isEmpty());
    assertFalse(sam.visibleDisplays().isEmpty());
  }

  @Test
  void aPlayerWhoWasNotEditingCanLeaveQuietly() {
    fire(new PlayerQuitEvent(alex, "left"));

    assertFalse(MapToolsModule.sessions.containsKey(alex.getUniqueId()));
  }

  // Worlds that load after startup

  @Test
  void aWorldLoadedLaterGetsTheAreasLayer() {
    @SuppressWarnings("unchecked")
    Registry<LayerProvider> layers = mock(Registry.class);
    MapWorld mapWorld = mock(MapWorld.class);
    when(mapWorld.layerRegistry()).thenReturn(layers);
    Squaremap api = MapToolsTestUtils.emptySquaremap();
    MapToolsUtils.init(api);
    verify(layers, never()).register(any(Key.class), any(LayerProvider.class));
    when(api.getWorldIfEnabled(any(WorldIdentifier.class))).thenReturn(Optional.of(mapWorld));

    fire(new WorldLoadEvent(drawing.scenario().nether()));

    verify(layers, times(2)).register(any(Key.class), any(LayerProvider.class));
  }
}
