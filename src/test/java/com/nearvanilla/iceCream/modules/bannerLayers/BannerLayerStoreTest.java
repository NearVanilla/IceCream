package com.nearvanilla.iceCream.modules.bannerLayers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BannerLayerStoreTest {
  private static final NamespacedKey KEY = new NamespacedKey("icecream", "banner_layers");
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

  private BannerLayerStore store;

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
    store =
        new BannerLayerStore(
            "icecream", RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN));
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

  static List<Pattern> patterns(int count) {
    List<Pattern> patterns = new ArrayList<>(count);
    DyeColor[] colors = DyeColor.values();
    for (int i = 0; i < count; i++) {
      patterns.add(new Pattern(colors[i % colors.length], TYPES[i]));
    }
    return patterns;
  }

  static ItemStack banner(int layers) {
    ItemStack banner = new ItemStack(Material.WHITE_BANNER);
    if (layers > 0) {
      banner.setData(
          DataComponentTypes.BANNER_PATTERNS,
          BannerPatternLayers.bannerPatternLayers(patterns(layers)));
    }
    return banner;
  }

  @Test
  void readsLayers() {
    assertEquals(patterns(3), BannerLayerStore.layers(banner(3)));
    assertEquals(List.of(), BannerLayerStore.layers(banner(0)));
    assertEquals(List.of(), BannerLayerStore.layers(null));
  }

  @Test
  void hidesBannersTheLoomRefusesBelowTheLimit() {
    assertTrue(store.shouldHide(banner(6), 16));
    assertTrue(store.shouldHide(banner(15), 16));
    assertTrue(store.shouldHide(banner(6), 7));
  }

  @Test
  void leavesBannersTheLoomAcceptsToVanilla() {
    assertFalse(store.shouldHide(banner(0), 16));
    assertFalse(store.shouldHide(banner(5), 16));
  }

  @Test
  void doesNotHideBannersAtTheLimit() {
    assertFalse(store.shouldHide(banner(16), 16));
    assertFalse(store.shouldHide(banner(8), 8));
    assertFalse(store.shouldHide(banner(6), 6));
  }

  @Test
  void doesNotHideOtherItems() {
    ItemStack shield = new ItemStack(Material.SHIELD);

    assertFalse(store.shouldHide(shield, 16));
    assertFalse(store.shouldHide(null, 16));
  }

  @Test
  void doesNotHideAHiddenCopyAgain() {
    assertFalse(store.shouldHide(store.hide(banner(8)), 16));
  }

  @Test
  void hiddenCopyShowsFiveLayersAndRemembersAll() {
    ItemStack banner = banner(9);
    banner.setAmount(3);

    ItemStack hidden = store.hide(banner);

    assertEquals(patterns(5), BannerLayerStore.layers(hidden));
    assertTrue(store.isHidden(hidden));
    assertEquals(3, hidden.getAmount());
    assertEquals(Material.WHITE_BANNER, hidden.getType());
    List<String> saved =
        hidden.getPersistentDataContainer().get(KEY, PersistentDataType.LIST.strings());
    assertEquals(9, saved.size());
    assertEquals("minecraft:stripe_top white", saved.getFirst());
  }

  @Test
  void hidingLeavesTheOriginalAlone() {
    ItemStack banner = banner(8);

    store.hide(banner);

    assertEquals(patterns(8), BannerLayerStore.layers(banner));
    assertFalse(store.isHidden(banner));
  }

  @Test
  void revealingGivesBackTheOriginal() {
    ItemStack banner = banner(10);
    banner.setAmount(2);

    ItemStack revealed = store.reveal(store.hide(banner));

    assertSameBanner(banner, revealed);
    assertFalse(store.isHidden(revealed));
  }

  @Test
  void revealingKeepsOtherData() {
    NamespacedKey other = new NamespacedKey("other", "data");
    ItemStack banner = banner(7);
    banner.editPersistentDataContainer(pdc -> pdc.set(other, PersistentDataType.STRING, "kept"));

    ItemStack revealed = store.reveal(store.hide(banner));

    assertSameBanner(banner, revealed);
    assertEquals(
        "kept", revealed.getPersistentDataContainer().get(other, PersistentDataType.STRING));
  }

  @Test
  void revealingTheLoomResultAddsTheNewLayerOnTop() {
    ItemStack hidden = store.hide(banner(8));
    Pattern added = new Pattern(DyeColor.BLACK, PatternType.CIRCLE);
    List<Pattern> loomLayers = new ArrayList<>(BannerLayerStore.layers(hidden));
    loomLayers.add(added);
    ItemStack result = hidden.clone();
    result.setAmount(1);
    result.setData(
        DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(loomLayers));

    ItemStack revealed = store.reveal(result);

    List<Pattern> expected = new ArrayList<>(patterns(8));
    expected.add(added);
    assertEquals(expected, BannerLayerStore.layers(revealed));
    assertEquals(1, revealed.getAmount());
    assertFalse(store.isHidden(revealed));
  }

  @Test
  void revealingLeavesOtherItemsUnchanged() {
    ItemStack banner = banner(8);

    assertSame(banner, store.reveal(banner));
    assertFalse(store.isHidden(null));
    assertFalse(store.isHidden(ItemStack.empty()));
  }

  @Test
  void revealingSkipsLayersThatNoLongerExist() {
    ItemStack hidden = store.hide(banner(7));
    hidden.editPersistentDataContainer(
        pdc ->
            pdc.set(
                KEY,
                PersistentDataType.LIST.strings(),
                List.of(
                    "minecraft:stripe_top white",
                    "minecraft:removed red",
                    "minecraft:cross nocolor",
                    "garbage")));

    ItemStack revealed = store.reveal(hidden);

    assertEquals(
        List.of(new Pattern(DyeColor.WHITE, PatternType.STRIPE_TOP)),
        BannerLayerStore.layers(revealed));
  }
}
