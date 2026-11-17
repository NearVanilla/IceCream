package com.nearvanilla.iceCream.modules.muteDeaths.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MuteDeathsPlayerJoinEventTest {
  private ServerMock server;
  private PlayerMock player;
  private final MuteDeathsPlayerJoinEvent listener = new MuteDeathsPlayerJoinEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer("Sleeper");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private void join(PlayerMock joining) {
    listener.onPlayerJoin(new PlayerJoinEvent(joining, Component.text("joined")));
  }

  private void setMuted(PlayerMock target, boolean muted) {
    target
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, muted);
  }

  private List<ClickEvent> clickEventsIn(Component component) {
    List<ClickEvent> found = new ArrayList<>();
    collectClickEvents(component, found);
    return found;
  }

  private void collectClickEvents(Component component, List<ClickEvent> found) {
    if (component.clickEvent() != null) {
      found.add(component.clickEvent());
    }
    component.children().forEach(child -> collectClickEvents(child, found));
  }

  @Test
  void remindsAMutedPlayerThatTheirDeathMessagesAreMuted() {
    setMuted(player, true);

    join(player);

    assertEquals("🔕 Your death messages are muted. [Toggle]", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void theReminderOffersAClickableToggle() {
    setMuted(player, true);

    join(player);

    Component reminder = player.nextComponentMessage();
    assertNotNull(reminder);
    List<ClickEvent> clicks = clickEventsIn(reminder);
    assertEquals(1, clicks.size());
    assertEquals(ClickEvent.Action.RUN_COMMAND, clicks.getFirst().action());
    assertEquals("/mutedeaths", ((ClickEvent.Payload.Text) clicks.getFirst().payload()).value());
  }

  @Test
  void staysSilentForAPlayerWhoNeverMuted() {
    join(player);

    assertNull(nextMessage(player));
  }

  @Test
  void staysSilentForAPlayerWhoUnmuted() {
    setMuted(player, false);

    join(player);

    assertNull(nextMessage(player));
  }

  @Test
  void remindsOnlyTheJoiningPlayer() {
    PlayerMock other = server.addPlayer("Other");
    setMuted(other, true);

    join(player);

    assertNull(nextMessage(player));
    assertNull(nextMessage(other));
  }
}
