package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Helpers for testing the world tour module with MockBukkit. A tour moves players around and keeps
 * its participants in a persistent data flag, so the helpers cover the flag, the players and the
 * command source the tour's commands read.
 */
public final class WorldTourTestUtils {
  /**
   * The configuration of an enabled world tour module with every value spelled out. It matches the
   * defaults the plugin ships, so a test can assert on the values.
   */
  public static final String CONFIG =
      """
      modules:
        worldtour:
          enabled: true
          failsafe-timeout-minutes: 10
          start-time-ticks: 1000
          times:
            day: 1000
            noon: 6000
            night: 13000
            midnight: 18000
      """;

  private WorldTourTestUtils() {}

  /**
   * Adds a player that can be saved. MockBukkit throws {@code UnimplementedOperationException} from
   * {@code saveData()}, which the tour's disconnect failsafe calls when it opts a player out, so
   * this player overrides it to count the saves it receives.
   *
   * @param server the mock server
   * @param name the player name
   * @return the added player
   */
  public static TourPlayer addPlayer(ServerMock server, String name) {
    TourPlayer player = new TourPlayer(server, name);
    server.addPlayer(player);
    return player;
  }

  /**
   * Puts the tour's participation flag on a player.
   *
   * @param player the player to change
   * @param joined whether the player takes part in the tour
   */
  public static void setJoined(Player player, boolean joined) {
    player
        .getPersistentDataContainer()
        .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, joined);
  }

  /**
   * Reads the tour's participation flag from a player.
   *
   * @param player the player to read
   * @return the stored flag, or null when the player never had one
   */
  public static Boolean joinedFlag(Player player) {
    return player
        .getPersistentDataContainer()
        .get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN);
  }

  /**
   * Builds a command source for a sender, the way Paper hands one to a command method.
   *
   * @param sender the sender that ran the command
   * @return a mocked source whose sender is the given one
   */
  public static CommandSourceStack source(CommandSender sender) {
    return CommandTestUtils.stack(sender);
  }

  /**
   * Builds the event Paper fires when a player leaves, without taking the player offline.
   *
   * @param player the player who leaves
   * @return a quit event for the player
   */
  public static PlayerQuitEvent quitEvent(Player player) {
    return new PlayerQuitEvent(
        player, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
  }

  /**
   * Collects the commands that clicking a message would run, in the order they appear in it.
   *
   * @param message the message a player received
   * @return the commands of every run-command click in the message, empty when there are none
   */
  public static List<String> clickCommands(Component message) {
    return CommandTestUtils.clickCommands(message);
  }

  /**
   * A player that counts how often the server saved it. The failsafe saves a player's data after it
   * opts them out of the tour while they are offline.
   */
  public static final class TourPlayer extends PlayerMock {
    private int saves;

    /**
     * Creates a player with a random id.
     *
     * @param server the mock server the player belongs to
     * @param name the player name
     */
    public TourPlayer(ServerMock server, String name) {
      super(server, name, UUID.randomUUID());
    }

    @Override
    public void saveData() {
      saves++;
    }

    /**
     * Returns how often the player's data was saved.
     *
     * @return the number of {@code saveData()} calls so far
     */
    public int saveCount() {
      return saves;
    }
  }
}
