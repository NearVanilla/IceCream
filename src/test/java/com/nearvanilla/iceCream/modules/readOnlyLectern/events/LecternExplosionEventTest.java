package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.CreeperMock;

class LecternExplosionEventTest {
  private ServerMock server;
  private World world;
  private final LecternExplosionEvent listener = new LecternExplosionEvent();

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

  private EntityExplodeEvent explode(List<Block> blocks) {
    EntityExplodeEvent event =
        new EntityExplodeEvent(
            new CreeperMock(server, UUID.randomUUID()),
            new Location(world, 0, 64, 0),
            blocks,
            1.0f,
            ExplosionResult.DESTROY);
    listener.onExplosion(event);
    return event;
  }

  @Test
  void keepsAReadOnlyLecternOutOfTheExplosion() {
    EntityExplodeEvent event = explode(new ArrayList<>(List.of(readOnlyLectern(0))));

    assertEquals(List.of(), event.blockList());
  }

  @Test
  void removesOnlyTheReadOnlyLecterns() {
    Block firstReadOnly = readOnlyLectern(0);
    Block editable = editableLectern(2);
    Block plain = stone(4);
    Block secondReadOnly = readOnlyLectern(6);

    EntityExplodeEvent event =
        explode(new ArrayList<>(List.of(firstReadOnly, editable, plain, secondReadOnly)));

    assertEquals(List.of(editable, plain), event.blockList());
  }

  @Test
  void leavesAnExplosionWithoutReadOnlyLectern() {
    Block editable = editableLectern(0);
    Block plain = stone(2);

    EntityExplodeEvent event = explode(new ArrayList<>(List.of(editable, plain)));

    assertEquals(List.of(editable, plain), event.blockList());
  }

  @Test
  void leavesAnEmptyExplosionAlone() {
    EntityExplodeEvent event = explode(new ArrayList<>());

    assertEquals(List.of(), event.blockList());
  }

  @Test
  void worksWhenFiredThroughThePluginManager() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    Block plain = stone(2);
    EntityExplodeEvent event =
        new EntityExplodeEvent(
            new CreeperMock(server, UUID.randomUUID()),
            new Location(world, 0, 64, 0),
            new ArrayList<>(List.of(readOnlyLectern(0), plain)),
            1.0f,
            ExplosionResult.DESTROY);

    server.getPluginManager().callEvent(event);

    assertEquals(List.of(plain), event.blockList());
  }
}
