package com.nearvanilla.iceCream.modules.lightning.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class StrikeCommandTest {
  /** Records lightning strikes, because MockBukkit does not implement strikeLightning. */
  private static final class RecordingWorld extends WorldMock {
    private final List<Location> strikes = new ArrayList<>();

    RecordingWorld() {
      super(Material.GRASS_BLOCK, 4);
    }

    @Override
    public LightningStrike strikeLightning(Location location) {
      strikes.add(location.clone());
      return mock(LightningStrike.class);
    }
  }

  private static final String ALWAYS_STRIKES = "modules:\n  lightning:\n    chance: 1.0\n";
  private static final String NEVER_STRIKES = "modules:\n  lightning:\n    chance: 0.0\n";

  private ServerMock server;
  private RecordingWorld world;
  private PlayerMock sender;
  private PlayerMock victim;
  private final StrikeCommand command = new StrikeCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, ALWAYS_STRIKES);
    world = new RecordingWorld();
    server.addWorld(world);
    sender = server.addPlayer("Sender");
    victim = server.addPlayer("Victim");
    sender.teleport(new Location(world, 0, 64, 0));
    victim.teleport(new Location(world, 10, 64, 10));
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void strike(CommandSender from, Player target) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(from);
    command.strikeCommand(source, target);
  }

  private String nextMessage(PlayerMock target) {
    Component message = target.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  private int lightningCount() {
    return world.strikes.size();
  }

  @Test
  void strikesTheTargetWhenTheChanceAlwaysSucceeds() {
    strike(sender, victim);

    assertEquals(1, lightningCount());
    assertEquals(victim.getLocation(), world.strikes.getFirst());
  }

  @Test
  void tellsBothPlayersAboutASuccessfulStrike() {
    strike(sender, victim);

    assertEquals("You have been struck by lightning!", nextMessage(victim));
    assertNull(nextMessage(victim));
    assertEquals("Success!", nextMessage(sender));
    assertNull(nextMessage(sender));
  }

  @Test
  void doesNotStrikeWhenTheChanceNeverSucceeds() {
    IceCreamFixture.install(server, NEVER_STRIKES);

    strike(sender, victim);

    assertEquals(0, lightningCount());
  }

  @Test
  void tellsOnlyTheSenderWhenFateDecidesAgainstTheStrike() {
    IceCreamFixture.install(server, NEVER_STRIKES);

    strike(sender, victim);

    assertEquals("Fate has decided that you cannot strike this player.", nextMessage(sender));
    assertNull(nextMessage(sender));
    assertNull(nextMessage(victim));
  }

  @Test
  void rejectsStrikingYourself() {
    strike(sender, sender);

    assertEquals("You cannot strike yourself!", nextMessage(sender));
    assertNull(nextMessage(sender));
    assertEquals(0, lightningCount());
  }

  @Test
  void rejectsAnOfflineTarget() {
    victim.disconnect();

    strike(sender, victim);

    assertEquals("This player is not online.", nextMessage(sender));
    assertNull(nextMessage(sender));
    assertEquals(0, lightningCount());
  }

  @Test
  void rejectsAMissingTarget() {
    strike(sender, null);

    assertEquals("No player found with that name.", nextMessage(sender));
    assertNull(nextMessage(sender));
    assertEquals(0, lightningCount());
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    strike(console, victim);

    Component message = console.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "You must be a player to use this command.",
        PlainTextComponentSerializer.plainText().serialize(message));
    assertNull(console.nextComponentMessage());
    assertNull(nextMessage(victim));
    assertEquals(0, lightningCount());
  }

  @Test
  void aNegativeChanceNeverStrikes() {
    IceCreamFixture.install(server, "modules:\n  lightning:\n    chance: -1.0\n");

    strike(sender, victim);

    assertEquals(0, lightningCount());
    assertEquals("Fate has decided that you cannot strike this player.", nextMessage(sender));
  }

  @Test
  void aChanceAboveOneAlwaysStrikes() {
    IceCreamFixture.install(server, "modules:\n  lightning:\n    chance: 2.5\n");

    strike(sender, victim);

    assertEquals(1, lightningCount());
  }
}
