package com.nearvanilla.iceCream.modules.tag.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.tag.TagConfig;
import com.nearvanilla.iceCream.modules.tag.TagCounterSlot;
import com.nearvanilla.iceCream.modules.tag.TagGame;
import com.nearvanilla.iceCream.modules.tag.TagItem;
import com.nearvanilla.iceCream.modules.tag.TagMessages;
import com.nearvanilla.iceCream.modules.tag.TagStore;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TagCommandTest {
  private final TagStore store = new TagStore("icecream");
  private ServerMock server;
  private TagGame game;
  private TagCommand command;
  private PlayerMock alex;
  private PlayerMock steve;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    game =
        new TagGame(
            new TagConfig(false, 0, true),
            store,
            new TagItem("icecream"),
            server.getScoreboardManager().getMainScoreboard(),
            mock(Audience.class),
            System::currentTimeMillis);
    command = new TagCommand(game);
    alex = server.addPlayer("Alex");
    steve = server.addPlayer("Steve");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static CommandSourceStack source(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    return source;
  }

  @Test
  void optOutAndOptIn() {
    command.optOut(source(steve));
    assertTrue(store.isOptedOut(steve));

    command.optIn(source(steve));
    assertFalse(store.isOptedOut(steve));
  }

  @Test
  void optOutIsForPlayersOnly() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.optOut(source(console));

    assertEquals(TagMessages.PLAYERS_ONLY, console.nextComponentMessage());
  }

  @Test
  void giveTagWithoutAPlayerGivesItToTheSender() {
    command.giveTag(source(alex), null);

    assertTrue(store.isIt(alex));
  }

  @Test
  void giveTagToAnotherPlayer() {
    command.giveTag(source(alex), steve);

    assertTrue(store.isIt(steve));
    assertFalse(store.isIt(alex));
  }

  @Test
  void consoleMustNameAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    command.giveTag(source(console), null);

    assertEquals(TagMessages.PLAYERS_ONLY, console.nextComponentMessage());

    command.giveTag(source(console), steve);
    assertTrue(store.isIt(steve));
  }

  @Test
  void resetClearsIt() {
    command.giveTag(source(alex), steve);

    command.reset(source(alex));

    assertFalse(store.isIt(steve));
  }

  @Test
  void counterReportsWhereItIsShown() {
    command.counter(source(alex), TagCounterSlot.SIDEBAR);
    assertEquals(TagMessages.counterShown("sidebar"), alex.nextComponentMessage());

    command.counter(source(alex), TagCounterSlot.SIDEBAR);
    assertEquals(TagMessages.COUNTER_ALREADY, alex.nextComponentMessage());

    command.counter(source(alex), TagCounterSlot.NONE);
    assertEquals(TagMessages.COUNTER_HIDDEN, alex.nextComponentMessage());
  }
}
