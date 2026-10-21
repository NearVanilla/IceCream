package com.nearvanilla.iceCream.modules.mapTools;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Runs every half second: redraws displays for players who moved, shows the mode on the action bar,
 * and ends edit mode for players who lost the permission.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-10-18
 */
public class AreaBorderTask implements Runnable {

  private static final String STATUS_TEMPLATE = "<yellow><mode></yellow>";

  private static String radiusOf(EditSession session) {
    return session.getRadius() == null ? "" : ", radius " + session.getRadius();
  }

  @Override
  public void run() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.hasPermission(MapToolsUtils.PERMISSION)) {
        if (MapToolsUtils.isEditing(player)) MapToolsUtils.stopEditing(player);
        continue;
      }
      AreaDisplays.update(player);
      EditSession session = MapToolsModule.sessions.get(player.getUniqueId());
      if (session == null) continue;

      String mode;
      if (session.isViewing()) {
        mode = "Edit mode";
      } else if (session.isNew()) {
        mode = session.getMode().label() + " mode" + radiusOf(session);
      } else {
        mode =
            "Editing "
                + session.getAreaId()
                + radiusOf(session)
                + (session.hasChanges() ? " (unsaved changes, /maptools save)" : "");
      }
      player.sendActionBar(
          MiniMessage.miniMessage()
              .deserialize(STATUS_TEMPLATE, Placeholder.unparsed("mode", mode)));
    }
  }
}
