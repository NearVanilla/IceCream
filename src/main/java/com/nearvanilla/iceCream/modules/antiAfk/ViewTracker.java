package com.nearvanilla.iceCream.modules.antiAfk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Watches where each player is looking and reports when they turn their camera far enough to count
 * as activity. Main thread only.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 * @see RotationGate
 */
public final class ViewTracker {
  private record View(float yaw, float pitch) {}

  private final Map<UUID, View> anchors = new HashMap<>();
  private final double minDegrees;

  public ViewTracker(double minDegrees) {
    this.minDegrees = minDegrees;
  }

  /**
   * Checks whether the player has turned far enough since their camera last counted as activity.
   * Small turns never move the anchor, so jiggles don't add up.
   *
   * @return true when the turn counts as activity
   */
  public boolean turned(Player player) {
    // Vehicles turn their riders (boats, steered mounts) or not (minecarts on bends), so camera
    // turns can't be told apart from the ride. Steering still counts through PlayerInputEvent.
    if (player.getVehicle() != null) {
      anchors.remove(player.getUniqueId());
      return false;
    }
    Location location = player.getLocation();
    View view = new View(location.getYaw(), location.getPitch());
    View anchor = anchors.putIfAbsent(player.getUniqueId(), view);
    if (anchor == null
        || !RotationGate.exceeded(
            anchor.yaw(), anchor.pitch(), view.yaw(), view.pitch(), minDegrees)) {
      return false;
    }
    anchors.put(player.getUniqueId(), view);
    return true;
  }

  /**
   * Forgets where the player was looking, for when the server rather than the player turned them.
   * The next check takes the new view as its starting point without counting it as activity.
   */
  public void reset(UUID playerId) {
    anchors.remove(playerId);
  }

  public void clear() {
    anchors.clear();
  }
}
