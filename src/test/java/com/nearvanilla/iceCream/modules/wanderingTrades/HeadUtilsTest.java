package com.nearvanilla.iceCream.modules.wanderingTrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WanderingTradesTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HeadUtilsTest {
  private static final String TEXTURE = WanderingTradesTestUtils.texture("chest");

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WanderingTradesTestUtils.CONFIG);
    WanderingTradesTestUtils.reset();
  }

  @AfterEach
  void tearDown() {
    WanderingTradesTestUtils.reset();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private SkullMeta meta(ItemStack head) {
    return (SkullMeta) head.getItemMeta();
  }

  private PlayerProfile profile(ItemStack head) {
    PlayerProfile profile = meta(head).getPlayerProfile();
    assertNotNull(profile);
    return profile;
  }

  @Test
  void createsAPlayerHeadOfTheRequestedAmount() {
    ItemStack head = HeadUtils.createCustomHead("Chest", TEXTURE, 8);

    assertEquals(Material.PLAYER_HEAD, head.getType());
    assertEquals(8, head.getAmount());
  }

  @Test
  void createsASingleHead() {
    assertEquals(1, HeadUtils.createCustomHead("Chest", TEXTURE, 1).getAmount());
  }

  @Test
  void createsAFullStackOfHeads() {
    assertEquals(64, HeadUtils.createCustomHead("Chest", TEXTURE, 64).getAmount());
  }

  @Test
  void namesTheHeadWithoutItalics() {
    ItemStack head = HeadUtils.createCustomHead("Mini Chest", TEXTURE, 1);

    Component name = meta(head).customName();

    assertNotNull(name);
    assertEquals("Mini Chest", MessageTestUtils.plainText(name));
    assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
  }

  @Test
  void stripsTheQuotesTheDataFileWrapsAroundNames() {
    ItemStack head = HeadUtils.createCustomHead("\"Mini Chest\"", TEXTURE, 1);

    assertEquals("Mini Chest", MessageTestUtils.plainText(meta(head).customName()));
  }

  @Test
  void stripsQuotesFromTheMiddleOfANameToo() {
    ItemStack head = HeadUtils.createCustomHead("Mini \"Big\" Chest", TEXTURE, 1);

    assertEquals("Mini Big Chest", MessageTestUtils.plainText(meta(head).customName()));
  }

  @Test
  void keepsAnEmptyNameEmpty() {
    ItemStack head = HeadUtils.createCustomHead("", TEXTURE, 1);

    assertEquals("", MessageTestUtils.plainText(meta(head).customName()));
  }

  @Test
  void leavesTheNameAsPlainTextWithoutParsingIt() {
    ItemStack head = HeadUtils.createCustomHead("<red>Chest</red>", TEXTURE, 1);

    assertEquals("<red>Chest</red>", MessageTestUtils.plainText(meta(head).customName()));
  }

  @Test
  void carriesTheTextureAsTheOnlyProfileProperty() {
    ItemStack head = HeadUtils.createCustomHead("Chest", TEXTURE, 1);

    List<ProfileProperty> properties = List.copyOf(profile(head).getProperties());

    assertEquals(1, properties.size());
    assertEquals("textures", properties.getFirst().getName());
    assertEquals(TEXTURE, properties.getFirst().getValue());
  }

  @Test
  void derivesTheProfileIdFromTheTexture() {
    ItemStack head = HeadUtils.createCustomHead("Chest", TEXTURE, 1);

    UUID expected = UUID.nameUUIDFromBytes(TEXTURE.getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, profile(head).getId());
  }

  @Test
  void headsWithTheSameNameAndTextureStack() {
    ItemStack first = HeadUtils.createCustomHead("Chest", TEXTURE, 1);
    ItemStack second = HeadUtils.createCustomHead("Chest", TEXTURE, 1);

    assertEquals(profile(first).getId(), profile(second).getId());
    assertTrue(first.isSimilar(second));
  }

  @Test
  void theProfileIdDoesNotDependOnTheName() {
    ItemStack first = HeadUtils.createCustomHead("Chest", TEXTURE, 1);
    ItemStack second = HeadUtils.createCustomHead("Other name", TEXTURE, 1);

    assertEquals(profile(first).getId(), profile(second).getId());
  }

  @Test
  void headsWithDifferentTexturesDoNotStack() {
    ItemStack chest = HeadUtils.createCustomHead("Chest", TEXTURE, 1);
    ItemStack furnace =
        HeadUtils.createCustomHead("Chest", WanderingTradesTestUtils.texture("furnace"), 1);

    assertNotEquals(profile(chest).getId(), profile(furnace).getId());
    assertFalse(chest.isSimilar(furnace));
  }

  @Test
  void anEmptyTextureStillMakesAProfile() {
    ItemStack head = HeadUtils.createCustomHead("Chest", "", 1);

    assertEquals(UUID.nameUUIDFromBytes(new byte[0]), profile(head).getId());
    assertEquals("", List.copyOf(profile(head).getProperties()).getFirst().getValue());
  }

  @Test
  void theHeadTradePoolIsTheModulesPool() {
    assertSame(WanderingTradesModule.headTradePool, HeadUtils.getHeadTradePool());
  }

  @Test
  void theHeadTradePoolStartsEmptyAndReflectsAddedTrades() {
    assertTrue(HeadUtils.getHeadTradePool().isEmpty());

    MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.DIRT), 1);
    WanderingTradesModule.headTradePool.add(recipe);

    assertEquals(List.of(recipe), HeadUtils.getHeadTradePool());
  }
}
