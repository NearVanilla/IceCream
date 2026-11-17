package com.nearvanilla.iceCream.modules.readOnlyLectern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import com.nearvanilla.iceCream.testutil.LecternTestUtils.LookingPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class LecternUtilsTest {
  private ServerMock server;
  private World world;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    ReadOnlyLecternModule.READ_ONLY_KEY = new NamespacedKey(IceCream.instance, "lectern.readonly");
    world = server.addSimpleWorld("world");
  }

  @AfterEach
  void tearDown() {
    ReadOnlyLecternModule.READ_ONLY_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Block lectern() {
    return LecternTestUtils.lectern(world, 0, 64, 0);
  }

  private Block lecternWithBook(int x) {
    return LecternTestUtils.lecternWithBook(world, x, 64, 0);
  }

  private Block plainBlock(Material material) {
    Block block = world.getBlockAt(5, 64, 5);
    block.setType(material);
    return block;
  }

  private void setFlag(Block block, boolean value) {
    ((TileState) block.getState())
        .getPersistentDataContainer()
        .set(ReadOnlyLecternModule.READ_ONLY_KEY, PersistentDataType.BOOLEAN, value);
  }

  private Boolean storedFlag(Block block) {
    return ((TileState) block.getState())
        .getPersistentDataContainer()
        .get(ReadOnlyLecternModule.READ_ONLY_KEY, PersistentDataType.BOOLEAN);
  }

  private String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  // isReadOnly

  @Test
  void aLecternIsNotReadOnlyByDefault() {
    assertFalse(LecternUtils.isReadOnly(lectern()));
  }

  @Test
  void aLecternWithTheFlagSetIsReadOnly() {
    Block lectern = lectern();
    setFlag(lectern, true);

    assertTrue(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void aLecternWithTheFlagClearedIsNotReadOnly() {
    Block lectern = lectern();
    setFlag(lectern, false);

    assertFalse(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void aBlockThatIsNotALecternIsNeverReadOnly() {
    Block chest = plainBlock(Material.CHEST);
    setFlag(chest, true);

    assertFalse(LecternUtils.isReadOnly(chest));
  }

  @Test
  void airIsNotReadOnly() {
    assertFalse(LecternUtils.isReadOnly(plainBlock(Material.AIR)));
  }

  // setReadOnly

  @Test
  void settingReadOnlyMakesTheLecternReadOnly() {
    Block lectern = lectern();

    LecternUtils.setReadOnly(lectern, true);

    assertTrue(LecternUtils.isReadOnly(lectern));
    assertEquals(Boolean.TRUE, storedFlag(lectern));
  }

  @Test
  void settingReadOnlyFalseClearsTheFlag() {
    Block lectern = lectern();
    LecternUtils.setReadOnly(lectern, true);

    LecternUtils.setReadOnly(lectern, false);

    assertFalse(LecternUtils.isReadOnly(lectern));
    assertEquals(Boolean.FALSE, storedFlag(lectern));
  }

  @Test
  void settingReadOnlyWritesTheFlagBackToTheBlock() {
    // A real block hands out a snapshot of its state, so the flag only reaches the block when the
    // state is updated. MockBukkit hands out the live state, so this view restores that behaviour.
    Block real = lectern();
    Block view = mock(Block.class);
    when(view.getType()).thenAnswer(invocation -> real.getType());
    when(view.getState()).thenAnswer(invocation -> real.getState(false));

    LecternUtils.setReadOnly(view, true);

    assertTrue(LecternUtils.isReadOnly(real));
  }

  @Test
  void settingReadOnlyKeepsTheBookOnTheLectern() {
    Block lectern = lecternWithBook(0);

    LecternUtils.setReadOnly(lectern, true);

    assertTrue(LecternUtils.hasPlayerAuthoredBook(lectern));
  }

  @Test
  void settingReadOnlyOnlyAffectsThatLectern() {
    Block first = lecternWithBook(0);
    Block second = lecternWithBook(2);

    LecternUtils.setReadOnly(first, true);

    assertFalse(LecternUtils.isReadOnly(second));
  }

  // hasPlayerAuthoredBook

  @Test
  void aLecternWithASignedBookHasAPlayerAuthoredBook() {
    assertTrue(LecternUtils.hasPlayerAuthoredBook(lecternWithBook(0)));
  }

  @Test
  void anEmptyLecternHasNoPlayerAuthoredBook() {
    assertFalse(LecternUtils.hasPlayerAuthoredBook(lectern()));
  }

  @Test
  void aLecternHoldingAWritableBookHasNoPlayerAuthoredBook() {
    Block lectern = lectern();
    // Even a writable book that carries an author is not a signed book.
    ItemStack writable = new ItemStack(Material.WRITABLE_BOOK);
    BookMeta meta = (BookMeta) writable.getItemMeta();
    meta.setAuthor("Steve");
    writable.setItemMeta(meta);
    LecternTestUtils.placeBook(lectern, writable);

    assertFalse(LecternUtils.hasPlayerAuthoredBook(lectern));
  }

  @Test
  void aLecternHoldingSomethingElseHasNoPlayerAuthoredBook() {
    Block lectern = lectern();
    LecternTestUtils.placeBook(lectern, new ItemStack(Material.PAPER));

    assertFalse(LecternUtils.hasPlayerAuthoredBook(lectern));
  }

  @Test
  void aLecternHoldingAWrittenBookWithoutAnAuthorHasNoPlayerAuthoredBook() {
    Block lectern = lectern();
    LecternTestUtils.placeBook(lectern, LecternTestUtils.writtenBook(null));

    assertFalse(LecternUtils.hasPlayerAuthoredBook(lectern));
  }

  @Test
  void aBlockThatIsNotALecternHasNoPlayerAuthoredBook() {
    assertFalse(LecternUtils.hasPlayerAuthoredBook(plainBlock(Material.CHEST)));
  }

  // isEligibleForToggle

  @Test
  void aLecternWithASignedBookCanBeToggled() {
    assertTrue(LecternUtils.isEligibleForToggle(lecternWithBook(0)));
  }

  @Test
  void anEmptyLecternCannotBeToggled() {
    assertFalse(LecternUtils.isEligibleForToggle(lectern()));
  }

  @Test
  void aLecternWithAnUnsignedBookCannotBeToggled() {
    Block lectern = lectern();
    LecternTestUtils.placeBook(lectern, new ItemStack(Material.WRITABLE_BOOK));

    assertFalse(LecternUtils.isEligibleForToggle(lectern));
  }

  @Test
  void aBlockThatIsNotALecternCannotBeToggled() {
    assertFalse(LecternUtils.isEligibleForToggle(plainBlock(Material.STONE)));
  }

  // getTargetLectern

  @Test
  void theTargetLecternIsTheLecternThePlayerLooksAt() {
    Block lectern = lectern();
    LookingPlayer player = LecternTestUtils.addLookingPlayer(server, "Reader", lectern);

    assertSame(lectern, LecternUtils.getTargetLectern(player, 5));
  }

  @Test
  void thereIsNoTargetLecternWhenThePlayerLooksAtAnotherBlock() {
    LookingPlayer player =
        LecternTestUtils.addLookingPlayer(server, "Reader", plainBlock(Material.STONE));

    assertNull(LecternUtils.getTargetLectern(player, 5));
  }

  @Test
  void thereIsNoTargetLecternWhenThePlayerLooksAtNothing() {
    LookingPlayer player = LecternTestUtils.addLookingPlayer(server, "Reader", null);

    assertNull(LecternUtils.getTargetLectern(player, 5));
  }

  @Test
  void theTargetLecternSearchUsesTheGivenDistance() {
    LookingPlayer player = LecternTestUtils.addLookingPlayer(server, "Reader", lectern());

    LecternUtils.getTargetLectern(player, 7);

    assertEquals(7, player.requestedDistance());
  }

  // buildMessage

  @Test
  void buildMessageReadsMiniMessageTags() {
    Component message = LecternUtils.buildMessage("<red>This lectern is read-only.</red>");

    assertEquals("This lectern is read-only.", plain(message));
    assertEquals(NamedTextColor.RED, message.color());
  }

  @Test
  void buildMessageKeepsPlainText() {
    Component message = LecternUtils.buildMessage("Hello there");

    assertEquals("Hello there", plain(message));
    assertNull(message.color());
  }

  @Test
  void buildMessageOfAnEmptyStringIsEmpty() {
    Component message = LecternUtils.buildMessage("");

    assertNotNull(message);
    assertEquals("", plain(message));
  }
}
