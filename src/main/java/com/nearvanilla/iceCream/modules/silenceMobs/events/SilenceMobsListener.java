package com.nearvanilla.iceCream.modules.silenceMobs.events;

import com.nearvanilla.iceCream.modules.silenceMobs.SilenceMobsConfig;
import com.nearvanilla.iceCream.modules.silenceMobs.SilenceMobsModule;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Silences a mob when a player names it with a name tag called one of the trigger names, then
 * renames it to the silenced name.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see SilenceMobsModule
 */
public class SilenceMobsListener implements Listener {
  private final SilenceMobsConfig config;
  private final Consumer<Runnable> nextTick;

  /**
   * Creates the listener.
   *
   * @param config the module settings
   * @param nextTick runs a task on the next server tick
   */
  public SilenceMobsListener(SilenceMobsConfig config, Consumer<Runnable> nextTick) {
    this.config = config;
    this.nextTick = nextTick;
  }

  /**
   * Waits a tick for a trigger name tag used on an entity, then silences the entity if the name tag
   * named it.
   *
   * @param event the interaction
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    Player player = event.getPlayer();
    if (!player.hasPermission(SilenceMobsModule.PERMISSION)) return;
    ItemStack item = player.getInventory().getItem(event.getHand());
    if (item.getType() != Material.NAME_TAG) return;
    if (!config.isTrigger(item.getItemMeta().customName())) return;
    Entity entity = event.getRightClicked();
    // Vanilla names the entity after this event, and only if it takes name tags, so check next
    // tick that the name was applied. This skips entities a name tag cannot name, such as
    // players and boats, and item frames that take the tag as their item.
    nextTick.accept(() -> silenceIfNamed(entity));
  }

  private void silenceIfNamed(Entity entity) {
    if (!entity.isValid() || !config.isTrigger(entity.customName())) return;
    entity.setSilent(true);
    if (config.silencedName() != null) {
      entity.customName(config.silencedName());
    }
  }
}
