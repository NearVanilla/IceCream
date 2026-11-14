package com.nearvanilla.iceCream.modules.trialVaultReset.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.trialVaultReset.LootTimes;
import com.nearvanilla.iceCream.modules.trialVaultReset.TrialVaultResetConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Vault;
import org.bukkit.block.data.type.Vault.State;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TrialVaultResetListenerTest {
  private static final long DAY = 86_400_000;
  private static final long NOW = 10 * DAY;

  private final LootTimes lootTimes = new LootTimes("icecream");
  private final List<Runnable> nextTick = new ArrayList<>();
  private ServerMock server;
  private PlayerMock player;
  private UUID uuid;
  private Block block;
  private Vault vault;
  private org.bukkit.block.data.type.Vault data;
  private PersistentDataContainer pdc;
  private TrialVaultResetListener listener;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    uuid = player.getUniqueId();
    // A second player's container stands in for the vault's real container.
    pdc = server.addPlayer().getPersistentDataContainer();

    block = mock(Block.class);
    vault = mock(Vault.class);
    data = mock(org.bukkit.block.data.type.Vault.class);
    when(block.getType()).thenReturn(Material.VAULT);
    when(block.getState()).thenReturn(vault);
    when(block.getBlockData()).thenReturn(data);
    when(data.getVaultState()).thenReturn(State.ACTIVE);
    when(vault.getKeyItem()).thenReturn(new ItemStack(Material.TRIAL_KEY));
    when(vault.getPersistentDataContainer()).thenReturn(pdc);

    listener =
        new TrialVaultResetListener(
            new TrialVaultResetConfig(DAY, "reset", "wait <time>"),
            lootTimes,
            () -> NOW,
            nextTick::add);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private PlayerInteractEvent click(Action action, ItemStack item) {
    PlayerInteractEvent event = new PlayerInteractEvent(player, action, item, block, BlockFace.UP);
    listener.onInteract(event);
    return event;
  }

  private PlayerInteractEvent clickWithKey() {
    return click(Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.TRIAL_KEY));
  }

  private String nextMessage() {
    Component message = player.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  private void assertNothingHappened(PlayerInteractEvent event) {
    verify(vault, never()).removeRewardedPlayer(any());
    verify(vault, never()).update();
    assertFalse(event.isCancelled());
    assertNull(nextMessage());
  }

  @Test
  void resetsUnrecordedLoot() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);

    PlayerInteractEvent event = clickWithKey();

    verify(vault).removeRewardedPlayer(uuid);
    verify(vault).update();
    assertTrue(event.isCancelled());
    assertEquals("reset", nextMessage());
  }

  @Test
  void resetsAfterDelay() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    lootTimes.put(pdc, uuid, NOW - DAY);

    PlayerInteractEvent event = clickWithKey();

    verify(vault).removeRewardedPlayer(uuid);
    verify(vault).update();
    assertTrue(event.isCancelled());
    assertNull(lootTimes.get(pdc, uuid));
    assertEquals("reset", nextMessage());
  }

  @Test
  void waitsBeforeDelay() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    lootTimes.put(pdc, uuid, NOW - DAY + 11_520_000);

    PlayerInteractEvent event = clickWithKey();

    verify(vault, never()).removeRewardedPlayer(any());
    assertFalse(event.isCancelled());
    assertEquals("wait 3h 12m", nextMessage());
  }

  @Test
  void ignoresPlayerWhoHasNotLooted() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(false);

    assertNothingHappened(clickWithKey());
  }

  @Test
  void ignoresWrongKey() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    when(vault.getKeyItem()).thenReturn(new ItemStack(Material.OMINOUS_TRIAL_KEY));

    assertNothingHappened(clickWithKey());
  }

  @Test
  void ignoresLeftClickAndEmptyHand() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);

    assertNothingHappened(click(Action.LEFT_CLICK_BLOCK, new ItemStack(Material.TRIAL_KEY)));
    assertNothingHappened(click(Action.RIGHT_CLICK_BLOCK, null));
  }

  @Test
  void ignoresEjectingVault() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    when(data.getVaultState()).thenReturn(State.EJECTING);

    assertNothingHappened(clickWithKey());
  }

  @Test
  void ignoresUnlockingVault() {
    // Vanilla marks the player rewarded as the vault starts unlocking, a tick before the loot time
    // is recorded. A second click in that window must not reset the vault.
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    when(data.getVaultState()).thenReturn(State.UNLOCKING);

    assertNothingHappened(clickWithKey());
  }

  @Test
  void resetsInactiveVault() {
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    when(data.getVaultState()).thenReturn(State.INACTIVE);

    assertTrue(clickWithKey().isCancelled());
    verify(vault).removeRewardedPlayer(uuid);
  }

  @Test
  void recordsLootNextTick() {
    UUID stale = UUID.randomUUID();
    lootTimes.put(pdc, stale, 1L);
    when(vault.hasRewardedPlayer(uuid)).thenReturn(true);
    when(vault.getRewardedPlayers()).thenReturn(Set.of(uuid));

    listener.onLoot(new BlockDispenseLootEvent(player, block, List.of(), null));

    assertNull(lootTimes.get(pdc, uuid));
    assertEquals(1, nextTick.size());

    nextTick.getFirst().run();

    assertEquals(NOW, lootTimes.get(pdc, uuid));
    assertNull(lootTimes.get(pdc, stale));
    verify(vault).update();
  }

  @Test
  void doesNotRecordForOtherBlocksOrNoPlayer() {
    listener.onLoot(new BlockDispenseLootEvent(null, block, List.of(), null));
    when(block.getType()).thenReturn(Material.TRIAL_SPAWNER);
    listener.onLoot(new BlockDispenseLootEvent(player, block, List.of(), null));

    assertTrue(nextTick.isEmpty());
  }

  @Test
  void lootHandlerSkipsCancelledEventsAtMonitor() throws NoSuchMethodException {
    EventHandler handler =
        TrialVaultResetListener.class
            .getMethod("onLoot", BlockDispenseLootEvent.class)
            .getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, handler.priority());
    assertTrue(handler.ignoreCancelled());
  }
}
