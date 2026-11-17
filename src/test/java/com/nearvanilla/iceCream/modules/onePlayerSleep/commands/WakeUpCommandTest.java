package com.nearvanilla.iceCream.modules.onePlayerSleep.commands;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addPlayer;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.addWorld;
import static com.nearvanilla.iceCream.testutil.SleepTestUtils.wakeToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.onePlayerSleep.OnePlayerSleepModule;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.SleepTestUtils.SleepyPlayer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

class WakeUpCommandTest {
  private static final String ENABLED = "modules:\n  oneplayersleep:\n    enabled: true\n";
  private static final String INACTIVE = "That sleep request is no longer active.";

  private ServerMock server;
  private OnePlayerSleepModule module;
  private SleepyPlayer sleeper;
  private SleepyPlayer watcher;
  private WakeUpCommand command;
  private String token;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, ENABLED);
    addWorld(server, "world", 13000);
    sleeper = addPlayer(server, "Sleeper");
    watcher = addPlayer(server, "Watcher");
    module = new OnePlayerSleepModule();
    module.register();
    HandlerList.unregisterAll(IceCream.instance);
    command = new WakeUpCommand(module);
    sleeper.goToBed();
    module.queueSleepCheck(sleeper);
    server.getScheduler().performOneTick();
    token = wakeToken(watcher.nextComponentMessage());
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void wakeUp(CommandSender sender, String sentToken) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.wakeUp(source, sentToken);
  }

  @Test
  void aPlayerWithTheTokenWakesTheSleepers() {
    wakeUp(watcher, token);

    assertFalse(sleeper.isSleeping());
    assertEquals(1, sleeper.wakeCalls());
    assertTrue(module.isCoolingDown());
  }

  @Test
  void aPlayerWithTheTokenAnnouncesTheyKeepTheNight() {
    wakeUp(watcher, token);

    assertEquals("Watcher wants to keep the night", nextMessage(watcher));
    assertNull(nextMessage(watcher));
  }

  @Test
  void aPlayerWithAnUnknownTokenIsToldTheRequestIsInactive() {
    wakeUp(watcher, "not-a-token");

    assertEquals(INACTIVE, nextMessage(watcher));
    assertNull(nextMessage(watcher));
    assertTrue(sleeper.isSleeping());
    assertFalse(module.isCoolingDown());
  }

  @Test
  void aPlayerIsToldTheRequestIsInactiveWhileTheModuleIsDisabled() {
    module.unregister();
    sleeper.goToBed();

    wakeUp(watcher, token);

    assertEquals(INACTIVE, nextMessage(watcher));
    assertTrue(sleeper.isSleeping());
  }

  @Test
  void aSleeperCanWakeThemselves() {
    wakeUp(sleeper, token);

    assertFalse(sleeper.isSleeping());
    assertTrue(module.isCoolingDown());
  }

  @Test
  void theConsoleIsToldOnlyPlayersCanUseTheCommand() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    wakeUp(console, token);

    assertEquals("Only players can use this command.", nextMessage(console));
    assertNull(nextMessage(console));
  }

  @Test
  void theConsoleCannotWakeTheSleepers() {
    wakeUp(server.getConsoleSender(), token);

    assertTrue(sleeper.isSleeping());
    assertFalse(module.isCoolingDown());
    assertNull(nextMessage(watcher));
  }
}
