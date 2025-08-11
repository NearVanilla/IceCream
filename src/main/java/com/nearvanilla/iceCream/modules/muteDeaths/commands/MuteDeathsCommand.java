package com.nearvanilla.iceCream.modules.muteDeaths.commands;

import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * MuteDeathsCommand is a command that allows players to mute their own death messages.
 *
 * @author 105hua
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-11
 */
public class MuteDeathsCommand {

  protected final Component deathsMutedComponent =
      MiniMessage.miniMessage()
          .deserialize("<dark_green>✔ <green>Your death messages are now muted.</green>");

  protected final Component deathsUnmutedComponent =
      MiniMessage.miniMessage()
          .deserialize("<red>✖ <green>Your death messages are now visible to others.</green>");

  protected final Component muteDeathsFailComponent =
      MiniMessage.miniMessage()
          .deserialize(
              "<red>An error occurred while trying to mute death messages. Please contact the server manager.</red>");

  /**
   * The logic for the mute deaths command. It sets the PersistentDataContainer MUTE_KEY for the
   * player to indicate that their death messages are muted. If the player already has their death
   * messages muted, it will unmute them instead.
   *
   * @param commandSourceStack The command source stack, containing a sender, executor and location,
   *     where applicable.
   */
  @Command("mutedeaths|mutedeathmessages|mutedeathmsgs")
  @CommandDescription("Mute death messages.")
  @Permission("icecream.modules.mutedeaths.mute")
  @SuppressWarnings("unused")
  public void muteDeathMessages(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }
    PersistentDataContainer playerContainer = player.getPersistentDataContainer();
    if (playerContainer.has(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN)) {
      try {
        @SuppressWarnings(
            "DataFlowIssue") // This is safe because we check for the key's existence above and
        // catch NullPointerException
        boolean currentValue =
            playerContainer.get(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN);
        boolean newValue = !currentValue;
        playerContainer.set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, newValue);
        player.sendMessage(newValue ? deathsMutedComponent : deathsUnmutedComponent);
      } catch (NullPointerException exc) {
        player.sendMessage(muteDeathsFailComponent);
      }
    } else {
      playerContainer.set(MuteDeathsModule.MUTE_KEY, PersistentDataType.BOOLEAN, true);
      player.sendMessage(deathsMutedComponent);
    }
  }
}
