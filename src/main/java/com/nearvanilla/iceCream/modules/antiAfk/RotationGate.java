package com.nearvanilla.iceCream.modules.antiAfk;

/**
 * Decides whether a player has turned their camera far enough to count as activity. The distance is
 * measured from an anchor that only moves when activity is counted, so small back and forth jiggles
 * (such as a mouse jiggler) never add up.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public final class RotationGate {
  private RotationGate() {}

  /**
   * Checks whether the view has turned at least {@code minDegrees} away from the anchor.
   *
   * @return true when the angular distance is at least {@code minDegrees}
   */
  public static boolean exceeded(
      float anchorYaw, float anchorPitch, float yaw, float pitch, double minDegrees) {
    double yawDelta = Math.abs(yaw - anchorYaw) % 360.0;
    if (yawDelta > 180.0) yawDelta = 360.0 - yawDelta;
    double pitchDelta = pitch - anchorPitch;
    return Math.hypot(yawDelta, pitchDelta) >= minDegrees;
  }
}
