package com.nearvanilla.iceCream.modules.readOnlyLectern.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.LecternUtils;
import com.nearvanilla.iceCream.modules.readOnlyLectern.ReadOnlyLecternModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LecternTestUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LecternTakeBookEventTest {
  private ServerMock server;
  private World world;
  private PlayerMock player;
  private final LecternTakeBookEvent listener = new LecternTakeBookEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    ReadOnlyLecternModule.READ_ONLY_KEY = new NamespacedKey(IceCream.instance, "lectern.readonly");
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Reader");
  }

  @AfterEach
  void tearDown() {
    ReadOnlyLecternModule.READ_ONLY_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Block lecternWithBook(int x) {
    return LecternTestUtils.lecternWithBook(world, x, 64, 0);
  }

  private PlayerTakeLecternBookEvent takeBook(Block lectern) {
    PlayerTakeLecternBookEvent event =
        new PlayerTakeLecternBookEvent(player, (Lectern) lectern.getState());
    listener.onPlayerTakeLecternBook(event);
    return event;
  }

  @Test
  void cancelsTakingTheBookFromAReadOnlyLectern() {
    Block lectern = lecternWithBook(0);
    LecternUtils.setReadOnly(lectern, true);

    assertTrue(takeBook(lectern).isCancelled());
  }

  @Test
  void allowsTakingTheBookFromALecternThatIsNotReadOnly() {
    assertFalse(takeBook(lecternWithBook(0)).isCancelled());
  }

  @Test
  void allowsTakingTheBookFromALecternThatWasMadeEditableAgain() {
    Block lectern = lecternWithBook(0);
    LecternUtils.setReadOnly(lectern, true);
    LecternUtils.setReadOnly(lectern, false);

    assertFalse(takeBook(lectern).isCancelled());
  }

  @Test
  void onlyProtectsTheLecternThatIsReadOnly() {
    Block readOnly = lecternWithBook(0);
    Block editable = lecternWithBook(2);
    LecternUtils.setReadOnly(readOnly, true);

    assertFalse(takeBook(editable).isCancelled());
  }

  @Test
  void worksWhenFiredThroughThePluginManager() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    Block lectern = lecternWithBook(0);
    LecternUtils.setReadOnly(lectern, true);
    PlayerTakeLecternBookEvent event =
        new PlayerTakeLecternBookEvent(player, (Lectern) lectern.getState());

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
  }
}
