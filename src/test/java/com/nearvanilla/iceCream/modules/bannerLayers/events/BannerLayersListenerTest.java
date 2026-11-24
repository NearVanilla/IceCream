package com.nearvanilla.iceCream.modules.bannerLayers.events;

import static com.nearvanilla.iceCream.modules.bannerLayers.events.BannerLayersListener.BANNER_SLOT;
import static com.nearvanilla.iceCream.modules.bannerLayers.events.BannerLayersListener.RESULT_SLOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import com.nearvanilla.iceCream.modules.bannerLayers.BannerLayerStore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import io.papermc.paper.event.player.PlayerLoomPatternSelectEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LoomInventory;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BannerLayersListenerTest {
  private static final PatternType[] TYPES = {
    PatternType.STRIPE_TOP,
    PatternType.STRIPE_BOTTOM,
    PatternType.CROSS,
    PatternType.BORDER,
    PatternType.CREEPER,
    PatternType.FLOWER,
    PatternType.GRADIENT,
    PatternType.BRICKS,
    PatternType.SKULL,
    PatternType.MOJANG,
    PatternType.GLOBE,
    PatternType.PIGLIN,
    PatternType.FLOW,
    PatternType.GUSTER,
    PatternType.RHOMBUS,
    PatternType.CIRCLE,
  };
  private static final Pattern ADDED = new Pattern(DyeColor.BLACK, PatternType.CIRCLE);

  private final List<Runnable> nextTick = new ArrayList<>();
  private final ItemStack[] loomSlots = new ItemStack[4];
  private final ItemStack[] playerSlots = new ItemStack[41];
  private BannerLayerStore store;
  private LoomInventory loom;
  private InventoryView view;
  private Player player;
  private PlayerInventory playerInventory;
  private ItemStack cursor;
  private BannerLayersListener listener;

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
    store =
        new BannerLayerStore(
            "icecream", RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN));
    // Backed by plain arrays: MockBukkit's inventories drop the banner layers of stored items.
    playerInventory = mock(PlayerInventory.class);
    when(playerInventory.getSize()).thenReturn(playerSlots.length);
    when(playerInventory.getItem(anyInt()))
        .thenAnswer(call -> playerSlots[call.<Integer>getArgument(0)]);
    doAnswer(call -> playerSlots[call.<Integer>getArgument(0)] = call.getArgument(1))
        .when(playerInventory)
        .setItem(anyInt(), any());

    loom = mock(LoomInventory.class);
    when(loom.getSize()).thenReturn(loomSlots.length);
    when(loom.getItem(anyInt())).thenAnswer(call -> loomSlots[call.<Integer>getArgument(0)]);
    doAnswer(call -> loomSlots[call.<Integer>getArgument(0)] = call.getArgument(1))
        .when(loom)
        .setItem(anyInt(), any());

    player = mock(Player.class);
    view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(loom);
    when(view.getBottomInventory()).thenReturn(playerInventory);
    when(view.getPlayer()).thenReturn(player);
    when(view.getItem(anyInt()))
        .thenAnswer(
            call -> {
              int raw = call.getArgument(0);
              return raw < loomSlots.length
                  ? loomSlots[raw]
                  : playerInventory.getItem(raw - loomSlots.length);
            });
    when(player.getOpenInventory()).thenReturn(view);
    when(player.getInventory()).thenReturn(playerInventory);
    when(player.getItemOnCursor()).thenAnswer(call -> cursor);
    doAnswer(call -> cursor = call.getArgument(0)).when(player).setItemOnCursor(any());

    listener = new BannerLayersListener(store, 16, nextTick::add);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  /** MockBukkit leaves data components out of item equality, so the layers are compared too. */
  private void assertSameBanner(ItemStack expected, ItemStack actual) {
    assertEquals(expected, actual);
    assertEquals(BannerLayerStore.layers(expected), BannerLayerStore.layers(actual));
    assertFalse(store.isHidden(actual));
  }

  private static List<Pattern> patterns(int count) {
    List<Pattern> patterns = new ArrayList<>(count);
    DyeColor[] colors = DyeColor.values();
    for (int i = 0; i < count; i++) {
      patterns.add(new Pattern(colors[i % colors.length], TYPES[i]));
    }
    return patterns;
  }

  private static ItemStack banner(List<Pattern> layers) {
    ItemStack banner = new ItemStack(Material.WHITE_BANNER);
    banner.setData(
        DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(layers));
    return banner;
  }

  private static ItemStack banner(int layers) {
    return banner(patterns(layers));
  }

  /** What the loom makes from the banner in it: a copy of one with the new layer on top. */
  private static ItemStack loomResult(ItemStack input) {
    List<Pattern> layers = new ArrayList<>(BannerLayerStore.layers(input));
    layers.add(ADDED);
    ItemStack result = input.clone();
    result.setAmount(1);
    result.setData(
        DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(layers));
    return result;
  }

  private static List<Pattern> withAdded(int layers) {
    List<Pattern> expected = new ArrayList<>(patterns(layers));
    expected.add(ADDED);
    return expected;
  }

  private void runNextTick() {
    List<Runnable> tasks = new ArrayList<>(nextTick);
    nextTick.clear();
    tasks.forEach(Runnable::run);
  }

  private InventoryClickEvent click(int rawSlot, ClickType type) {
    InventoryClickEvent event =
        new InventoryClickEvent(
            view, SlotType.CONTAINER, rawSlot, type, InventoryAction.PICKUP_ALL);
    listener.onClick(event);
    return event;
  }

  @Test
  void hidesBannerPlacedInTheLoom() {
    loomSlots[BANNER_SLOT] = banner(8);

    click(BANNER_SLOT, ClickType.LEFT);
    runNextTick();

    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
    assertEquals(patterns(5), BannerLayerStore.layers(loomSlots[BANNER_SLOT]));
  }

  @Test
  void hidesBannerShiftClickedIntoTheLoom() {
    playerInventory.setItem(0, new ItemStack(Material.STONE));
    click(loomSlots.length, ClickType.SHIFT_LEFT);
    loomSlots[BANNER_SLOT] = banner(6);
    runNextTick();

    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void hidesBannerDraggedIntoTheLoom() {
    loomSlots[BANNER_SLOT] = banner(7);

    listener.onDrag(
        new InventoryDragEvent(
            view, null, banner(7), false, Map.of(BANNER_SLOT, loomSlots[BANNER_SLOT])));
    runNextTick();

    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void leavesBannersTheLoomAcceptsAlone() {
    ItemStack banner = banner(5);
    loomSlots[BANNER_SLOT] = banner;

    click(BANNER_SLOT, ClickType.LEFT);
    runNextTick();

    assertSameBanner(banner, loomSlots[BANNER_SLOT]);
  }

  @Test
  void leavesBannersAtTheLimitAlone() {
    listener = new BannerLayersListener(store, 8, nextTick::add);
    ItemStack banner = banner(8);
    loomSlots[BANNER_SLOT] = banner;

    click(2, ClickType.LEFT);
    runNextTick();

    assertSameBanner(banner, loomSlots[BANNER_SLOT]);
  }

  @Test
  void doesNothingOnceTheLoomIsClosed() {
    loomSlots[BANNER_SLOT] = banner(8);
    click(2, ClickType.LEFT);
    InventoryView other = mock(InventoryView.class);
    when(other.getTopInventory()).thenReturn(mock(Inventory.class));
    when(player.getOpenInventory()).thenReturn(other);

    runNextTick();

    assertFalse(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void ignoresOtherInventories() {
    when(view.getTopInventory()).thenReturn(mock(Inventory.class));

    listener.onClick(
        new InventoryClickEvent(
            view, SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));

    assertTrue(nextTick.isEmpty());
  }

  @Test
  void revealsResultWhenTheLoomUpdates() {
    ItemStack hidden = store.hide(banner(8));
    PrepareResultEvent event = new PrepareResultEvent(view, loomResult(hidden));

    listener.onPrepareResult(event);

    assertEquals(withAdded(8), BannerLayerStore.layers(event.getResult()));
    assertFalse(store.isHidden(event.getResult()));
  }

  @Test
  void leavesOrdinaryResultsAlone() {
    ItemStack result = loomResult(banner(3));
    PrepareResultEvent event = new PrepareResultEvent(view, result);

    listener.onPrepareResult(event);

    assertEquals(result, event.getResult());
  }

  @Test
  void leavesEmptyResultsAlone() {
    PrepareResultEvent event = new PrepareResultEvent(view, null);

    listener.onPrepareResult(event);

    assertNull(event.getResult());
  }

  @Test
  void revealsResultOnceAPatternIsPicked() {
    ItemStack hidden = store.hide(banner(8));
    loomSlots[BANNER_SLOT] = hidden;
    loomSlots[RESULT_SLOT] = loomResult(hidden);

    listener.onSelectPattern(new PlayerLoomPatternSelectEvent(player, loom, PatternType.CIRCLE));
    runNextTick();

    assertEquals(withAdded(8), BannerLayerStore.layers(loomSlots[RESULT_SLOT]));
    assertFalse(store.isHidden(loomSlots[RESULT_SLOT]));
    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void revealsResultBeforeItIsTaken() {
    ItemStack hidden = store.hide(banner(8));
    loomSlots[BANNER_SLOT] = hidden;
    loomSlots[RESULT_SLOT] = loomResult(hidden);

    click(RESULT_SLOT, ClickType.SHIFT_LEFT);

    assertEquals(withAdded(8), BannerLayerStore.layers(loomSlots[RESULT_SLOT]));
    // The banner stays hidden, or the loom would clear the result before it is taken.
    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void revealsBannerBeforeItIsTakenOut() {
    ItemStack banner = banner(9);
    banner.setAmount(4);
    loomSlots[BANNER_SLOT] = store.hide(banner);

    for (ClickType type :
        List.of(ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DROP)) {
      loomSlots[BANNER_SLOT] = store.hide(banner);
      click(BANNER_SLOT, type);
      assertSameBanner(banner, loomSlots[BANNER_SLOT]);
    }
  }

  @Test
  void revealsBannerSoAnIdenticalOneCanJoinIt() {
    ItemStack banner = banner(8);
    loomSlots[BANNER_SLOT] = store.hide(banner);
    playerInventory.setItem(0, banner.clone());

    click(loomSlots.length, ClickType.SHIFT_LEFT);

    assertSameBanner(banner, loomSlots[BANNER_SLOT]);
  }

  @Test
  void keepsBannerHiddenWhenADifferentOneIsShiftClicked() {
    loomSlots[BANNER_SLOT] = store.hide(banner(8));
    ItemStack other = banner(8);
    other = other.withType(Material.RED_BANNER);
    playerInventory.setItem(0, other);

    click(loomSlots.length, ClickType.SHIFT_LEFT);

    assertTrue(store.isHidden(loomSlots[BANNER_SLOT]));
  }

  @Test
  void revealsBannerWhenTheLoomCloses() {
    ItemStack banner = banner(8);
    loomSlots[BANNER_SLOT] = store.hide(banner);

    listener.onClose(new InventoryCloseEvent(view, InventoryCloseEvent.Reason.PLAYER));

    assertSameBanner(banner, loomSlots[BANNER_SLOT]);
  }

  @Test
  void revealsHiddenCopiesLeftInTheInventoryAfterClosing() {
    ItemStack banner = banner(8);
    playerInventory.setItem(5, store.hide(banner));
    cursor = store.hide(banner);

    listener.onClose(new InventoryCloseEvent(view, InventoryCloseEvent.Reason.PLAYER));
    runNextTick();

    assertSameBanner(banner, playerInventory.getItem(5));
    assertSameBanner(banner, cursor);
  }

  @Test
  void revealsOpenLoomWhenShuttingDown() {
    ItemStack banner = banner(8);
    ItemStack hidden = store.hide(banner);
    loomSlots[BANNER_SLOT] = hidden;
    loomSlots[RESULT_SLOT] = loomResult(hidden);

    listener.revealOpenLoom(player);

    assertSameBanner(banner, loomSlots[BANNER_SLOT]);
    assertEquals(withAdded(8), BannerLayerStore.layers(loomSlots[RESULT_SLOT]));
  }

  @Test
  void weavesUpToTheLimit() {
    ItemStack banner = banner(6);
    for (int layers = 6; layers < 16; layers++) {
      loomSlots[BANNER_SLOT] = banner;
      click(BANNER_SLOT, ClickType.LEFT);
      runNextTick();
      PrepareResultEvent event = new PrepareResultEvent(view, loomResult(loomSlots[BANNER_SLOT]));
      listener.onPrepareResult(event);
      banner = event.getResult();
      assertEquals(layers + 1, BannerLayerStore.layers(banner).size());
    }

    loomSlots[BANNER_SLOT] = banner;
    click(BANNER_SLOT, ClickType.LEFT);
    runNextTick();

    assertFalse(store.isHidden(loomSlots[BANNER_SLOT]));
  }
}
