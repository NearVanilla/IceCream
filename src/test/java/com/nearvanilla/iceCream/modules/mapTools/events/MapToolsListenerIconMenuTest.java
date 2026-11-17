package com.nearvanilla.iceCream.modules.mapTools.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.AreaForms;
import com.nearvanilla.iceCream.modules.mapTools.EditSession;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.IconMenu;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.DisplayScenario;
import com.nearvanilla.iceCream.testutil.DisplayTestUtils.Viewer;
import com.nearvanilla.iceCream.testutil.InventoryTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import java.io.IOException;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The icon menu seen through the listener. The menu is opened the way players open it: from the
 * form of a new marker. MockBukkit inventories cannot tell who holds them, which the listener asks,
 * so the events are fired for a view over a spy that says the menu does.
 */
class MapToolsListenerIconMenuTest {
  private DisplayScenario drawing;
  private Viewer alex;
  private IconMenu menu;
  private Inventory top;
  private InventoryView view;

  @BeforeEach
  void setUp() throws IOException {
    drawing = DisplayScenario.open("");
    alex = drawing.viewer();
    MapToolsTestUtils.writeIcons("alpha", "beta");
    MapToolsUtils.init(MapToolsTestUtils.emptySquaremap());

    MapToolsUtils.startEditing(alex, EditSession.create(Mode.MARKER));
    AreaForms.openCreate(alex);
    ShownDialog form = alex.lastDialog();
    form.click("Pick icon", form.responseWithInitialValues(), alex);
    Inventory opened = alex.getOpenInventory().getTopInventory();
    menu = assertInstanceOf(IconMenu.class, opened.getHolder());
    top = spy(opened);
    doReturn(menu).when(top).getHolder(false);
    // The player has the spy open, so the close that ends the pick also finds the menu's holder.
    alex.openInventory(top);
    view = InventoryTestUtils.containerView(alex, top);
    drawing.server().getPluginManager().registerEvents(new MapToolsListener(), IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    drawing.close();
  }

  private InventoryClickEvent click(int rawSlot) {
    InventoryClickEvent event =
        new InventoryClickEvent(
            view, SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    drawing.server().getPluginManager().callEvent(event);
    return event;
  }

  private void tick() {
    drawing.server().getScheduler().performTicks(1);
  }

  /** What the form that reopened says about the icon. */
  private String iconLine() {
    return alex.lastDialog().body().stream()
        .filter(line -> line.contains("Icon: "))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void theMenuOpensFromTheForm() {
    assertEquals(1, alex.dialogs().size());
    assertEquals(9, menu.getInventory().getSize());
    assertEquals(Material.BARRIER, menu.getInventory().getItem(0).getType());
    assertNotNull(menu.getInventory().getItem(1));
    assertNotNull(menu.getInventory().getItem(2));
    assertNull(menu.getInventory().getItem(3));
  }

  @Test
  void clickingAnIconCancelsTheClickAndReopensTheFormWithIt() {
    InventoryClickEvent event = click(2);

    assertTrue(event.isCancelled());
    assertEquals(1, alex.dialogs().size(), "the form reopens on the next tick");
    tick();
    assertEquals(2, alex.dialogs().size());
    assertEquals("New marker", alex.lastDialog().title());
    assertTrue(iconLine().endsWith("Icon: beta"), iconLine());
  }

  @Test
  void clickingTheNoIconSlotReopensTheFormWithoutAnIcon() {
    click(0);
    tick();

    assertTrue(iconLine().endsWith("Icon: none, shown as a dot"), iconLine());
  }

  @Test
  void clickingInThePlayersOwnInventoryIsCancelledAndPicksNothing() {
    // The first slot of the player's inventory has the number of a slot of the menu that holds an
    // icon, which must not be taken for it.
    InventoryClickEvent event = click(top.getSize() + 1);

    assertEquals(1, event.getSlot());
    assertTrue(event.isCancelled());
    tick();
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void clickingAnEmptySlotOfTheMenuIsCancelledAndPicksNothing() {
    InventoryClickEvent event = click(7);

    assertTrue(event.isCancelled());
    tick();
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void aClickInAnotherInventoryIsLeftAlone() {
    Inventory chest = mock(Inventory.class);
    when(chest.getSize()).thenReturn(27);
    when(chest.getHolder(false)).thenReturn(null);
    view = InventoryTestUtils.containerView(alex, chest);

    InventoryClickEvent event = click(2);

    assertFalse(event.isCancelled());
    tick();
    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void draggingItemsAcrossTheMenuIsCancelled() {
    InventoryDragEvent event =
        new InventoryDragEvent(
            view,
            null,
            new ItemStack(Material.STONE),
            false,
            Map.of(1, new ItemStack(Material.STONE)));

    drawing.server().getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void draggingItemsInAnotherInventoryIsLeftAlone() {
    Inventory chest = mock(Inventory.class);
    when(chest.getSize()).thenReturn(27);
    when(chest.getHolder(false)).thenReturn(null);
    InventoryDragEvent event =
        new InventoryDragEvent(
            InventoryTestUtils.containerView(alex, chest),
            null,
            new ItemStack(Material.STONE),
            false,
            Map.of(1, new ItemStack(Material.STONE)));

    drawing.server().getPluginManager().callEvent(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void closingTheMenuReopensTheFormWithTheIconItHad() {
    drawing.server().getPluginManager().callEvent(new InventoryCloseEvent(view));

    assertEquals(1, alex.dialogs().size());
    tick();
    assertEquals(2, alex.dialogs().size());
    assertTrue(iconLine().endsWith("Icon: alpha"), iconLine());
  }

  @Test
  void closingAnotherInventoryLeavesTheFormClosed() {
    Inventory chest = mock(Inventory.class);
    when(chest.getSize()).thenReturn(27);
    when(chest.getHolder(false)).thenReturn(null);

    drawing
        .server()
        .getPluginManager()
        .callEvent(new InventoryCloseEvent(InventoryTestUtils.containerView(alex, chest)));
    tick();

    assertEquals(1, alex.dialogs().size());
  }

  @Test
  void theCloseThatFollowsAPickDoesNotReopenTheFormTwice() {
    click(2);
    tick();

    drawing.server().getPluginManager().callEvent(new InventoryCloseEvent(view));
    tick();

    assertEquals(2, alex.dialogs().size());
    assertTrue(iconLine().endsWith("Icon: beta"), iconLine());
  }
}
