package com.nearvanilla.iceCream.modules.muteDeaths.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MuteDeathsPlayerDeathEventTest {
  private static final Component DEATH_MESSAGE = Component.text("Sleeper fell from a high place");

  private PlayerMock player;
  private final MuteDeathsPlayerDeathEvent listener = new MuteDeathsPlayerDeathEvent();

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    player = server.addPlayer("Sleeper");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private PlayerDeathEvent die() {
    PlayerDeathEvent event =
        new PlayerDeathEvent(
            player,
            DamageSource.builder(DamageType.FALL).build(),
            new ArrayList<>(),
            0,
            DEATH_MESSAGE,
            true);
    listener.onPlayerDeath(event);
    return event;
  }

  private void setMuted(boolean muted) {
    player
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, muted);
  }

  @Test
  void removesTheDeathMessageOfAMutedPlayer() {
    setMuted(true);

    assertNull(die().deathMessage());
  }

  @Test
  void keepsTheDeathMessageOfAPlayerWhoNeverMuted() {
    assertEquals(DEATH_MESSAGE, die().deathMessage());
  }

  @Test
  void keepsTheDeathMessageOfAPlayerWhoUnmuted() {
    setMuted(false);

    assertEquals(DEATH_MESSAGE, die().deathMessage());
  }

  @Test
  void keepsTheDeathMessageOfAnotherPlayerWhenSomeoneElseIsMuted() {
    PlayerMock other = MockBukkit.getMock().addPlayer("Other");
    other
        .getPersistentDataContainer()
        .set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, true);

    assertEquals(DEATH_MESSAGE, die().deathMessage());
  }
}
