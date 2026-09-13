package com.nearvanilla.iceCream.modules.locatorBar.commands;

import com.nearvanilla.iceCream.modules.locatorBar.LocatorBarModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * Handles the player command for toggling locator bar waypoint reception.
 *
 * <p>The command stores the player's current base receive range before applying the module's
 * configured disabled range. Running the command again restores that stored value.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-09-13
 * @see LocatorBarModule
 */
@SuppressWarnings("unused")
public class LocatorBarCommand {

  private static final Component LOCATOR_BAR_ENABLED =
      MiniMessage.miniMessage().deserialize("<green>The locator bar is now enabled.</green>");
  private static final Component LOCATOR_BAR_DISABLED =
      MiniMessage.miniMessage().deserialize("<red>The locator bar is now disabled.</red>");
  private static final Component PLAYERS_ONLY =
      MiniMessage.miniMessage().deserialize("<red>This command can only be used by players.</red>");
  private static final Component ATTRIBUTE_UNAVAILABLE =
      MiniMessage.miniMessage()
          .deserialize("<red>Your locator bar setting could not be changed.</red>");

  /** Creates a locator bar toggle command. */
  public LocatorBarCommand() {}

  /**
   * Toggles whether the executing player receives locator bar waypoints.
   *
   * @param commandSourceStack command context containing the executing sender
   */
  @Command("togglelocatorbar|locatorbartoggle")
  @CommandDescription("Toggle the locator bar for yourself.")
  @Permission("icecream.modules.locatorbar.toggle")
  public void toggleLocatorBar(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }

    AttributeInstance receiveRange = player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE);
    if (receiveRange == null) {
      player.sendMessage(ATTRIBUTE_UNAVAILABLE);
      return;
    }

    PersistentDataContainer data = player.getPersistentDataContainer();
    Double previousRange =
        data.get(LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY, PersistentDataType.DOUBLE);
    if (previousRange != null) {
      receiveRange.setBaseValue(previousRange);
      data.remove(LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY);
      player.sendMessage(LOCATOR_BAR_ENABLED);
      return;
    }

    data.set(
        LocatorBarModule.PREVIOUS_RECEIVE_RANGE_KEY,
        PersistentDataType.DOUBLE,
        receiveRange.getBaseValue());
    receiveRange.setBaseValue(LocatorBarModule.getDisabledReceiveRange());
    player.sendMessage(LOCATOR_BAR_DISABLED);
  }
}
