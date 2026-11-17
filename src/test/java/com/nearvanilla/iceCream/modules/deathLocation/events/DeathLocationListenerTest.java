package com.nearvanilla.iceCream.modules.deathLocation.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class DeathLocationListenerTest {
  private ServerMock server;
  private World world;
  private PlayerMock player;
  private final DeathLocationListener listener = new DeathLocationListener();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    world = server.addSimpleWorld("world");
    player = server.addPlayer("Victim");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private PlayerDeathEvent death(Component deathMessage) {
    return new PlayerDeathEvent(
        player,
        DamageSource.builder(DamageType.GENERIC).build(),
        new ArrayList<ItemStack>(),
        0,
        deathMessage,
        false);
  }

  private String nextMessage() {
    Component message = player.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  @Test
  void sendsTheDeathCoordinatesToTheDyingPlayer() {
    player.teleport(new Location(world, 120.5, 64.0, -30.5));

    listener.onPlayerDeath(death(Component.text("Victim died")));

    assertEquals("You died at <120, 64, -31>", nextMessage());
  }

  @Test
  void roundsCoordinatesDownToTheBlock() {
    player.teleport(new Location(world, -0.4, -12.9, 7.99));

    listener.onPlayerDeath(death(Component.text("Victim died")));

    // Block coordinates floor toward negative infinity, so -0.4 is block -1 and -12.9 is -13.
    assertEquals("You died at <-1, -13, 7>", nextMessage());
  }

  @Test
  void sendsExactlyOneMessage() {
    listener.onPlayerDeath(death(Component.text("Victim died")));

    nextMessage();
    assertNull(nextMessage());
  }

  @Test
  void leavesThePublicDeathMessageAndDropsUntouched() {
    Component deathMessage = Component.text("Victim fell from a high place");
    ItemStack drop = new ItemStack(Material.DIAMOND, 3);
    ArrayList<ItemStack> drops = new ArrayList<>();
    drops.add(drop);
    PlayerDeathEvent event =
        new PlayerDeathEvent(
            player,
            DamageSource.builder(DamageType.GENERIC).build(),
            drops,
            0,
            deathMessage,
            false);

    listener.onPlayerDeath(event);

    assertEquals(deathMessage, event.deathMessage());
    assertEquals(1, event.getDrops().size());
    assertEquals(drop, event.getDrops().getFirst());
  }

  @Test
  void firesForARealDeathEventOnTheServer() {
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    player.teleport(new Location(world, 1, 2, 3));

    server.getPluginManager().callEvent(death(Component.text("Victim died")));

    assertEquals("You died at <1, 2, 3>", nextMessage());
  }
}
