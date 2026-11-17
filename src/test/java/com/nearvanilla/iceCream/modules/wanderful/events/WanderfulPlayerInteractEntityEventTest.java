package com.nearvanilla.iceCream.modules.wanderful.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.wanderful.WandType;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.WanderfulTestUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Pig;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

// The tests read the persistent keys of WandType, which builds them from IceCream.instance when
// the class loads, so the fixture is installed before any test touches the enum.
class WanderfulPlayerInteractEntityEventTest {
  private ServerMock server;
  private World world;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WanderfulTestUtils.CONFIG);
    server
        .getPluginManager()
        .registerEvents(new WanderfulPlayerInteractEntityEvent(), IceCream.instance);
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Builder");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  // An invisible frame that holds an item, like the ones the wand is meant for.
  private ItemFrame filledFrame() {
    ItemFrame frame = world.spawn(new Location(world, 0, 64, 0), ItemFrame.class);
    frame.setItem(new ItemStack(Material.DIAMOND));
    frame.setVisible(false);
    return frame;
  }

  private PlayerInteractEntityEvent interact(Entity target, EquipmentSlot hand) {
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, hand);
    server.getPluginManager().callEvent(event);
    return event;
  }

  private void hold(ItemStack item) {
    player.getInventory().setItemInMainHand(item);
  }

  @Test
  void itemFrameWandMakesAnInvisibleFrameVisible() {
    ItemFrame frame = filledFrame();
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertTrue(frame.isVisible());
    assertTrue(event.isCancelled());
  }

  @Test
  void itemFrameWandMakesAVisibleFrameInvisible() {
    ItemFrame frame = filledFrame();
    frame.setVisible(true);
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertTrue(event.isCancelled());
  }

  @Test
  void usingTheWandTwiceRestoresTheFrame() {
    ItemFrame frame = filledFrame();
    hold(WandType.ITEM_FRAME.createItem());

    interact(frame, EquipmentSlot.HAND);
    interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
  }

  @Test
  void itemFrameWandAlsoTogglesAGlowItemFrame() {
    GlowItemFrame frame = world.spawn(new Location(world, 0, 64, 0), GlowItemFrame.class);
    frame.setItem(new ItemStack(Material.DIAMOND));
    frame.setVisible(false);
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertTrue(frame.isVisible());
    assertTrue(event.isCancelled());
  }

  @Test
  void anEmptyFrameIsLeftAlone() {
    ItemFrame frame = world.spawn(new Location(world, 0, 64, 0), ItemFrame.class);
    frame.setVisible(false);
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void theOffHandInteractionIsIgnored() {
    ItemFrame frame = filledFrame();
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.OFF_HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void aWandInTheOffHandOnlyDoesNothing() {
    ItemFrame frame = filledFrame();
    player.getInventory().setItemInOffHand(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void anEmptyHandDoesNothing() {
    ItemFrame frame = filledFrame();

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void anOrdinaryItemDoesNothing() {
    ItemFrame frame = filledFrame();
    hold(new ItemStack(Material.WOODEN_SWORD));

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void theArmorStandWandDoesNothing() {
    ItemFrame frame = filledFrame();
    hold(WandType.ARMOR_STAND.createItem());

    PlayerInteractEntityEvent event = interact(frame, EquipmentSlot.HAND);

    assertFalse(frame.isVisible());
    assertFalse(event.isCancelled());
  }

  @Test
  void aWandUsedOnAnotherEntityDoesNothing() {
    Pig pig = world.spawn(new Location(world, 0, 64, 0), Pig.class);
    hold(WandType.ITEM_FRAME.createItem());

    PlayerInteractEntityEvent event = interact(pig, EquipmentSlot.HAND);

    assertFalse(event.isCancelled());
  }

  @Test
  void theWandStaysInTheHandAfterUse() {
    ItemFrame frame = filledFrame();
    hold(WandType.ITEM_FRAME.createItem());

    interact(frame, EquipmentSlot.HAND);

    assertTrue(
        player
            .getInventory()
            .getItemInMainHand()
            .getItemMeta()
            .getPersistentDataContainer()
            .has(WandType.ITEM_FRAME.getMainPersistentKey()));
  }
}
