package com.nearvanilla.iceCream.modules.phantomToggle;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class PhantomToggleUtils {

  public static boolean isPhantomDisabled(Player player) {
    return player
        .getPersistentDataContainer()
        .getOrDefault(PhantomToggleModule.PHANTOM_DISABLED_KEY, PersistentDataType.BOOLEAN, false);
  }

  public static void setPhantomDisabled(Player player, boolean disabled) {
    player
        .getPersistentDataContainer()
        .set(PhantomToggleModule.PHANTOM_DISABLED_KEY, PersistentDataType.BOOLEAN, disabled);
  }
}
