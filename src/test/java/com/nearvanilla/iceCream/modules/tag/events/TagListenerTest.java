package com.nearvanilla.iceCream.modules.tag.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.tag.TagConfig;
import com.nearvanilla.iceCream.modules.tag.TagGame;
import com.nearvanilla.iceCream.modules.tag.TagItem;
import com.nearvanilla.iceCream.modules.tag.TagStore;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TagListenerTest {
  private final TagStore store = new TagStore("icecream");
  private final TagItem item = new TagItem("icecream");
  private ServerMock server;
  private PlayerMock alex;
  private PlayerMock steve;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    alex = server.addPlayer("Alex");
    steve = server.addPlayer("Steve");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private TagGame game(boolean preventTagDestruction) {
    return new TagGame(
        new TagConfig(false, 0, preventTagDestruction),
        store,
        item,
        server.getScoreboardManager().getMainScoreboard(),
        mock(Audience.class),
        System::currentTimeMillis);
  }

  private static EntityDamageByEntityEvent hit(Entity damager, Entity target, DamageCause cause) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getDamager()).thenReturn(damager);
    when(event.getEntity()).thenReturn(target);
    when(event.getCause()).thenReturn(cause);
    return event;
  }

  /** Makes Alex "it", holding the tag item. */
  private TagListener alexIsIt(boolean preventTagDestruction) {
    TagGame game = game(preventTagDestruction);
    game.give(alex, alex);
    alex.getInventory().setHeldItemSlot(0);
    assertTrue(item.isTagItem(alex.getInventory().getItemInMainHand()));
    return new TagListener(game);
  }

  @Test
  void hittingAPlayerWithTheTagItemTagsThem() {
    TagListener listener = alexIsIt(true);

    listener.onPlayerDamage(hit(alex, steve, DamageCause.ENTITY_ATTACK));

    assertTrue(store.isIt(steve));
    assertFalse(store.isIt(alex));
  }

  @Test
  void hittingWithoutTheTagItemDoesNotTag() {
    TagListener listener = alexIsIt(true);
    alex.getInventory().setItem(1, new ItemStack(Material.STICK));
    alex.getInventory().setHeldItemSlot(1);

    listener.onPlayerDamage(hit(alex, steve, DamageCause.ENTITY_ATTACK));

    assertFalse(store.isIt(steve));
    assertTrue(store.isIt(alex));
  }

  @Test
  void thornsDamageDoesNotTag() {
    TagListener listener = alexIsIt(true);

    listener.onPlayerDamage(hit(alex, steve, DamageCause.THORNS));

    assertFalse(store.isIt(steve));
  }

  @Test
  void hittingAMobDoesNothing() {
    TagListener listener = alexIsIt(true);

    listener.onPlayerDamage(hit(alex, mock(Zombie.class), DamageCause.ENTITY_ATTACK));

    assertTrue(store.isIt(alex));
  }

  @Test
  void hitsFromMobsDoNothing() {
    TagListener listener = alexIsIt(true);

    listener.onPlayerDamage(hit(mock(Zombie.class), steve, DamageCause.ENTITY_ATTACK));

    assertFalse(store.isIt(steve));
  }

  @Test
  void theTagItemCannotRenameMobs() {
    TagListener listener = alexIsIt(true);
    PlayerInteractEntityEvent event =
        new PlayerInteractEntityEvent(alex, mock(Zombie.class), EquipmentSlot.HAND);

    listener.onPlayerInteractEntity(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void theTagItemCanRenameMobsWhenProtectionIsOff() {
    TagListener listener = alexIsIt(false);
    PlayerInteractEntityEvent event =
        new PlayerInteractEntityEvent(alex, mock(Zombie.class), EquipmentSlot.HAND);

    listener.onPlayerInteractEntity(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void otherNameTagsCanRenameMobs() {
    TagListener listener = new TagListener(game(true));
    alex.getInventory().setItemInMainHand(new ItemStack(Material.NAME_TAG));
    PlayerInteractEntityEvent event =
        new PlayerInteractEntityEvent(alex, mock(Zombie.class), EquipmentSlot.HAND);

    listener.onPlayerInteractEntity(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void theTagItemCannotBeDropped() {
    TagListener listener = alexIsIt(true);

    PlayerDropItemEvent event = drop(item.create());
    listener.onPlayerDropItem(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void theTagItemCanBeDroppedWhenProtectionIsOff() {
    TagListener listener = alexIsIt(false);

    PlayerDropItemEvent event = drop(item.create());
    listener.onPlayerDropItem(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void otherItemsCanBeDropped() {
    TagListener listener = alexIsIt(true);

    PlayerDropItemEvent event = drop(new ItemStack(Material.NAME_TAG));
    listener.onPlayerDropItem(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void joiningRestoresTheTagState() {
    TagGame game = game(true);
    game.give(alex, steve);
    game.reset(alex);
    // Steve is "it" again in his data, but was reset while offline.
    store.setIt(steve, 0);

    new TagListener(game).onPlayerJoin(new PlayerJoinEvent(steve, Component.empty()));

    assertFalse(store.isIt(steve));
  }

  private PlayerDropItemEvent drop(ItemStack stack) {
    Item dropped = mock(Item.class);
    when(dropped.getItemStack()).thenReturn(stack);
    return new PlayerDropItemEvent(alex, dropped);
  }
}
