package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;

/** The chest menu that picks a marker icon. */
class IconMenuTest {
  private ServerMock server;
  private Squaremap squaremap;
  private PlayerMock player;
  private List<String> picked;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    squaremap = mock(Squaremap.class);
    when(squaremap.iconRegistry()).thenReturn(mock(Registry.class));
    player = server.addPlayer("Alex");
    picked = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    MapIcons.unload(squaremap);
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  /** Loads icons with these names, in file name order. */
  private void icons(String... names) throws IOException {
    File folder = new File(IceCreamFixture.dataFolder(), "map-icons");
    assertTrue(folder.isDirectory() || folder.mkdirs());
    for (String name : names) {
      ImageIO.write(
          new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
          "png",
          new File(folder, name + ".png"));
    }
    MapIcons.load(squaremap, folder);
  }

  private IconMenu open(String current) {
    IconMenu.open(player, current, picked::add);
    return assertInstanceOf(
        IconMenu.class, player.getOpenInventory().getTopInventory().getHolder());
  }

  private static String name(ItemStack item) {
    return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
  }

  private void tick() {
    server.getScheduler().performTicks(1);
  }

  // The inventory

  @Test
  void theMenuOpensForThePlayerWithTheTitle() throws IOException {
    icons("pin");

    IconMenu menu = open(null);

    assertSame(menu.getInventory(), player.getOpenInventory().getTopInventory());
    assertEquals(
        "Pick an icon",
        PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()));
  }

  @Test
  void theMenuIsOneRowWhenThereAreUpToEightIcons() throws IOException {
    icons("a", "b", "c", "d", "e", "f", "g", "h");

    assertEquals(9, open(null).getInventory().getSize());
  }

  @Test
  void theMenuGrowsByARowWhenTheNoIconSlotAndTheIconsNeedMore() throws IOException {
    icons("a", "b", "c", "d", "e", "f", "g", "h", "i");

    assertEquals(18, open(null).getInventory().getSize());
  }

  @Test
  void aMenuWithoutIconsStillHasTheNoIconSlot() {
    Inventory inventory = open(null).getInventory();

    assertEquals(9, inventory.getSize());
    assertEquals(Material.BARRIER, inventory.getItem(0).getType());
    assertNull(inventory.getItem(1));
  }

  @Test
  void theMenuNeverHasMoreThanSixRowsAndLeavesOutTheIconsThatDoNotFit() throws IOException {
    String[] names = new String[60];
    for (int i = 0; i < names.length; i++) names[i] = "icon%02d".formatted(i);
    icons(names);

    IconMenu menu = open(null);

    assertEquals(54, menu.getInventory().getSize());
    // Slot 0 is the no-icon slot, so slot 53 holds the 53rd icon, icon52.
    assertEquals("icon52", name(menu.getInventory().getItem(53)));
    // The slots after the last one shown hold icons that did not fit, which cannot be picked.
    menu.click(player, 54);
    tick();
    assertTrue(picked.isEmpty());
    menu.click(player, 53);
    tick();
    assertEquals(List.of("icon52"), picked);
  }

  @Test
  void theFirstSlotIsABarrierForNoIcon() throws IOException {
    icons("pin");

    ItemStack barrier = open("pin").getInventory().getItem(0);

    assertEquals(Material.BARRIER, barrier.getType());
    assertEquals("No icon (dot)", name(barrier));
  }

  @Test
  void everyIconGetsASlotInFileNameOrderNamedAfterIt() throws IOException {
    icons("zeta", "alpha", "mid");

    Inventory inventory = open(null).getInventory();

    assertEquals("alpha", name(inventory.getItem(1)));
    assertEquals("mid", name(inventory.getItem(2)));
    assertEquals("zeta", name(inventory.getItem(3)));
    assertNull(inventory.getItem(4));
  }

  @Test
  void theNamesAreWhiteAndNotItalic() throws IOException {
    icons("pin");

    Component name = open(null).getInventory().getItem(1).getItemMeta().displayName();

    assertEquals(NamedTextColor.WHITE, name.color());
    assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
  }

  // The items

  @Test
  void anIconNamedAfterAnItemShowsThatItem() throws IOException {
    icons("chest");

    assertEquals(Material.CHEST, open(null).getInventory().getItem(1).getType());
  }

  @Test
  void aConfiguredItemWinsOverTheIconName() throws IOException {
    IceCreamFixture.install(
        server, "modules:\n  maptools:\n    marker-icon-items:\n      chest: diamond\n");
    icons("chest");

    assertEquals(Material.DIAMOND, open(null).getInventory().getItem(1).getType());
  }

  @Test
  void anIconWithNoMatchingItemShowsPaper() throws IOException {
    icons("not_an_item");

    assertEquals(Material.PAPER, open(null).getInventory().getItem(1).getType());
  }

  @Test
  void aConfiguredNameThatIsNotAnItemShowsPaper() throws IOException {
    IceCreamFixture.install(
        server, "modules:\n  maptools:\n    marker-icon-items:\n      chest: not_an_item\n");
    icons("chest");

    assertEquals(Material.PAPER, open(null).getInventory().getItem(1).getType());
  }

  // The selected icon

  @Test
  void theCurrentIconShinesAndSaysSelected() throws IOException {
    icons("flag", "pin");

    Inventory inventory = open("pin").getInventory();

    ItemStack pin = inventory.getItem(2);
    assertTrue(pin.getItemMeta().getEnchantmentGlintOverride());
    assertEquals(
        List.of("Selected"),
        pin.getItemMeta().lore().stream()
            .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
            .toList());
    assertEquals(NamedTextColor.GRAY, pin.getItemMeta().lore().getFirst().color());
  }

  @Test
  void theOtherIconsAreNotMarkedSelected() throws IOException {
    icons("flag", "pin");

    Inventory inventory = open("pin").getInventory();

    for (int slot : new int[] {0, 1}) {
      assertNotSelected(inventory.getItem(slot));
    }
  }

  @Test
  void withNoCurrentIconTheNoIconSlotIsTheSelectedOne() throws IOException {
    icons("flag");

    Inventory inventory = open(null).getInventory();

    assertTrue(inventory.getItem(0).getItemMeta().getEnchantmentGlintOverride());
    assertNotNull(inventory.getItem(0).getItemMeta().lore());
    assertNotSelected(inventory.getItem(1));
  }

  @Test
  void aCurrentIconThatNoLongerExistsSelectsNothing() throws IOException {
    icons("flag");

    Inventory inventory = open("gone").getInventory();

    assertNotSelected(inventory.getItem(0));
    assertNotSelected(inventory.getItem(1));
  }

  private static void assertNotSelected(ItemStack item) {
    assertFalse(item.getItemMeta().hasEnchantmentGlintOverride());
    assertNull(item.getItemMeta().lore());
  }

  // Picking

  @Test
  void clickingAnIconPicksItOnTheNextTick() throws IOException {
    icons("flag", "pin");
    IconMenu menu = open(null);

    menu.click(player, 2);

    assertTrue(picked.isEmpty(), "the form must not reopen inside the inventory event");
    tick();
    assertEquals(List.of("pin"), picked);
  }

  @Test
  void clickingTheNoIconSlotPicksNull() throws IOException {
    icons("flag");
    IconMenu menu = open("flag");

    menu.click(player, 0);
    tick();

    assertEquals(1, picked.size());
    assertNull(picked.getFirst());
  }

  @Test
  void theMenuIsClosedBeforeTheFormReopens() throws IOException {
    icons("flag");
    IconMenu[] menu = new IconMenu[1];
    List<Boolean> menuOpenWhenPicked = new ArrayList<>();
    IconMenu.open(
        player,
        null,
        icon ->
            menuOpenWhenPicked.add(
                player.getOpenInventory().getTopInventory() == menu[0].getInventory()));
    menu[0] =
        assertInstanceOf(IconMenu.class, player.getOpenInventory().getTopInventory().getHolder());

    menu[0].click(player, 1);
    tick();

    assertEquals(List.of(false), menuOpenWhenPicked);
  }

  @Test
  void clickingOutsideTheIconSlotsPicksNothing() throws IOException {
    icons("flag");
    IconMenu menu = open(null);

    menu.click(player, -1);
    menu.click(player, 2);
    menu.click(player, 8);
    menu.click(player, 9);
    tick();

    assertTrue(picked.isEmpty());
  }

  @Test
  void aStrayClickDoesNotUseUpTheMenu() throws IOException {
    icons("flag");
    IconMenu menu = open(null);
    menu.click(player, 5);

    menu.click(player, 1);
    tick();

    assertEquals(List.of("flag"), picked);
  }

  @Test
  void onlyTheFirstPickCounts() throws IOException {
    icons("flag", "pin");
    IconMenu menu = open(null);

    menu.click(player, 1);
    menu.click(player, 2);
    tick();

    assertEquals(List.of("flag"), picked);
  }

  // Closing

  @Test
  void closingTheMenuKeepsTheCurrentIcon() throws IOException {
    icons("flag", "pin");
    IconMenu menu = open("pin");

    menu.closed(player);
    tick();

    assertEquals(List.of("pin"), picked);
  }

  @Test
  void closingTheMenuWithNoCurrentIconPicksNull() throws IOException {
    icons("flag");
    IconMenu menu = open(null);

    menu.closed(player);
    tick();

    assertEquals(1, picked.size());
    assertNull(picked.getFirst());
  }

  @Test
  void closingAfterPickingDoesNotPickAgain() throws IOException {
    icons("flag", "pin");
    IconMenu menu = open("pin");
    menu.click(player, 1);

    menu.closed(player);
    tick();

    assertEquals(List.of("flag"), picked);
  }

  @Test
  void clickingAfterClosingDoesNotPickAgain() throws IOException {
    icons("flag", "pin");
    IconMenu menu = open("pin");
    menu.closed(player);

    menu.click(player, 1);
    tick();

    assertEquals(List.of("pin"), picked);
  }

  @Test
  void nothingHappensUntilTheNextTickWhenTheMenuIsClosed() throws IOException {
    icons("flag");
    IconMenu menu = open(null);

    menu.closed(player);

    assertTrue(picked.isEmpty());
    assertSame(menu.getInventory(), player.getOpenInventory().getTopInventory());
  }

  @Test
  void pickingClosesTheInventoryOnTheNextTick() throws IOException {
    icons("flag");
    IconMenu menu = open(null);

    menu.click(player, 1);
    tick();

    assertNotSame(menu.getInventory(), player.getOpenInventory().getTopInventory());
  }

  @Test
  void eachMenuKeepsItsOwnPick() throws IOException {
    icons("flag", "pin");
    List<String> other = new ArrayList<>();
    IconMenu first = open(null);
    PlayerMock second = server.addPlayer("Sam");
    IconMenu.open(second, null, other::add);
    IconMenu secondMenu =
        assertInstanceOf(IconMenu.class, second.getOpenInventory().getTopInventory().getHolder());

    first.click(player, 1);
    secondMenu.click(second, 2);
    tick();

    assertEquals(List.of("flag"), picked);
    assertEquals(List.of("pin"), other);
  }
}
