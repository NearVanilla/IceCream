package com.nearvanilla.iceCream.modules.firstJoinMessage.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FirstJoinMessagePlayerJoinEventTest {
  private static final String FULL_CONFIG =
      """
      modules:
        firstjoinmessage:
          enabled: true
          announcement: '<yellow>Welcome <gold><player></gold>!</yellow>'
          welcome-message:
            - '<green>Read the rules, <player>.</green>'
            - '<aqua>Have fun.</aqua>'
      """;

  private ServerMock server;
  private final FirstJoinMessagePlayerJoinEvent listener = new FirstJoinMessagePlayerJoinEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private PlayerMock newPlayer(String name) {
    return server.addPlayer(name);
  }

  private PlayerMock returningPlayer(String name) {
    PlayerMock player = server.addPlayer(name);
    server.getPlayerList().setFirstPlayed(player.getUniqueId(), 1L);
    return player;
  }

  private void join(PlayerMock player) {
    listener.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
  }

  private List<String> received(PlayerMock player) {
    List<String> messages = new ArrayList<>();
    Component message;
    while ((message = player.nextComponentMessage()) != null) {
      messages.add(plainText(message));
    }
    return messages;
  }

  @Test
  void firstTimePlayerReceivesTheAnnouncementAndThenTheWelcomeMessage() {
    IceCreamFixture.install(server, FULL_CONFIG);
    PlayerMock player = newPlayer("Newbie");

    join(player);

    assertEquals(
        List.of("Welcome Newbie!", "Read the rules, Newbie.", "Have fun."), received(player));
  }

  @Test
  void everyoneElseReceivesOnlyTheAnnouncement() {
    IceCreamFixture.install(server, FULL_CONFIG);
    PlayerMock veteran = returningPlayer("Veteran");
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertEquals(List.of("Welcome Newbie!"), received(veteran));
  }

  @Test
  void returningPlayerTriggersNothing() {
    IceCreamFixture.install(server, FULL_CONFIG);
    PlayerMock returning = returningPlayer("Returning");
    PlayerMock bystander = returningPlayer("Bystander");

    join(returning);

    assertTrue(received(returning).isEmpty());
    assertTrue(received(bystander).isEmpty());
  }

  @Test
  void noAnnouncementIsSentWhenItIsNotConfigured() {
    IceCreamFixture.install(
        server,
        """
        modules:
          firstjoinmessage:
            welcome-message:
              - 'Hello <player>'
        """);
    PlayerMock veteran = returningPlayer("Veteran");
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertTrue(received(veteran).isEmpty());
    assertEquals(List.of("Hello Newbie"), received(newcomer));
  }

  @Test
  void noAnnouncementIsSentWhenItIsBlank() {
    IceCreamFixture.install(
        server,
        """
        modules:
          firstjoinmessage:
            announcement: '   '
            welcome-message:
              - 'Hello <player>'
        """);
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertEquals(List.of("Hello Newbie"), received(newcomer));
  }

  @Test
  void noWelcomeMessageIsSentWhenItIsNotConfigured() {
    IceCreamFixture.install(
        server,
        """
        modules:
          firstjoinmessage:
            announcement: 'Welcome <player>'
        """);
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertEquals(List.of("Welcome Newbie"), received(newcomer));
  }

  @Test
  void noWelcomeMessageIsSentWhenTheListIsEmpty() {
    IceCreamFixture.install(
        server,
        """
        modules:
          firstjoinmessage:
            announcement: 'Welcome <player>'
            welcome-message: []
        """);
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertEquals(List.of("Welcome Newbie"), received(newcomer));
  }

  @Test
  void nothingIsSentWhenNeitherMessageIsConfigured() {
    IceCreamFixture.install(server, "");
    PlayerMock veteran = returningPlayer("Veteran");
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertTrue(received(veteran).isEmpty());
    assertTrue(received(newcomer).isEmpty());
  }

  @Test
  void everyWelcomeLineCanUseThePlayerPlaceholder() {
    IceCreamFixture.install(
        server,
        """
        modules:
          firstjoinmessage:
            welcome-message:
              - 'One <player>'
              - 'Two <player>'
              - 'Three'
        """);
    PlayerMock newcomer = newPlayer("Newbie");

    join(newcomer);

    assertEquals(List.of("One Newbie", "Two Newbie", "Three"), received(newcomer));
  }

  @Test
  void displayNameIsInsertedAsTextNotAsMiniMessage() {
    IceCreamFixture.install(server, FULL_CONFIG);
    PlayerMock newcomer = newPlayer("Newbie");
    newcomer.displayName(Component.text("<red>Mallory</red><click:run_command:'/op me'>"));

    join(newcomer);

    List<String> messages = received(newcomer);
    assertEquals("Welcome <red>Mallory</red><click:run_command:'/op me'>!", messages.getFirst());
    assertEquals(
        "Read the rules, <red>Mallory</red><click:run_command:'/op me'>.", messages.get(1));
  }

  @Test
  void aRealJoinEventOnTheServerGreetsAFirstTimePlayer() {
    IceCreamFixture.install(server, FULL_CONFIG);
    server.getPluginManager().registerEvents(listener, IceCream.instance);

    // MockBukkit fires a PlayerJoinEvent for every player it adds.
    PlayerMock newcomer = newPlayer("Newbie");

    assertEquals(
        List.of("Welcome Newbie!", "Read the rules, Newbie.", "Have fun."), received(newcomer));
  }
}
