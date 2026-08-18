package com.nearvanilla.iceCream.modules.phantomToggle.commands;

import com.nearvanilla.iceCream.modules.phantomToggle.PhantomToggleUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * PhantomToggleCommand allows players to toggle phantoms for themselves, and to check whether they
 * currently have phantoms disabled.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-08-18
 */
@SuppressWarnings("unused")
public class PhantomToggleCommand {

  private static final Component PHANTOMS_ENABLED =
      MiniMessage.miniMessage()
          .deserialize(
              "<green>Phantom spawning is now <bold>enabled</bold> for you. Don't forget to"
                  + " sleep!</green>");
  private static final Component PHANTOMS_DISABLED =
      MiniMessage.miniMessage()
          .deserialize(
              "<red>Phantom spawning is now <bold>disabled</bold> for you. No more phantom"
                  + " visits.</red>");
  private static final Component STATUS_ENABLED =
      MiniMessage.miniMessage()
          .deserialize(
              "<green>Phantom spawning is currently <bold>enabled</bold> for you. Remember to"
                  + " sleep!</green>");
  private static final Component STATUS_DISABLED =
      MiniMessage.miniMessage()
          .deserialize(
              "<red>Phantom spawning is currently <bold>disabled</bold> for you. Enjoy the peace"
                  + " and quiet!</red>");
  private static final Component PLAYERS_ONLY =
      MiniMessage.miniMessage().deserialize("<red>This command can only be used by players.</red>");

  /**
   * Toggles phantoms for the player, storing the preference in the player's
   * PersistentDataContainer. Players with phantoms disabled have no phantoms spawned for them, and
   * are not targeted by phantoms that already exist.
   */
  @Command("phantomtoggle")
  @CommandDescription("Toggle phantom spawning for yourself.")
  @Permission("icecream.modules.phantomtoggle.toggle")
  public void togglePhantoms(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    boolean isDisabled = PhantomToggleUtils.isPhantomDisabled(player);
    PhantomToggleUtils.setPhantomDisabled(player, !isDisabled);
    player.sendMessage(isDisabled ? PHANTOMS_ENABLED : PHANTOMS_DISABLED);
  }

  /** Tells the player whether they currently have phantoms disabled. */
  @Command("phantomtoggle status")
  @CommandDescription("Check whether phantom spawning is disabled for you.")
  @Permission("icecream.modules.phantomtoggle.toggle")
  public void phantomStatus(CommandSourceStack commandSourceStack) {
    if (!(commandSourceStack.getSender() instanceof Player player)) {
      commandSourceStack.getSender().sendMessage(PLAYERS_ONLY);
      return;
    }
    player.sendMessage(
        PhantomToggleUtils.isPhantomDisabled(player) ? STATUS_DISABLED : STATUS_ENABLED);
  }
}
