package com.nearvanilla.iceCream.modules.bonemealableSporeBlossoms.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import java.util.Collection;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class SporeBlossomBonemealEventTest {
  private ServerMock server;
  // A spy, because the growth effects only go out to the world and cannot be read back from it.
  private WorldMock world;
  private Block blossom;
  private PlayerMock player;
  private Inventory dispenserInventory;
  private final SporeBlossomBonemealEvent listener = new SporeBlossomBonemealEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    world = spy(new WorldMock());
    world.setName("world");
    server.addWorld(world);
    blossom = world.getBlockAt(5, 70, 5);
    blossom.setType(Material.SPORE_BLOSSOM);
    player = server.addPlayer();
    player.setGameMode(GameMode.SURVIVAL);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Location centreOfBlossom() {
    return new Location(world, 5.5, 70.5, 5.5);
  }

  private PlayerInteractEvent click(
      Action action, ItemStack item, Block block, EquipmentSlot hand) {
    return new PlayerInteractEvent(player, action, item, block, BlockFace.DOWN, hand);
  }

  private PlayerInteractEvent rightClickBlossomWith(ItemStack item, EquipmentSlot hand) {
    return click(Action.RIGHT_CLICK_BLOCK, item, blossom, hand);
  }

  private Collection<Item> droppedItems() {
    return world.getEntitiesByClass(Item.class);
  }

  private void assertGrew() {
    assertEquals(1, droppedItems().size());
    Item dropped = droppedItems().iterator().next();
    assertEquals(new ItemStack(Material.SPORE_BLOSSOM), dropped.getItemStack());
    verify(world).playEffect(centreOfBlossom(), Effect.BONE_MEAL_USE, 0);
    verify(world).spawnParticle(Particle.HAPPY_VILLAGER, centreOfBlossom(), 15, 0.5, 0.5, 0.5, 0);
  }

  private void assertDidNotGrow() {
    assertTrue(droppedItems().isEmpty());
    verify(world, never()).playEffect(centreOfBlossom(), Effect.BONE_MEAL_USE, 0);
  }

  @Test
  void bonemealOnASporeBlossomDropsABlossomAndConsumesOneBonemeal() {
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 3);
    PlayerInteractEvent event = rightClickBlossomWith(boneMeal, EquipmentSlot.HAND);

    listener.onPlayerInteract(event);

    assertEquals(2, boneMeal.getAmount());
    assertEquals(Event.Result.DENY, event.useItemInHand());
    assertGrew();
  }

  @Test
  void consumingTheLastBonemealEmptiesTheStack() {
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 1);

    listener.onPlayerInteract(rightClickBlossomWith(boneMeal, EquipmentSlot.HAND));

    assertEquals(0, boneMeal.getAmount());
    assertGrew();
  }

  @Test
  void creativePlayersDoNotUseUpTheirBonemeal() {
    player.setGameMode(GameMode.CREATIVE);
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 3);

    listener.onPlayerInteract(rightClickBlossomWith(boneMeal, EquipmentSlot.HAND));

    assertEquals(3, boneMeal.getAmount());
    assertGrew();
  }

  @Test
  void onlyOneBlossomDropsPerUse() {
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 64);

    listener.onPlayerInteract(rightClickBlossomWith(boneMeal, EquipmentSlot.HAND));

    assertEquals(1, droppedItems().size());
    assertEquals(1, droppedItems().iterator().next().getItemStack().getAmount());
  }

  @Test
  void offHandBonemealWorksWhenTheMainHandHoldsSomethingElse() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

    PlayerInteractEvent event = rightClickBlossomWith(boneMeal, EquipmentSlot.OFF_HAND);
    listener.onPlayerInteract(event);

    assertEquals(1, boneMeal.getAmount());
    assertEquals(Event.Result.DENY, event.useItemInHand());
    assertGrew();
  }

  @Test
  void offHandBonemealIsIgnoredWhenTheMainHandAlsoHoldsBonemeal() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.BONE_MEAL, 5));
    ItemStack offHand = new ItemStack(Material.BONE_MEAL, 2);

    PlayerInteractEvent event = rightClickBlossomWith(offHand, EquipmentSlot.OFF_HAND);
    listener.onPlayerInteract(event);

    assertEquals(2, offHand.getAmount());
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertDidNotGrow();
  }

  @Test
  void aClickWithoutAHandStillGrowsTheBlossom() {
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

    listener.onPlayerInteract(rightClickBlossomWith(boneMeal, null));

    assertEquals(1, boneMeal.getAmount());
    assertGrew();
  }

  @Test
  void otherActionsAreIgnored() {
    for (Action action :
        new Action[] {
          Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.RIGHT_CLICK_AIR, Action.PHYSICAL
        }) {
      ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

      listener.onPlayerInteract(click(action, boneMeal, blossom, EquipmentSlot.HAND));

      assertEquals(2, boneMeal.getAmount(), action.name());
    }
    assertDidNotGrow();
  }

  @Test
  void otherBlocksAreIgnored() {
    Block grass = world.getBlockAt(8, 70, 8);
    grass.setType(Material.SHORT_GRASS);
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

    PlayerInteractEvent event =
        click(Action.RIGHT_CLICK_BLOCK, boneMeal, grass, EquipmentSlot.HAND);
    listener.onPlayerInteract(event);

    assertEquals(2, boneMeal.getAmount());
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertDidNotGrow();
  }

  @Test
  void aMissingBlockIsIgnored() {
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 2);

    listener.onPlayerInteract(click(Action.RIGHT_CLICK_BLOCK, boneMeal, null, EquipmentSlot.HAND));

    assertEquals(2, boneMeal.getAmount());
    assertDidNotGrow();
  }

  @Test
  void otherItemsAreIgnored() {
    ItemStack stick = new ItemStack(Material.STICK, 2);

    PlayerInteractEvent event = rightClickBlossomWith(stick, EquipmentSlot.HAND);
    listener.onPlayerInteract(event);

    assertEquals(2, stick.getAmount());
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertDidNotGrow();
  }

  @Test
  void anEmptyHandIsIgnored() {
    PlayerInteractEvent event = rightClickBlossomWith(null, EquipmentSlot.HAND);

    listener.onPlayerInteract(event);

    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertDidNotGrow();
  }

  @Test
  void registeredWithTheServerItGrowsTheBlossom() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 3);

    server.getPluginManager().callEvent(rightClickBlossomWith(boneMeal, EquipmentSlot.HAND));

    assertEquals(2, boneMeal.getAmount());
    assertGrew();
  }

  @Test
  void registeredWithTheServerItSkipsAnInteractionAnotherPluginCancelled() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 3);
    PlayerInteractEvent event = rightClickBlossomWith(boneMeal, EquipmentSlot.HAND);
    event.setCancelled(true);

    server.getPluginManager().callEvent(event);

    assertEquals(3, boneMeal.getAmount());
    assertDidNotGrow();
  }

  // Dispensers

  // MockBukkit does not return a Dispenser from getState(), so the block hands out a stub whose
  // inventory is a real one, which keeps the slot handling under test real.
  private Block dispenserFacing(BlockFace face, int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    Directional data = (Directional) Material.DISPENSER.createBlockData();
    data.setFacing(face);
    block.setBlockData(data);

    dispenserInventory = server.createInventory(null, InventoryType.DISPENSER);
    Dispenser state = mock(Dispenser.class);
    when(state.getInventory()).thenReturn(dispenserInventory);
    Block dispenser = spy(block);
    doReturn(state).when(dispenser).getState();
    return dispenser;
  }

  private Block dispenserBelowTheBlossom() {
    return dispenserFacing(BlockFace.UP, 5, 69, 5);
  }

  @Test
  void aDispenserFacingASporeBlossomUsesOneBonemealOnIt() {
    Block dispenser = dispenserBelowTheBlossom();
    dispenserInventory.setItem(3, new ItemStack(Material.BONE_MEAL, 4));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 4), 3);

    listener.onBlockPreDispense(event);

    assertTrue(event.isCancelled());
    assertEquals(3, dispenserInventory.getItem(3).getAmount());
    assertGrew();
  }

  @Test
  void aDispenserEmptiesTheSlotWhenItUsesTheLastBonemeal() {
    Block dispenser = dispenserBelowTheBlossom();
    dispenserInventory.setItem(0, new ItemStack(Material.BONE_MEAL, 1));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 1), 0);

    listener.onBlockPreDispense(event);

    assertTrue(event.isCancelled());
    assertNull(dispenserInventory.getItem(0));
    assertGrew();
  }

  @Test
  void aDispenserOnlyTouchesTheSlotItIsDispensingFrom() {
    Block dispenser = dispenserBelowTheBlossom();
    dispenserInventory.setItem(1, new ItemStack(Material.BONE_MEAL, 2));
    dispenserInventory.setItem(2, new ItemStack(Material.BONE_MEAL, 5));

    listener.onBlockPreDispense(
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 5), 2));

    assertEquals(2, dispenserInventory.getItem(1).getAmount());
    assertEquals(4, dispenserInventory.getItem(2).getAmount());
  }

  @Test
  void aSidewaysDispenserFindsABlossomNextToIt() {
    Block dispenser = dispenserFacing(BlockFace.WEST, 6, 70, 5);
    dispenserInventory.setItem(0, new ItemStack(Material.BONE_MEAL, 2));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 2), 0);

    listener.onBlockPreDispense(event);

    assertTrue(event.isCancelled());
    assertGrew();
  }

  @Test
  void aDispenserFacingAwayFromTheBlossomIsIgnored() {
    Block dispenser = dispenserFacing(BlockFace.DOWN, 5, 69, 5);
    dispenserInventory.setItem(0, new ItemStack(Material.BONE_MEAL, 2));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 2), 0);

    listener.onBlockPreDispense(event);

    assertFalse(event.isCancelled());
    assertEquals(2, dispenserInventory.getItem(0).getAmount());
    assertDidNotGrow();
  }

  @Test
  void aDispenserHoldingOtherItemsIsIgnored() {
    Block dispenser = dispenserBelowTheBlossom();
    dispenserInventory.setItem(0, new ItemStack(Material.STICK, 2));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.STICK, 2), 0);

    listener.onBlockPreDispense(event);

    assertFalse(event.isCancelled());
    assertEquals(2, dispenserInventory.getItem(0).getAmount());
    assertDidNotGrow();
  }

  @Test
  void aDispenserWhoseSlotNoLongerHoldsBonemealIsIgnored() {
    Block dispenser = dispenserBelowTheBlossom();
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 2), 0);

    listener.onBlockPreDispense(event);

    assertFalse(event.isCancelled());
    assertDidNotGrow();
  }

  @Test
  void aDropperIsIgnored() {
    Block dropper = world.getBlockAt(5, 69, 5);
    Directional data = (Directional) Material.DROPPER.createBlockData();
    data.setFacing(BlockFace.UP);
    dropper.setBlockData(data);
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dropper, new ItemStack(Material.BONE_MEAL, 2), 0);

    listener.onBlockPreDispense(event);

    assertFalse(event.isCancelled());
    assertDidNotGrow();
  }

  @Test
  void registeredWithTheServerItHandlesDispensers() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    Block dispenser = dispenserBelowTheBlossom();
    dispenserInventory.setItem(0, new ItemStack(Material.BONE_MEAL, 2));
    BlockPreDispenseEvent event =
        new BlockPreDispenseEvent(dispenser, new ItemStack(Material.BONE_MEAL, 2), 0);

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertGrew();
  }
}
