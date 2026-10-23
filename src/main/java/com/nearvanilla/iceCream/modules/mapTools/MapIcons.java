package com.nearvanilla.iceCream.modules.mapTools;

import com.nearvanilla.iceCream.IceCream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.marker.Marker;

/**
 * The icons markers can use: PNG files in {@code plugins/IceCream/map-icons}, named after their
 * file name. They are handed to squaremap on startup, which serves them to the web map. The folder
 * starts with a few icons that ship with the plugin.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-22
 */
final class MapIcons {

  private record Icon(Key key, int width, int height) {}

  private static final Map<String, Icon> icons = new LinkedHashMap<>();

  /** The icons bundled in the jar, copied to the folder when it is first created. */
  private static final List<String> DEFAULTS =
      List.of("pin", "home", "shop", "flag", "star", "portal", "tree");

  private MapIcons() {
    // Utility class; prevent instantiation.
  }

  /** Registers every PNG in the icon folder with squaremap, creating the folder if needed. */
  static void load(Squaremap squaremap, File folder) {
    if (!folder.exists()) {
      DEFAULTS.forEach(name -> IceCream.instance.saveResource("map-icons/" + name + ".png", false));
    }
    File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
    if (files == null) return;

    Arrays.sort(files);
    for (File file : files) {
      String name =
          file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
      if (!name.matches("[a-z0-9_-]+")) {
        IceCream.logger.warning(
            "Skipping map icon " + file.getName() + ": use only a-z, 0-9, _ and - in the name.");
        continue;
      }
      try {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("not a readable image");
        Key key = Key.of("icecream_icon_" + name);
        if (squaremap.iconRegistry().hasEntry(key)) squaremap.iconRegistry().unregister(key);
        squaremap.iconRegistry().register(key, image);
        icons.put(name, new Icon(key, image.getWidth(), image.getHeight()));
      } catch (IOException e) {
        IceCream.logger.warning("Skipping map icon " + file.getName() + ": " + e.getMessage());
      }
    }
  }

  /** Removes the icons from squaremap. */
  static void unload(Squaremap squaremap) {
    icons.values().forEach(icon -> squaremap.iconRegistry().unregister(icon.key()));
    icons.clear();
  }

  /** The names of the available icons, in file name order. */
  static List<String> names() {
    return List.copyOf(icons.keySet());
  }

  static boolean exists(String name) {
    return icons.containsKey(name);
  }

  /**
   * @return a marker showing the icon at its own size, or null if there is no icon with that name
   */
  static Marker marker(String name, Point point) {
    Icon icon = name == null ? null : icons.get(name);
    if (icon == null) return null;
    return Marker.icon(point, icon.key(), icon.width(), icon.height());
  }
}
