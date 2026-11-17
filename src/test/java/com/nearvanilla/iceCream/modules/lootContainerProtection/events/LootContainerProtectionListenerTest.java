package com.nearvanilla.iceCream.modules.lootContainerProtection.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LootableTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
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
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;
import org.mockbukkit.mockbukkit.entity.CreeperMock;
import org.mockbukkit.mockbukkit.entity.HopperMinecartMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.StorageMinecartMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

class LootContainerProtectionListenerTest {
  private static final String SNEAK_MESSAGE = "You need to sneak to break loot containers!";

  private ServerMock server;
  private World world;
  private PlayerMock player;
  private NamespacedKey originalTableKey;
  private LootTable dungeonTable;
  private int nextX;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Looter");
    originalTableKey = new NamespacedKey(IceCream.instance, "lootcontainer.original_table");
    dungeonTable = LootableTestUtils.lootTable("chests/simple_dungeon");
    server
        .getPluginManager()
        .registerEvents(new LootContainerProtectionListener(originalTableKey), IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private <E extends Event> E fire(E event) {
    server.getPluginManager().callEvent(event);
    return event;
  }

  private Block lootChest() {
    return LootableTestUtils.chest(world, nextX++, 64, 0, dungeonTable);
  }

  private Block ordinaryChest() {
    return LootableTestUtils.chest(world, nextX++, 64, 0, null);
  }

  // A chest whose loot was already generated: the table is gone and only the saved copy is left.
  private Block openedChest() {
    Block block = ordinaryChest();
    saveOriginalTable(block, "minecraft:chests/simple_dungeon");
    return block;
  }

  private Block stone() {
    Block block = world.getBlockAt(nextX++, 64, 0);
    block.setType(Material.STONE);
    return block;
  }

  private void saveOriginalTable(Block block, String table) {
    ((TileState) block.getState())
        .getPersistentDataContainer()
        .set(originalTableKey, PersistentDataType.STRING, table);
  }

  private String savedTable(Block block) {
    return ((TileState) block.getState())
        .getPersistentDataContainer()
        .get(originalTableKey, PersistentDataType.STRING);
  }

  private String savedTable(StorageMinecartMock cart) {
    return cart.getPersistentDataContainer().get(originalTableKey, PersistentDataType.STRING);
  }

  // A block that only hands out the state of a real block, so a test can check that a handler
  // never looked at it.
  private Block watched(Block real) {
    Block block = mock(Block.class);
    when(block.getState(false)).thenReturn(real.getState(false));
    return block;
  }

  private DamageSource damageFrom(Entity direct) {
    if (direct == null) {
      return DamageSource.builder(DamageType.GENERIC).build();
    }
    return DamageSource.builder(DamageType.PLAYER_ATTACK)
        .withDirectEntity(direct)
        .withCausingEntity(direct)
        .build();
  }

  private ArrowMock arrowShotBy(ProjectileSource shooter) {
    ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
    arrow.setShooter(shooter);
    return arrow;
  }

  private ZombieMock zombie() {
    return new ZombieMock(server, UUID.randomUUID());
  }

  private LootGenerateEvent lootGenerated(
      InventoryHolder holder, LootTable table, boolean plugin, boolean cancelled) {
    LootGenerateEvent event =
        new LootGenerateEvent(
            world, null, holder, table, mock(LootContext.class), new ArrayList<>(), plugin);
    event.setCancelled(cancelled);
    return event;
  }

  private LootGenerateEvent lootGenerated(InventoryHolder holder, LootTable table) {
    return lootGenerated(holder, table, false, false);
  }

  private BlockBreakEvent breakEvent(Block block) {
    return new BlockBreakEvent(block, player);
  }

  private void assertNoMessage() {
    assertNull(nextMessage(player));
  }

  // Loot generation

  @Test
  void savesTheTableOfAChestWhenItsLootIsGenerated() {
    Block chest = lootChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable));

    assertEquals("minecraft:chests/simple_dungeon", savedTable(chest));
  }

  @Test
  void savesTheTableOfAChestWhoseAssignedTableWasAlreadyConsumed() {
    Block chest = ordinaryChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable));

    assertEquals("minecraft:chests/simple_dungeon", savedTable(chest));
  }

  @Test
  void keepsTheFirstTableSavedForAChest() {
    Block chest = openedChest();
    saveOriginalTable(chest, "minecraft:chests/abandoned_mineshaft");

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable));

    assertEquals("minecraft:chests/abandoned_mineshaft", savedTable(chest));
  }

  @Test
  void savesTheTableOfAStorageMinecartWhenItsLootIsGenerated() {
    StorageMinecartMock cart = LootableTestUtils.storageMinecart(server, dungeonTable);

    fire(lootGenerated(cart, dungeonTable));

    assertEquals("minecraft:chests/simple_dungeon", savedTable(cart));
  }

  @Test
  void keepsTheFirstTableSavedForAStorageMinecart() {
    StorageMinecartMock cart = LootableTestUtils.storageMinecart(server, dungeonTable);
    cart.getPersistentDataContainer()
        .set(originalTableKey, PersistentDataType.STRING, "minecraft:chests/abandoned_mineshaft");

    fire(lootGenerated(cart, dungeonTable));

    assertEquals("minecraft:chests/abandoned_mineshaft", savedTable(cart));
  }

  @Test
  void savesTheTableEvenWhenAnotherPluginCancelsTheLoot() {
    Block chest = lootChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable, false, true));

    assertEquals("minecraft:chests/simple_dungeon", savedTable(chest));
  }

  @Test
  void savesTheTableWhenAPluginFillsAContainerThatHasALootTable() {
    Block chest = lootChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable, true, false));

    assertEquals("minecraft:chests/simple_dungeon", savedTable(chest));
  }

  @Test
  void ignoresAPluginFillingAnOrdinaryChest() {
    Block chest = ordinaryChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable, true, false));

    assertNull(savedTable(chest));
  }

  @Test
  void ignoresAPluginFillingAnOrdinaryStorageMinecart() {
    StorageMinecartMock cart = LootableTestUtils.storageMinecart(server, null);

    fire(lootGenerated(cart, dungeonTable, true, false));

    assertNull(savedTable(cart));
  }

  @Test
  void ignoresACancelledPluginLoot() {
    Block chest = lootChest();

    fire(lootGenerated((InventoryHolder) chest.getState(false), dungeonTable, true, true));

    assertNull(savedTable(chest));
  }

  @Test
  void ignoresACancelledPluginLootForAStorageMinecart() {
    StorageMinecartMock cart = LootableTestUtils.storageMinecart(server, dungeonTable);

    fire(lootGenerated(cart, dungeonTable, true, true));

    assertNull(savedTable(cart));
  }

  @Test
  void ignoresAChestThatWasReplacedByAnotherContainer() {
    Block chest = lootChest();
    InventoryHolder holder = (InventoryHolder) chest.getState(false);
    chest.setType(Material.BARREL);

    fire(lootGenerated(holder, dungeonTable));

    assertNull(savedTable(chest));
  }

  @Test
  void ignoresAChestThatWasReplacedByAPlainBlock() {
    Block chest = lootChest();
    InventoryHolder holder = (InventoryHolder) chest.getState(false);
    chest.setType(Material.STONE);

    fire(lootGenerated(holder, dungeonTable));

    assertEquals(Material.STONE, chest.getType());
  }

  @Test
  void ignoresAChestStateThatIsNotPlacedInTheWorld() {
    var unplaced = LootableTestUtils.unplacedChest(dungeonTable);

    fire(lootGenerated(unplaced, dungeonTable));

    assertNull(
        unplaced.getPersistentDataContainer().get(originalTableKey, PersistentDataType.STRING));
  }

  @Test
  void ignoresLootGeneratedWithoutAContainer() {
    LootGenerateEvent event = fire(lootGenerated(null, dungeonTable));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresLootGeneratedForAnInventoryThatIsNotAContainer() {
    fire(lootGenerated(player, dungeonTable));

    assertFalse(player.getPersistentDataContainer().has(originalTableKey));
  }

  // Breaking blocks

  @Test
  void cancelsBreakingALootChest() {
    BlockBreakEvent event = fire(breakEvent(lootChest()));

    assertTrue(event.isCancelled());
  }

  @Test
  void tellsAPlayerToSneakWhenBreakingALootChest() {
    fire(breakEvent(lootChest()));

    assertEquals(SNEAK_MESSAGE, nextMessage(player));
    assertNoMessage();
  }

  @Test
  void allowsASneakingPlayerToBreakALootChest() {
    player.setSneaking(true);

    BlockBreakEvent event = fire(breakEvent(lootChest()));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsBreakingAChestWhoseLootWasAlreadyGenerated() {
    BlockBreakEvent event = fire(breakEvent(openedChest()));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
  }

  @Test
  void allowsBreakingAnOrdinaryChest() {
    BlockBreakEvent event = fire(breakEvent(ordinaryChest()));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void allowsBreakingAPlainBlock() {
    BlockBreakEvent event = fire(breakEvent(stone()));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void ignoresABreakThatIsAlreadyCancelled() {
    BlockBreakEvent event = breakEvent(lootChest());
    event.setCancelled(true);

    fire(event);

    assertNoMessage();
  }

  // Destroying and damaging minecarts

  private VehicleDestroyEvent destroyEvent(StorageMinecartMock cart, Entity attacker) {
    return new VehicleDestroyEvent(cart, damageFrom(attacker), attacker);
  }

  private VehicleDamageEvent damageEvent(StorageMinecartMock cart, Entity attacker) {
    return new VehicleDamageEvent(cart, damageFrom(attacker), attacker, 1.0);
  }

  private StorageMinecartMock lootCart() {
    return LootableTestUtils.storageMinecart(server, dungeonTable);
  }

  @Test
  void cancelsAPlayerDestroyingALootMinecart() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), player));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
    assertNoMessage();
  }

  @Test
  void allowsASneakingPlayerToDestroyALootMinecart() {
    player.setSneaking(true);

    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAMinecartThatLostItsTableButKeepsTheSavedOne() {
    StorageMinecartMock cart = LootableTestUtils.storageMinecart(server, null);
    cart.getPersistentDataContainer()
        .set(originalTableKey, PersistentDataType.STRING, "minecraft:chests/simple_dungeon");

    VehicleDestroyEvent event = fire(destroyEvent(cart, player));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAMobDestroyingALootMinecartWithoutAMessage() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), zombie()));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsDestroyingALootMinecartWithNoAttacker() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), null));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAnArrowFromAPlayerDestroyingALootMinecart() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), arrowShotBy(player)));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
  }

  @Test
  void allowsAnArrowFromASneakingPlayerToDestroyALootMinecart() {
    player.setSneaking(true);

    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), arrowShotBy(player)));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAnArrowFromAMobDestroyingALootMinecart() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), arrowShotBy(zombie())));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAnArrowWithNoShooterDestroyingALootMinecart() {
    VehicleDestroyEvent event = fire(destroyEvent(lootCart(), arrowShotBy(null)));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsDestroyingAnOrdinaryMinecart() {
    VehicleDestroyEvent event =
        fire(destroyEvent(LootableTestUtils.storageMinecart(server, null), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void allowsDestroyingAMinecartThatIsNotAStorageMinecart() {
    HopperMinecartMock hopperCart = new HopperMinecartMock(server, UUID.randomUUID());

    VehicleDestroyEvent event =
        fire(new VehicleDestroyEvent(hopperCart, damageFrom(player), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void ignoresAMinecartDestroyedByAnEventThatIsAlreadyCancelled() {
    VehicleDestroyEvent event = destroyEvent(lootCart(), player);
    event.setCancelled(true);

    fire(event);

    assertNoMessage();
  }

  @Test
  void cancelsAPlayerDamagingALootMinecart() {
    VehicleDamageEvent event = fire(damageEvent(lootCart(), player));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
    assertNoMessage();
  }

  @Test
  void allowsASneakingPlayerToDamageALootMinecart() {
    player.setSneaking(true);

    VehicleDamageEvent event = fire(damageEvent(lootCart(), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAMobDamagingALootMinecartWithoutAMessage() {
    VehicleDamageEvent event = fire(damageEvent(lootCart(), zombie()));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAnArrowFromAPlayerDamagingALootMinecart() {
    VehicleDamageEvent event = fire(damageEvent(lootCart(), arrowShotBy(player)));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
  }

  @Test
  void allowsDamagingAnOrdinaryMinecart() {
    VehicleDamageEvent event =
        fire(damageEvent(LootableTestUtils.storageMinecart(server, null), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void ignoresMinecartDamageThatIsAlreadyCancelled() {
    VehicleDamageEvent event = damageEvent(lootCart(), player);
    event.setCancelled(true);

    fire(event);

    assertNoMessage();
  }

  // Damaging entities

  private EntityDamageEvent entityDamage(Entity victim, Entity direct) {
    return entityDamageFrom(victim, damageFrom(direct));
  }

  @SuppressWarnings("removal")
  private EntityDamageEvent entityDamageFrom(Entity victim, DamageSource source) {
    return new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1.0);
  }

  @Test
  void cancelsDamageFromAMobThatAPlayerIsResponsibleForWithoutAMessage() {
    DamageSource source =
        DamageSource.builder(DamageType.MOB_ATTACK)
            .withDirectEntity(zombie())
            .withCausingEntity(player)
            .build();
    EntityDamageEvent event = fire(entityDamageFrom(lootCart(), source));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAPlayerHittingALootMinecart() {
    EntityDamageEvent event = fire(entityDamage(lootCart(), player));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
    assertNoMessage();
  }

  @Test
  void allowsASneakingPlayerToHitALootMinecart() {
    player.setSneaking(true);

    EntityDamageEvent event = fire(entityDamage(lootCart(), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsAnArrowFromAPlayerHittingALootMinecart() {
    EntityDamageEvent event = fire(entityDamage(lootCart(), arrowShotBy(player)));

    assertTrue(event.isCancelled());
    assertEquals(SNEAK_MESSAGE, nextMessage(player));
  }

  @Test
  void allowsAnArrowFromASneakingPlayerToHitALootMinecart() {
    player.setSneaking(true);

    EntityDamageEvent event = fire(entityDamage(lootCart(), arrowShotBy(player)));

    assertFalse(event.isCancelled());
  }

  @Test
  void cancelsDamageToALootMinecartFromAMob() {
    EntityDamageEvent event = fire(entityDamage(lootCart(), zombie()));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void cancelsDamageToALootMinecartWithNoAttacker() {
    EntityDamageEvent event = fire(entityDamage(lootCart(), null));

    assertTrue(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void allowsDamageToAnOrdinaryMinecart() {
    EntityDamageEvent event =
        fire(entityDamage(LootableTestUtils.storageMinecart(server, null), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void allowsDamageToAnEntityThatIsNotAContainer() {
    EntityDamageEvent event = fire(entityDamage(zombie(), player));

    assertFalse(event.isCancelled());
    assertNoMessage();
  }

  @Test
  void ignoresEntityDamageThatIsAlreadyCancelled() {
    EntityDamageEvent event = entityDamage(lootCart(), player);
    event.setCancelled(true);

    fire(event);

    assertNoMessage();
  }

  // Explosions

  private EntityExplodeEvent entityExplosion(List<Block> blocks) {
    return new EntityExplodeEvent(
        new CreeperMock(server, UUID.randomUUID()),
        new Location(world, 0, 64, 0),
        blocks,
        1.0f,
        ExplosionResult.DESTROY);
  }

  private BlockExplodeEvent blockExplosion(List<Block> blocks) {
    Block source = stone();
    return new BlockExplodeEvent(source, source.getState(), blocks, 1.0f, ExplosionResult.DESTROY);
  }

  @Test
  void keepsLootContainersOutOfAnEntityExplosion() {
    Block loot = lootChest();
    Block opened = openedChest();
    Block ordinary = ordinaryChest();
    Block plain = stone();

    EntityExplodeEvent event =
        fire(entityExplosion(new ArrayList<>(List.of(loot, ordinary, opened, plain))));

    assertEquals(List.of(ordinary, plain), event.blockList());
  }

  @Test
  void leavesAnEntityExplosionWithoutLootContainersAlone() {
    Block ordinary = ordinaryChest();
    Block plain = stone();

    EntityExplodeEvent event = fire(entityExplosion(new ArrayList<>(List.of(ordinary, plain))));

    assertEquals(List.of(ordinary, plain), event.blockList());
  }

  @Test
  void ignoresAnEntityExplosionThatIsAlreadyCancelled() {
    Block loot = lootChest();
    EntityExplodeEvent event = entityExplosion(new ArrayList<>(List.of(loot)));
    event.setCancelled(true);

    fire(event);

    assertEquals(List.of(loot), event.blockList());
  }

  @Test
  void keepsLootContainersOutOfABlockExplosion() {
    Block loot = lootChest();
    Block opened = openedChest();
    Block ordinary = ordinaryChest();
    Block plain = stone();

    BlockExplodeEvent event =
        fire(blockExplosion(new ArrayList<>(List.of(loot, ordinary, opened, plain))));

    assertEquals(List.of(ordinary, plain), event.blockList());
  }

  @Test
  void leavesABlockExplosionWithoutLootContainersAlone() {
    Block ordinary = ordinaryChest();
    Block plain = stone();

    BlockExplodeEvent event = fire(blockExplosion(new ArrayList<>(List.of(ordinary, plain))));

    assertEquals(List.of(ordinary, plain), event.blockList());
  }

  @Test
  void ignoresABlockExplosionThatIsAlreadyCancelled() {
    Block loot = lootChest();
    BlockExplodeEvent event = blockExplosion(new ArrayList<>(List.of(loot)));
    event.setCancelled(true);

    fire(event);

    assertEquals(List.of(loot), event.blockList());
  }

  // Burning, changing and destroying blocks

  private BlockBurnEvent burn(Block block) {
    return new BlockBurnEvent(block, null);
  }

  @Test
  void cancelsALootChestBurning() {
    BlockBurnEvent event = fire(burn(lootChest()));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAChestWhoseLootWasGeneratedBurning() {
    BlockBurnEvent event = fire(burn(openedChest()));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsAnOrdinaryChestBurning() {
    BlockBurnEvent event = fire(burn(ordinaryChest()));

    assertFalse(event.isCancelled());
  }

  @Test
  void allowsAPlainBlockBurning() {
    BlockBurnEvent event = fire(burn(stone()));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresABurnThatIsAlreadyCancelled() {
    Block watched = watched(lootChest());
    BlockBurnEvent event = burn(watched);
    event.setCancelled(true);

    fire(event);

    verify(watched, never()).getState(anyBoolean());
  }

  private EntityChangeBlockEvent changeBlock(Block block) {
    return new EntityChangeBlockEvent(zombie(), block, Material.AIR.createBlockData());
  }

  @Test
  void cancelsAnEntityChangingALootChest() {
    EntityChangeBlockEvent event = fire(changeBlock(lootChest()));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsAnEntityChangingAnOrdinaryChest() {
    EntityChangeBlockEvent event = fire(changeBlock(ordinaryChest()));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresABlockChangeThatIsAlreadyCancelled() {
    Block watched = watched(lootChest());
    EntityChangeBlockEvent event = changeBlock(watched);
    event.setCancelled(true);

    fire(event);

    verify(watched, never()).getState(anyBoolean());
  }

  private BlockDestroyEvent destroyBlock(Block block) {
    return new BlockDestroyEvent(
        block, Material.AIR.createBlockData(), Material.AIR.createBlockData(), 0, true);
  }

  @Test
  void cancelsALootChestBeingDestroyed() {
    BlockDestroyEvent event = fire(destroyBlock(lootChest()));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsAnOrdinaryChestBeingDestroyed() {
    BlockDestroyEvent event = fire(destroyBlock(ordinaryChest()));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresADestroyThatIsAlreadyCancelled() {
    Block watched = watched(lootChest());
    BlockDestroyEvent event = destroyBlock(watched);
    event.setCancelled(true);

    fire(event);

    verify(watched, never()).getState(anyBoolean());
  }

  // Pistons

  private BlockPistonExtendEvent extend(List<Block> blocks) {
    return new BlockPistonExtendEvent(stone(), blocks, BlockFace.UP);
  }

  private BlockPistonRetractEvent retract(List<Block> blocks) {
    return new BlockPistonRetractEvent(stone(), blocks, BlockFace.UP);
  }

  @Test
  void cancelsAPistonPushingALootChest() {
    BlockPistonExtendEvent event = fire(extend(List.of(lootChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAPistonPushingALootChestAmongOtherBlocks() {
    BlockPistonExtendEvent event = fire(extend(List.of(stone(), ordinaryChest(), lootChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAPistonPushingAChestWhoseLootWasGenerated() {
    BlockPistonExtendEvent event = fire(extend(List.of(openedChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsAPistonPushingOrdinaryBlocks() {
    BlockPistonExtendEvent event = fire(extend(List.of(stone(), ordinaryChest())));

    assertFalse(event.isCancelled());
  }

  @Test
  void allowsAPistonPushingNothing() {
    BlockPistonExtendEvent event = fire(extend(List.of()));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresAPistonPushThatIsAlreadyCancelled() {
    Block watched = watched(lootChest());
    BlockPistonExtendEvent event = extend(List.of(watched));
    event.setCancelled(true);

    fire(event);

    verify(watched, never()).getState(anyBoolean());
  }

  @Test
  void cancelsAPistonPullingALootChest() {
    BlockPistonRetractEvent event = fire(retract(List.of(lootChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAPistonPullingALootChestAmongOtherBlocks() {
    BlockPistonRetractEvent event = fire(retract(List.of(stone(), ordinaryChest(), lootChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void cancelsAPistonPullingAChestWhoseLootWasGenerated() {
    BlockPistonRetractEvent event = fire(retract(List.of(openedChest())));

    assertTrue(event.isCancelled());
  }

  @Test
  void allowsAPistonPullingOrdinaryBlocks() {
    BlockPistonRetractEvent event = fire(retract(List.of(stone(), ordinaryChest())));

    assertFalse(event.isCancelled());
  }

  @Test
  void allowsAPistonPullingNothing() {
    BlockPistonRetractEvent event = fire(retract(List.of()));

    assertFalse(event.isCancelled());
  }

  @Test
  void ignoresAPistonPullThatIsAlreadyCancelled() {
    Block watched = watched(lootChest());
    BlockPistonRetractEvent event = retract(List.of(watched));
    event.setCancelled(true);

    fire(event);

    verify(watched, never()).getState(anyBoolean());
  }
}
