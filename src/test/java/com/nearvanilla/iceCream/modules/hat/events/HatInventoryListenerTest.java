package com.nearvanilla.iceCream.modules.hat.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.hat.commands.HatCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.InventoryTestUtils;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HatInventoryListenerTest {
  private static final int HELMET_SLOT = 39;
  private static final int CHESTPLATE_SLOT = 38;

  private ServerMock server;
  private PlayerMock player;
  private final HatInventoryListener listener = new HatInventoryListener();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    player = server.addPlayer("Hatter");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void allowHats() {
    player.addAttachment(IceCream.instance, HatCommand.PERMISSION, true);
  }

  private InventoryClickEvent click(
      InventoryView view, InventoryType.SlotType slotType, int slot, ItemStack cursor) {
    view.setCursor(cursor);
    return new InventoryClickEvent(
        view, slotType, slot, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
  }

  private InventoryClickEvent helmetClick(ItemStack cursor) {
    return click(
        InventoryTestUtils.craftingView(player), InventoryType.SlotType.ARMOR, HELMET_SLOT, cursor);
  }

  // MockBukkit items carry no default data components, so the slot an item can be equipped to is
  // set explicitly.
  private ItemStack equippableFor(Material material, EquipmentSlot slot) {
    ItemStack item = new ItemStack(material);
    item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).build());
    return item;
  }

  private ItemStack helmet() {
    return player.getInventory().getItem(EquipmentSlot.HEAD);
  }

  private void assertIgnored(InventoryClickEvent event) {
    assertFalse(event.isCancelled());
    assertNull(MessageTestUtils.nextMessage(player));
  }

  @Test
  void putsANonEquippableCursorItemOnTheHead() {
    allowHats();
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertEquals(Material.PUMPKIN, helmet().getType());
  }

  // How many items of a stack end up on the head is not asserted, only that none are lost.
  @Test
  void wearingAStackLosesNoItems() {
    allowHats();
    InventoryClickEvent event = helmetClick(new ItemStack(Material.DIRT, 7));

    listener.onInventoryClick(event);

    assertEquals(Material.DIRT, helmet().getType());
    int onTheCursor =
        event.getView().getCursor().getType() == Material.DIRT
            ? event.getView().getCursor().getAmount()
            : 0;
    assertEquals(7, InventoryTestUtils.countOf(player.getInventory(), Material.DIRT) + onTheCursor);
  }

  @Test
  void leavesTheCursorEmptyWhenTheHeadWasEmpty() {
    allowHats();
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertTrue(event.getView().getCursor().isEmpty());
  }

  @Test
  void cancelsTheClickSoVanillaDoesNotHandleItAgain() {
    allowHats();
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void tellsThePlayerTheItemWasSetAsAHat() {
    allowHats();
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertEquals("[Hat] Item set as hat.", MessageTestUtils.nextMessage(player));
    assertNull(MessageTestUtils.nextMessage(player));
  }

  @Test
  void swapsTheCursorItemWithTheCurrentHelmet() {
    allowHats();
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertEquals(Material.PUMPKIN, helmet().getType());
    assertEquals(Material.DIAMOND_HELMET, event.getView().getCursor().getType());
  }

  @Test
  void treatsAnItemMadeForAnotherArmorSlotAsAHat() {
    allowHats();
    InventoryClickEvent event =
        helmetClick(equippableFor(Material.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST));

    listener.onInventoryClick(event);

    assertTrue(event.isCancelled());
    assertEquals(Material.DIAMOND_CHESTPLATE, helmet().getType());
  }

  @Test
  void refusesAPlayerWithoutPermission() {
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertEquals(
        "[Hat] You do not have permission to use hats.", MessageTestUtils.nextMessage(player));
    assertNull(MessageTestUtils.nextMessage(player));
  }

  @Test
  void stillCancelsTheClickWhenRefusing() {
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void leavesTheHelmetAndCursorAloneWhenRefusing() {
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertEquals(Material.DIAMOND_HELMET, helmet().getType());
    assertEquals(Material.PUMPKIN, event.getView().getCursor().getType());
  }

  @Test
  void leavesAnItemMadeForTheHeadForVanillaToEquip() {
    allowHats();
    InventoryClickEvent event =
        helmetClick(equippableFor(Material.DIAMOND_HELMET, EquipmentSlot.HEAD));

    listener.onInventoryClick(event);

    assertIgnored(event);
    assertTrue(helmet().isEmpty());
    assertEquals(Material.DIAMOND_HELMET, event.getView().getCursor().getType());
  }

  @Test
  void ignoresAnEmptyCursor() {
    allowHats();
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));
    InventoryClickEvent event = helmetClick(ItemStack.empty());

    listener.onInventoryClick(event);

    assertIgnored(event);
    assertEquals(Material.DIAMOND_HELMET, helmet().getType());
  }

  @Test
  void ignoresOtherArmorSlots() {
    allowHats();
    InventoryClickEvent event =
        click(
            InventoryTestUtils.craftingView(player),
            InventoryType.SlotType.ARMOR,
            CHESTPLATE_SLOT,
            new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertIgnored(event);
    assertTrue(helmet().isEmpty());
  }

  @Test
  void ignoresTheHelmetSlotNumberOutsideTheArmorSlots() {
    allowHats();
    InventoryClickEvent event =
        click(
            InventoryTestUtils.craftingView(player),
            InventoryType.SlotType.QUICKBAR,
            HELMET_SLOT,
            new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertIgnored(event);
    assertTrue(helmet().isEmpty());
  }

  @Test
  void ignoresClicksInAnotherKindOfInventory() {
    allowHats();
    InventoryClickEvent event =
        click(
            InventoryTestUtils.craftingView(player, InventoryType.CHEST),
            InventoryType.SlotType.ARMOR,
            HELMET_SLOT,
            new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertIgnored(event);
    assertTrue(helmet().isEmpty());
  }

  @Test
  void ignoresAClickerThatIsNotAPlayer() {
    HumanEntity npc = mock(HumanEntity.class);
    InventoryView view = InventoryTestUtils.craftingView(npc);
    InventoryClickEvent event =
        click(view, InventoryType.SlotType.ARMOR, HELMET_SLOT, new ItemStack(Material.PUMPKIN));

    listener.onInventoryClick(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void firesForARealClickEventOnTheServer() {
    allowHats();
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertEquals(Material.PUMPKIN, helmet().getType());
    assertEquals("[Hat] Item set as hat.", MessageTestUtils.nextMessage(player));
  }

  @Test
  void ignoresAClickAnotherPluginAlreadyCancelled() {
    allowHats();
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    InventoryClickEvent event = helmetClick(new ItemStack(Material.PUMPKIN));
    event.setCancelled(true);

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertTrue(helmet().isEmpty());
    assertEquals(Material.PUMPKIN, event.getView().getCursor().getType());
    assertNull(MessageTestUtils.nextMessage(player));
  }
}
