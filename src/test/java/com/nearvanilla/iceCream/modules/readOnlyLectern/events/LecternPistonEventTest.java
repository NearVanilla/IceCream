package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class LecternPistonEventTest {
  private ServerMock server;
  private World world;
  private final LecternPistonEvent listener = new LecternPistonEvent();

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

  private Block readOnlyLectern(int x) {
    Block lectern = LecternTestUtils.lecternWithBook(world, x, 64, 0);
    LecternUtils.setReadOnly(lectern, true);
    return lectern;
  }

  private Block editableLectern(int x) {
    return LecternTestUtils.lecternWithBook(world, x, 64, 0);
  }

  private Block stone(int x) {
    Block block = world.getBlockAt(x, 64, 0);
    block.setType(Material.STONE);
    return block;
  }

  private BlockPistonExtendEvent extend(List<Block> blocks) {
    BlockPistonExtendEvent event = new BlockPistonExtendEvent(stone(20), blocks, BlockFace.UP);
    listener.onPistonExtend(event);
    return event;
  }

  private BlockPistonRetractEvent retract(List<Block> blocks) {
    BlockPistonRetractEvent event = new BlockPistonRetractEvent(stone(20), blocks, BlockFace.UP);
    listener.onPistonRetract(event);
    return event;
  }

  @Test
  void cancelsAPistonPushingAReadOnlyLectern() {
    assertTrue(extend(List.of(readOnlyLectern(0))).isCancelled());
  }

  @Test
  void cancelsAPistonPushingAReadOnlyLecternAmongOtherBlocks() {
    assertTrue(extend(List.of(stone(0), editableLectern(2), readOnlyLectern(4))).isCancelled());
  }

  @Test
  void cancelsAPistonPushingAReadOnlyLecternFirst() {
    assertTrue(extend(List.of(readOnlyLectern(0), stone(2))).isCancelled());
  }

  @Test
  void allowsAPistonPushingEditableLecternsAndOtherBlocks() {
    assertFalse(extend(List.of(stone(0), editableLectern(2))).isCancelled());
  }

  @Test
  void allowsAPistonPushingNothing() {
    assertFalse(extend(List.of()).isCancelled());
  }

  @Test
  void cancelsAPistonPullingAReadOnlyLectern() {
    assertTrue(retract(List.of(readOnlyLectern(0))).isCancelled());
  }

  @Test
  void cancelsAPistonPullingAReadOnlyLecternAmongOtherBlocks() {
    assertTrue(retract(List.of(stone(0), editableLectern(2), readOnlyLectern(4))).isCancelled());
  }

  @Test
  void cancelsAPistonPullingAReadOnlyLecternFirst() {
    assertTrue(retract(List.of(readOnlyLectern(0), stone(2))).isCancelled());
  }

  @Test
  void allowsAPistonPullingEditableLecternsAndOtherBlocks() {
    assertFalse(retract(List.of(stone(0), editableLectern(2))).isCancelled());
  }

  @Test
  void allowsAPistonPullingNothing() {
    assertFalse(retract(List.of()).isCancelled());
  }

  @Test
  void worksWhenFiredThroughThePluginManager() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    BlockPistonExtendEvent pushed =
        new BlockPistonExtendEvent(stone(20), List.of(readOnlyLectern(0)), BlockFace.UP);
    BlockPistonRetractEvent pulled =
        new BlockPistonRetractEvent(stone(22), List.of(readOnlyLectern(2)), BlockFace.UP);

    server.getPluginManager().callEvent(pushed);
    server.getPluginManager().callEvent(pulled);

    assertTrue(pushed.isCancelled());
    assertTrue(pulled.isCancelled());
  }
}
