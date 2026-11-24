package com.nearvanilla.iceCream.modules.coordinatesHud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/*
 * Adapted from PaperTweaks by Machine_Maker (GPL-3.0).
 * Source: https://github.com/MC-Machinations/PaperTweaks
 * Original files: me.machinemaker.papertweaks.modules.survival.coordinateshud.CoordinatesHUD and
 *   HUDRunnable
 * Adaptations: the line is a configurable MiniMessage template instead of fixed components.
 */
/**
 * Builds the action bar line of the Coordinates HUD: the player's block coordinates, the compass
 * direction they face and the time of day in their world.
 *
 * <p>The template is MiniMessage with the placeholders {@code <x>}, {@code <y>}, {@code <z>},
 * {@code <direction>} and {@code <time>}. The direction is padded to two characters, so the time
 * after it does not shift when the direction changes between one and two letters.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class HudLine {
  /** The line PaperTweaks showed, kept as the default. */
  public static final String DEFAULT_TEMPLATE =
      "<gold>XYZ: <white><x> <y> <z></white>  <direction>      <time></gold>";

  private static final MiniMessage MM = MiniMessage.miniMessage();

  private final String template;

  /**
   * Creates the line.
   *
   * @param template the MiniMessage template of the line
   */
  public HudLine(String template) {
    this.template = template;
  }

  /**
   * Renders the line.
   *
   * @param x the block X coordinate
   * @param y the block Y coordinate
   * @param z the block Z coordinate
   * @param yaw the yaw the player faces
   * @param worldTime the time of the player's world, in ticks
   * @return the rendered line
   */
  public Component render(int x, int y, int z, float yaw, long worldTime) {
    return MM.deserialize(
        template,
        Placeholder.unparsed("x", Integer.toString(x)),
        Placeholder.unparsed("y", Integer.toString(y)),
        Placeholder.unparsed("z", Integer.toString(z)),
        Placeholder.unparsed("direction", String.format("%2s", direction(yaw))),
        Placeholder.unparsed("time", clock(worldTime)));
  }

  /**
   * Gives the compass direction for a yaw, one of eight, each covering 45 degrees.
   *
   * @param yaw the yaw, where 0 faces south and 90 faces west
   * @return the direction's abbreviation, such as {@code N} or {@code SW}
   */
  public static String direction(float yaw) {
    double degrees = yaw < 0 ? (yaw % -360.0) + 360 : yaw % 360.0;
    if (degrees <= 22.5) return "S";
    if (degrees <= 67.5) return "SW";
    if (degrees <= 112.5) return "W";
    if (degrees <= 157.5) return "NW";
    if (degrees <= 202.5) return "N";
    if (degrees <= 247.5) return "NE";
    if (degrees <= 292.5) return "E";
    if (degrees <= 337.5) return "SE";
    return "S";
  }

  /**
   * Gives the time of day on a 24 hour clock, where tick 0 is 06:00 as in vanilla.
   *
   * @param worldTime the time of the world, in ticks
   * @return the time as {@code HH:mm}
   */
  public static String clock(long worldTime) {
    long time = Math.floorMod(worldTime + 6000, 24000L);
    long hours = time / 1000;
    long minutes = (time % 1000) * 60 / 1000;
    return String.format("%02d:%02d", hours, minutes);
  }
}
