package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LecternBlockBreakEventTest {
  private ServerMock server;
  private World world;
  private PlayerMock player;
  private final LecternBlockBreakEvent listener = new LecternBlockBreakEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    ReadOnlyLecternModule.READ_ONLY_KEY = new NamespacedKey(IceCream.instance, "lectern.readonly");
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Breaker");
  }

  @AfterEach
  void tearDown() {
    ReadOnlyLecternModule.READ_ONLY_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Block readOnlyLectern() {
    Block lectern = LecternTestUtils.lecternWithBook(world, 0, 64, 0);
    LecternUtils.setReadOnly(lectern, true);
    return lectern;
  }

  private BlockBreakEvent breakBlock(Block block, PlayerMock breaker) {
    BlockBreakEvent event = new BlockBreakEvent(block, breaker);
    listener.onBlockBreak(event);
    return event;
  }

  @Test
  void cancelsBreakingAReadOnlyLectern() {
    assertTrue(breakBlock(readOnlyLectern(), player).isCancelled());
  }

  @Test
  void tellsThePlayerTheLecternIsReadOnly() {
    breakBlock(readOnlyLectern(), player);

    assertEquals("This lectern is read-only.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void stillCancelsWhenThereIsNoPlayerToTell() {
    assertTrue(breakBlock(readOnlyLectern(), null).isCancelled());
  }

  @Test
  void allowsBreakingALecternThatIsNotReadOnly() {
    Block lectern = LecternTestUtils.lecternWithBook(world, 0, 64, 0);

    assertFalse(breakBlock(lectern, player).isCancelled());
    assertNull(nextMessage(player));
  }

  @Test
  void allowsBreakingALecternThatWasMadeEditableAgain() {
    Block lectern = readOnlyLectern();
    LecternUtils.setReadOnly(lectern, false);

    assertFalse(breakBlock(lectern, player).isCancelled());
    assertNull(nextMessage(player));
  }

  @Test
  void allowsBreakingOtherBlocks() {
    Block stone = world.getBlockAt(3, 64, 0);
    stone.setType(Material.STONE);

    assertFalse(breakBlock(stone, player).isCancelled());
    assertNull(nextMessage(player));
  }

  @Test
  void worksWhenFiredThroughThePluginManager() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    BlockBreakEvent event = new BlockBreakEvent(readOnlyLectern(), player);

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertEquals("This lectern is read-only.", nextMessage(player));
  }
}
