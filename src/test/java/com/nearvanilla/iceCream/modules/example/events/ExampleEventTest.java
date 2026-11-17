package com.nearvanilla.iceCream.modules.example.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A listener is tested by registering it with the mock server and then causing the event it
 * handles. Going through the server means the test also proves that the {@code @EventHandler}
 * annotation is picked up, not just that the method body works. {@link IceCreamFixture} provides
 * the plugin to register against, and {@link MessageTestUtils} reads what the player received as
 * plain text.
 */
class ExampleEventTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    // Listeners are registered against the plugin, so the plugin has to exist first.
    IceCreamFixture.install(server, "");
    server.getPluginManager().registerEvents(new ExampleEvent(), IceCream.instance);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  @Test
  void welcomesAPlayerWhoJoins() {
    // MockBukkit fires a PlayerJoinEvent for every player it adds, so adding the player is what
    // triggers the listener.
    PlayerMock player = server.addPlayer();

    assertEquals(
        "Welcome to the server! This is an example event.", MessageTestUtils.nextMessage(player));
    assertNull(player.nextComponentMessage());
  }

  @Test
  void onlyWelcomesThePlayerWhoJoined() {
    PlayerMock bystander = server.addPlayer("Bystander");
    bystander.nextComponentMessage();

    server.addPlayer("Joiner");

    assertNull(bystander.nextComponentMessage());
  }
}
