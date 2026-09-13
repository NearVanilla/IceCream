package com.nearvanilla.iceCream.modules.flight;

import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class FlightUtils {
  public static final String PERMISSION = "icecream.modules.flight.fly";

  public static boolean isEnabled(Player player) {
    return player
        .getPersistentDataContainer()
        .getOrDefault(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, false);
  }

  public static boolean hasOtherFlight(Player player) {
    return player.getGameMode() == GameMode.CREATIVE
        || player.getGameMode() == GameMode.SPECTATOR
        || (StaffModeModule.STAFF_MODE_TOGGLE_KEY != null
            && player
                .getPersistentDataContainer()
                .getOrDefault(
                    StaffModeModule.STAFF_MODE_TOGGLE_KEY, PersistentDataType.BOOLEAN, false));
  }

  public static void setEnabled(Player player, boolean enabled) {
    if (enabled) {
      player
          .getPersistentDataContainer()
          .set(FlightModule.FLIGHT_ENABLED_KEY, PersistentDataType.BOOLEAN, true);
      restoreFlight(player);
    } else {
      suspendFlight(player);
      player.getPersistentDataContainer().remove(FlightModule.FLIGHT_ENABLED_KEY);
    }
  }

  /** Restores the saved preference only while the player has permission to fly. */
  public static void restoreFlight(Player player) {
    if (!isEnabled(player) || hasOtherFlight(player)) {
      return;
    }
    if (player.hasPermission(PERMISSION)) {
      player.setAllowFlight(true);
    } else {
      suspendFlight(player);
    }
  }

  /** Removes this module's flight allowance without forgetting the player's preference. */
  public static void suspendFlight(Player player) {
    if (!isEnabled(player) || hasOtherFlight(player)) {
      return;
    }
    player.setFlying(false);
    player.setAllowFlight(false);
  }
}
