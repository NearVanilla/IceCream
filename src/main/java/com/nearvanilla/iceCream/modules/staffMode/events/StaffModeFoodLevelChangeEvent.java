package com.nearvanilla.iceCream.modules.staffMode.events;

import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.persistence.PersistentDataType;

/** Prevents hunger loss in staff mode while still allowing players to eat. */
public class StaffModeFoodLevelChangeEvent implements Listener {
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onFoodLevelChange(FoodLevelChangeEvent event) {
    if (event.getEntity() instanceof Player player
        && event.getFoodLevel() < player.getFoodLevel()
        && Boolean.TRUE.equals(
            player
                .getPersistentDataContainer()
                .get(StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN))) {
      event.setCancelled(true);
    }
  }
}
