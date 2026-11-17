package com.nearvanilla.iceCream.modules.playerHeadDrops.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlayerDeathHeadDropEventTest {
  private static final String PERMISSION = "icecream.modules.playerheaddrops";

  private ServerMock server;
  private PlayerMock victim;
  private final PlayerDeathHeadDropEvent listener = new PlayerDeathHeadDropEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    victim = server.addPlayer("Victim");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String dropChance, String requirePlayerKill) {
    StringBuilder yaml = new StringBuilder("modules:\n  playerheaddrops:\n");
    if (dropChance != null) {
      yaml.append("    drop-chance: ").append(dropChance).append('\n');
    }
    if (requirePlayerKill != null) {
      yaml.append("    require-player-kill: ").append(requirePlayerKill).append('\n');
    }
    IceCreamFixture.install(server, yaml.toString());
  }

  private PlayerMock killer(boolean allowed) {
    PlayerMock killer = server.addPlayer("Killer");
    if (allowed) {
      killer.addAttachment(IceCream.instance, PERMISSION, true);
    }
    victim.setKiller(killer);
    return killer;
  }

  private PlayerDeathEvent deathEvent(ItemStack... existingDrops) {
    List<ItemStack> drops = new ArrayList<>(List.of(existingDrops));
    return new PlayerDeathEvent(
        victim,
        DamageSource.builder(DamageType.GENERIC).build(),
        drops,
        0,
        Component.text("Victim died"),
        false);
  }

  private PlayerDeathEvent die(ItemStack... existingDrops) {
    PlayerDeathEvent event = deathEvent(existingDrops);
    listener.onPlayerDeath(event);
    return event;
  }

  private List<ItemStack> heads(PlayerDeathEvent event) {
    return event.getDrops().stream()
        .filter(item -> item.getType() == Material.PLAYER_HEAD)
        .toList();
  }

  @Test
  void dropsTheVictimsHeadWhenAPermittedPlayerKillsThem() {
    config("1.0", "true");
    killer(true);

    PlayerDeathEvent event = die();

    assertEquals(1, heads(event).size());
    ItemStack head = heads(event).getFirst();
    assertEquals(1, head.getAmount());
    SkullMeta meta = (SkullMeta) head.getItemMeta();
    assertNotNull(meta.getPlayerProfile());
    assertEquals(victim.getUniqueId(), meta.getPlayerProfile().getId());
    assertEquals("Victim", meta.getPlayerProfile().getName());
  }

  @Test
  void headIsLoreTaggedWithTheKillersName() {
    config("1.0", "true");
    killer(true);

    ItemStack head = heads(die()).getFirst();

    List<Component> lore = head.getItemMeta().lore();
    assertNotNull(lore);
    assertEquals(1, lore.size());
    assertEquals("Killed by Killer", plainText(lore.getFirst()));
  }

  @Test
  void dropsTheHeadForARealDeathEventOnTheServer() {
    config("1.0", "true");
    killer(true);
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    PlayerDeathEvent event = deathEvent();

    server.getPluginManager().callEvent(event);

    assertEquals(1, heads(event).size());
  }

  @Test
  void dropsNoHeadWhenAnotherPluginAlreadyCancelledTheDeath() {
    config("1.0", "true");
    killer(true);
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    PlayerDeathEvent event = deathEvent();
    event.setCancelled(true);

    server.getPluginManager().callEvent(event);

    assertTrue(event.getDrops().isEmpty());
  }

  @Test
  void keepsTheDropsThatWereAlreadyThere() {
    config("1.0", "true");
    killer(true);
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

    PlayerDeathEvent event = die(sword);

    assertEquals(2, event.getDrops().size());
    assertEquals(sword, event.getDrops().getFirst());
    assertEquals(Material.PLAYER_HEAD, event.getDrops().get(1).getType());
  }

  @Test
  void dropsNothingWithoutAKillerWhenAPlayerKillIsRequired() {
    config("1.0", "true");

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void aPlayerKillIsRequiredByDefault() {
    IceCreamFixture.install(server, "");

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void dropsAHeadWithoutAKillerWhenAPlayerKillIsNotRequired() {
    config("1.0", "false");

    PlayerDeathEvent event = die();

    assertEquals(1, heads(event).size());
    assertNull(heads(event).getFirst().getItemMeta().lore());
  }

  @Test
  void dropsNothingWhenTheKillerLacksThePermission() {
    config("1.0", "true");
    killer(false);

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void theKillerPermissionStillAppliesWhenAPlayerKillIsNotRequired() {
    config("1.0", "false");
    killer(false);

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void dropsNothingWhenTheChanceIsZero() {
    config("0.0", "false");

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void dropsNothingWhenTheChanceIsNegative() {
    config("-0.5", "false");

    assertTrue(die().getDrops().isEmpty());
  }

  @Test
  void alwaysDropsWhenTheChanceIsAboveOne() {
    config("3.0", "false");

    assertEquals(1, heads(die()).size());
  }

  @Test
  void dropChanceDefaultsToCertain() {
    config(null, "false");

    assertEquals(1, heads(die()).size());
  }

  @Test
  void fractionalChanceDropsSomeHeadsButNotAll() {
    config("0.5", "false");

    int dropped = 0;
    int trials = 200;
    for (int i = 0; i < trials; i++) {
      if (!heads(die()).isEmpty()) {
        dropped++;
      }
    }

    // With a fair coin the odds of all 200 trials landing on one side are about 2^-199.
    assertTrue(dropped > 0, "no head was dropped");
    assertTrue(dropped < trials, "every death dropped a head");
  }

  @Test
  void aRealDeathEventOnTheServerDropsAHead() {
    config("1.0", "false");
    server.getPluginManager().registerEvents(listener, IceCream.instance);
    PlayerDeathEvent event =
        new PlayerDeathEvent(
            victim,
            DamageSource.builder(DamageType.GENERIC).build(),
            new ArrayList<>(),
            0,
            Component.text("Victim died"),
            false);

    server.getPluginManager().callEvent(event);

    assertEquals(1, heads(event).size());
  }
}
