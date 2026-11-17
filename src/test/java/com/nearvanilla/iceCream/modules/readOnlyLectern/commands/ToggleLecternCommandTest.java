package com.nearvanilla.iceCream.modules.readOnlyLectern.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import com.nearvanilla.iceCream.testutil.LecternTestUtils.LookingPlayer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

class ToggleLecternCommandTest {
  private static final String NOT_LOOKING = "You are not looking at a valid lectern.";
  private static final String NOT_ELIGIBLE =
      "This lectern does not contain a player-authored book.";
  private static final String NOW_READ_ONLY = "This lectern is now read-only.";
  private static final String NO_LONGER_READ_ONLY = "This lectern is no longer read-only.";

  private ServerMock server;
  private World world;
  private LookingPlayer player;
  private final ToggleLecternCommand command = new ToggleLecternCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    ReadOnlyLecternModule.READ_ONLY_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void install(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    ReadOnlyLecternModule.READ_ONLY_KEY = new NamespacedKey(IceCream.instance, "lectern.readonly");
    world = server.addSimpleWorld("world");
  }

  private void lookAt(Block target) {
    player = LecternTestUtils.addLookingPlayer(server, "Reader", target);
  }

  private void toggle(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.toggleLecternCommand(source);
  }

  private Block lecternWithBook() {
    return LecternTestUtils.lecternWithBook(world, 0, 64, 0);
  }

  @Test
  void firstUseMakesTheLecternReadOnly() {
    install("");
    Block lectern = lecternWithBook();
    lookAt(lectern);

    toggle(player);

    assertTrue(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void firstUseTellsThePlayerTheLecternIsReadOnly() {
    install("");
    lookAt(lecternWithBook());

    toggle(player);

    assertEquals(NOW_READ_ONLY, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void secondUseMakesTheLecternEditableAgain() {
    install("");
    Block lectern = lecternWithBook();
    lookAt(lectern);
    toggle(player);
    nextMessage(player);

    toggle(player);

    assertFalse(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void secondUseTellsThePlayerTheLecternIsNoLongerReadOnly() {
    install("");
    lookAt(lecternWithBook());
    toggle(player);
    nextMessage(player);

    toggle(player);

    assertEquals(NO_LONGER_READ_ONLY, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void thirdUseMakesTheLecternReadOnlyAgain() {
    install("");
    Block lectern = lecternWithBook();
    lookAt(lectern);
    toggle(player);
    toggle(player);
    nextMessage(player);
    nextMessage(player);

    toggle(player);

    assertTrue(LecternUtils.isReadOnly(lectern));
    assertEquals(NOW_READ_ONLY, nextMessage(player));
  }

  @Test
  void onlyTheLecternThePlayerLooksAtChanges() {
    install("");
    Block target = lecternWithBook();
    Block other = LecternTestUtils.lecternWithBook(world, 2, 64, 0);
    lookAt(target);

    toggle(player);

    assertFalse(LecternUtils.isReadOnly(other));
  }

  @Test
  void refusesAPlayerLookingAtNothing() {
    install("");
    lookAt(null);

    toggle(player);

    assertEquals(NOT_LOOKING, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void refusesAPlayerLookingAtAnotherBlock() {
    install("");
    Block stone = world.getBlockAt(0, 64, 0);
    stone.setType(Material.STONE);
    lookAt(stone);

    toggle(player);

    assertEquals(NOT_LOOKING, nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void refusesALecternWithNoBook() {
    install("");
    Block lectern = LecternTestUtils.lectern(world, 0, 64, 0);
    lookAt(lectern);

    toggle(player);

    assertEquals(NOT_ELIGIBLE, nextMessage(player));
    assertNull(nextMessage(player));
    assertFalse(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void refusesALecternWithAnUnsignedBook() {
    install("");
    Block lectern = LecternTestUtils.lectern(world, 0, 64, 0);
    LecternTestUtils.placeBook(lectern, new ItemStack(Material.WRITABLE_BOOK));
    lookAt(lectern);

    toggle(player);

    assertEquals(NOT_ELIGIBLE, nextMessage(player));
    assertFalse(LecternUtils.isReadOnly(lectern));
  }

  @Test
  void looksAtTheDefaultDistanceWhenTheConfigHasNone() {
    install("");
    lookAt(lecternWithBook());

    toggle(player);

    assertEquals(5, player.requestedDistance());
  }

  @Test
  void looksAtTheConfiguredDistance() {
    install("modules:\n  readonlylectern:\n    max-distance: 9\n");
    lookAt(lecternWithBook());

    toggle(player);

    assertEquals(9, player.requestedDistance());
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    install("");
    lookAt(lecternWithBook());
    ConsoleCommandSenderMock console = server.getConsoleSender();

    toggle(console);

    assertEquals("Only players can use this command.", nextMessage(console));
    assertNull(console.nextComponentMessage());
    assertEquals(-1, player.requestedDistance());
    assertNull(nextMessage(player));
  }
}
