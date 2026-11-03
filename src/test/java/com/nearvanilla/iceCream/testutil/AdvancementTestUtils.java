package com.nearvanilla.iceCream.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.UUID;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Helpers for testing advancement events with MockBukkit, which has no advancement support. */
public final class AdvancementTestUtils {

  private AdvancementTestUtils() {}

  /**
   * Adds a player that supports {@link PlayerMock#getAdvancementProgress(Advancement)}, which
   * MockBukkit does not implement and Paper's advancement events call in their constructor.
   *
   * @param server the mock server
   * @param name the player name
   * @return the added player
   */
  public static PlayerMock addPlayer(ServerMock server, String name) {
    PlayerMock player =
        new PlayerMock(server, name, UUID.randomUUID()) {
          @Override
          public AdvancementProgress getAdvancementProgress(Advancement advancement) {
            return mock(AdvancementProgress.class);
          }
        };
    server.addPlayer(player);
    return player;
  }

  /**
   * Creates an advancement, since MockBukkit has no advancement implementation.
   *
   * @param visible whether the advancement has a display, like those shown in the advancement
   *     screen
   * @return a mocked advancement
   */
  public static Advancement advancement(boolean visible) {
    Advancement advancement = mock(Advancement.class);
    if (visible) {
      when(advancement.getDisplay()).thenReturn(mock(AdvancementDisplay.class));
    }
    return advancement;
  }
}
