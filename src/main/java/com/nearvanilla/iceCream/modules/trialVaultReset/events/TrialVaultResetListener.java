package com.nearvanilla.iceCream.modules.trialVaultReset.events;

import com.nearvanilla.iceCream.modules.trialVaultReset.DurationFormat;
import com.nearvanilla.iceCream.modules.trialVaultReset.LootTimes;
import com.nearvanilla.iceCream.modules.trialVaultReset.ResetDecision;
import com.nearvanilla.iceCream.modules.trialVaultReset.TrialVaultResetConfig;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Records when players loot trial vaults, and resets a vault for a player who uses its key once the
 * configured delay has passed.
 *
 * <p>Vanilla refuses keys while a vault is inactive, which it is when every nearby player has
 * already looted it. The reset click is therefore cancelled, so the key is not used, and the vault
 * reactivates on its own shortly after. The player then uses the key again as normal.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-14
 */
public class TrialVaultResetListener implements Listener {
  private final TrialVaultResetConfig config;
  private final LootTimes lootTimes;
  private final LongSupplier clock;
  private final Consumer<Runnable> nextTick;

  /**
   * Creates the listener.
   *
   * @param config the module settings
   * @param lootTimes where loot times are stored
   * @param clock the current time in milliseconds
   * @param nextTick runs a task on the next server tick
   */
  public TrialVaultResetListener(
      TrialVaultResetConfig config,
      LootTimes lootTimes,
      LongSupplier clock,
      Consumer<Runnable> nextTick) {
    this.config = config;
    this.lootTimes = lootTimes;
    this.clock = clock;
    this.nextTick = nextTick;
  }

  /**
   * Records the loot time when a vault ejects loot for a player. The write waits a tick, so the
   * block state snapshot is taken after vanilla has added the player to the rewarded players and
   * cannot overwrite that change.
   *
   * @param event the loot event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLoot(BlockDispenseLootEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    if (player == null || block.getType() != Material.VAULT) return;
    UUID uuid = player.getUniqueId();
    nextTick.accept(() -> record(block, uuid));
  }

  private void record(Block block, UUID uuid) {
    if (!(block.getState() instanceof Vault vault) || !vault.hasRewardedPlayer(uuid)) return;
    lootTimes.retainOnly(vault.getPersistentDataContainer(), vault.getRewardedPlayers());
    lootTimes.put(vault.getPersistentDataContainer(), uuid, clock.getAsLong());
    vault.update();
  }

  /**
   * Resets a vault for a player who uses its key after the delay, or tells them how long is left.
   *
   * @param event the interact event
   */
  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Block block = event.getClickedBlock();
    ItemStack item = event.getItem();
    if (block == null || item == null || block.getType() != Material.VAULT) return;
    if (!(block.getState() instanceof Vault vault)) return;
    // Only idle vaults. Vanilla marks the player rewarded as the vault starts unlocking, a tick
    // before onLoot records the time, so resetting while unlocking or ejecting would give the
    // player a second loot.
    if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Vault data)
        || (data.getVaultState() != org.bukkit.block.data.type.Vault.State.ACTIVE
            && data.getVaultState() != org.bukkit.block.data.type.Vault.State.INACTIVE)) return;
    ItemStack key = vault.getKeyItem();
    if (key.isEmpty() || item.getType() != key.getType()) return;

    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    ResetDecision decision =
        ResetDecision.decide(
            vault.hasRewardedPlayer(uuid),
            lootTimes.get(vault.getPersistentDataContainer(), uuid),
            clock.getAsLong(),
            config.delayMs());

    switch (decision.outcome()) {
      case RESET -> {
        vault.removeRewardedPlayer(uuid);
        lootTimes.remove(vault.getPersistentDataContainer(), uuid);
        vault.update();
        event.setCancelled(true);
        player.sendMessage(MiniMessage.miniMessage().deserialize(config.resetMessage()));
      }
      case WAIT ->
          player.sendMessage(
              MiniMessage.miniMessage()
                  .deserialize(
                      config.waitingMessage(),
                      Placeholder.unparsed("time", DurationFormat.format(decision.remainingMs()))));
      case IGNORE -> {}
    }
  }
}
