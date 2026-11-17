package com.nearvanilla.iceCream.modules.readOnlyLectern;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.readOnlyLectern.commands.ToggleLecternCommand;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.Lectern;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.CreeperMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ReadOnlyLecternModuleTest {
  private static final String ENABLED = "modules:\n  readonlylectern:\n    enabled: true\n";

  private ServerMock server;
  private World world;
  private PlayerMock player;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Reader");
  }

  @AfterEach
  void tearDown() {
    ReadOnlyLecternModule.READ_ONLY_KEY = null;
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private ReadOnlyLecternModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new ReadOnlyLecternModule();
  }

  // Registers the module, which also creates the key, then makes a lectern read-only.
  private Block readOnlyLectern(ReadOnlyLecternModule module, int x) {
    module.register();
    Block lectern = LecternTestUtils.lecternWithBook(world, x, 64, 0);
    LecternUtils.setReadOnly(lectern, true);
    return lectern;
  }

  private Block stone(int x) {
    Block block = world.getBlockAt(x, 64, 0);
    block.setType(Material.STONE);
    return block;
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  readonlylectern:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    ReadOnlyLecternModule module = module("");
    Block lectern = readOnlyLectern(module, 0);
    BlockBreakEvent event = new BlockBreakEvent(lectern, player);

    server.getPluginManager().callEvent(event);

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertFalse(event.isCancelled());
    assertNull(nextMessage(player));
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    ReadOnlyLecternModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(ToggleLecternCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheBlockBreakListener() {
    Block lectern = readOnlyLectern(module(ENABLED), 0);
    BlockBreakEvent event = new BlockBreakEvent(lectern, player);

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertEquals("This lectern is read-only.", nextMessage(player));
  }

  @Test
  void registerWhenEnabledRegistersTheTakeBookListener() {
    Block lectern = readOnlyLectern(module(ENABLED), 0);
    PlayerTakeLecternBookEvent event =
        new PlayerTakeLecternBookEvent(player, (Lectern) lectern.getState());

    server.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void registerWhenEnabledRegistersTheExplosionListener() {
    Block lectern = readOnlyLectern(module(ENABLED), 0);
    Block plain = stone(2);
    EntityExplodeEvent event =
        new EntityExplodeEvent(
            new CreeperMock(server, UUID.randomUUID()),
            new Location(world, 0, 64, 0),
            new ArrayList<>(List.of(lectern, plain)),
            1.0f,
            ExplosionResult.DESTROY);

    server.getPluginManager().callEvent(event);

    assertEquals(List.of(plain), event.blockList());
  }

  @Test
  void registerWhenEnabledRegistersThePistonListeners() {
    Block lectern = readOnlyLectern(module(ENABLED), 0);
    BlockPistonExtendEvent pushed =
        new BlockPistonExtendEvent(stone(10), List.of(lectern), BlockFace.UP);
    BlockPistonRetractEvent pulled =
        new BlockPistonRetractEvent(stone(12), List.of(lectern), BlockFace.UP);

    server.getPluginManager().callEvent(pushed);
    server.getPluginManager().callEvent(pulled);

    assertTrue(pushed.isCancelled());
    assertTrue(pulled.isCancelled());
  }

  @Test
  void registerCreatesTheKey() {
    IceCreamFixture.install(server, ENABLED);
    assertNull(ReadOnlyLecternModule.READ_ONLY_KEY);

    new ReadOnlyLecternModule().register();

    assertEquals(
        new NamespacedKey("icecream", "lectern.readonly"), ReadOnlyLecternModule.READ_ONLY_KEY);
  }

  @Test
  void registerCreatesTheKeyEvenWhenDisabled() {
    IceCreamFixture.install(server, "");

    new ReadOnlyLecternModule().register();

    assertEquals(
        new NamespacedKey("icecream", "lectern.readonly"), ReadOnlyLecternModule.READ_ONLY_KEY);
  }

  @Test
  void registerKeepsAnExistingKey() {
    ReadOnlyLecternModule module = module(ENABLED);
    NamespacedKey existing = new NamespacedKey("other", "lectern");
    ReadOnlyLecternModule.READ_ONLY_KEY = existing;

    module.register();

    assertSame(existing, ReadOnlyLecternModule.READ_ONLY_KEY);
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    ReadOnlyLecternModule module = module(ENABLED);
    when(IceCream.annotationParser.parse(any(Object.class)))
        .thenThrow(new IllegalStateException("no parser"));
    module.register();
    Block lectern = LecternTestUtils.lecternWithBook(world, 0, 64, 0);
    LecternUtils.setReadOnly(lectern, true);
    BlockBreakEvent event = new BlockBreakEvent(lectern, player);

    server.getPluginManager().callEvent(event);

    assertFalse(module.isEnabled());
    assertFalse(event.isCancelled());
  }

  @Test
  void registerWhenEventRegistrationFailsStaysDisabled() {
    ReadOnlyLecternModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
