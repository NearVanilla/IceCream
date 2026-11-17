package com.nearvanilla.iceCream.modules.isSlimeChunk.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.ChunkMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class isSlimeChunkCommandTest {
  private ServerMock server;
  private WorldMock world;
  private PlayerMock player;
  private final isSlimeChunkCommand command = new isSlimeChunkCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Slimer");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private void run(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.exampleCommand(source);
  }

  private void standInChunk(int blockX, int blockZ, boolean slime) {
    Location location = new Location(world, blockX, 64, blockZ);
    ((ChunkMock) world.getChunkAt(location)).setSlimeChunk(slime);
    player.teleport(location);
  }

  @Test
  void tellsThePlayerWhenTheChunkIsASlimeChunk() {
    standInChunk(8, 8, true);

    run(player);

    assertEquals("This chunk is a slime chunk.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void tellsThePlayerWhenTheChunkIsNotASlimeChunk() {
    standInChunk(8, 8, false);

    run(player);

    assertEquals("This chunk is not a slime chunk.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void checksTheChunkThePlayerStandsIn() {
    standInChunk(8, 8, true);
    ((ChunkMock) world.getChunkAt(new Location(world, 40, 64, 40))).setSlimeChunk(false);

    player.teleport(new Location(world, 40, 64, 40));
    run(player);

    assertEquals("This chunk is not a slime chunk.", nextMessage(player));
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    run(console);

    assertEquals("This command can only be used by players.", nextMessage(console));
    assertNull(console.nextComponentMessage());
    assertNull(player.nextComponentMessage());
  }
}
