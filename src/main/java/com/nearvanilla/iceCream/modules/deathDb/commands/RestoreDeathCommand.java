package com.nearvanilla.iceCream.modules.deathDb.commands;

import com.nearvanilla.iceCream.modules.deathDb.DeathDbModule;
import com.nearvanilla.iceCream.modules.deathDb.gui.DeathListGui;
import com.nearvanilla.iceCream.modules.deathDb.storage.DeathRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/** Opens the death browser for a player so staff can preview and restore an inventory. */
public class RestoreDeathCommand {
  private final DeathRepository repository;

  public RestoreDeathCommand(DeathRepository repository) {
    this.repository = repository;
  }

  /**
   * Opens the paginated death list for the given player.
   *
   * @param commandSourceStack the command source, must be a player
   * @param target the player whose deaths to browse; may be offline
   */
  @Command("restoredeath|rd <player>")
  @CommandDescription("Browse a player's recorded deaths and restore an inventory.")
  @Permission(DeathDbModule.RESTORE_PERMISSION)
  @SuppressWarnings("unused")
  public void restoreDeath(
      CommandSourceStack commandSourceStack,
      @Argument(value = "player", description = "The player whose deaths to browse")
          OfflinePlayer target) {
    if (!(commandSourceStack.getSender() instanceof Player staff)) {
      commandSourceStack.getSender().sendMessage("This command can only be used by players.");
      return;
    }
    String name = target.getName();
    if (name == null || !(target.isOnline() || target.hasPlayedBefore())) {
      staff.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red><name> has never played on this server.</red>",
                  Placeholder.unparsed("name", name == null ? "That player" : name)));
      return;
    }
    DeathListGui.open(staff, repository, target.getUniqueId(), name, 0);
  }
}
