package com.nearvanilla.iceCream.modules.commandTracker.events;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.commandTracker.CommandNames;
import com.nearvanilla.iceCream.modules.commandTracker.CommandRules;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandLogRepository;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandPosition;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandSource;
import com.nearvanilla.iceCream.modules.commandTracker.storage.CommandUse;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Records the commands players run, and optionally the console's. Runs at MONITOR so it sees the
 * final command line other plugins left and whether one of them cancelled it. The command is
 * captured on the server thread and stored on the repository thread.
 */
public class CommandTrackerListener implements Listener {
  private final CommandLogRepository repository;
  private final CommandRules rules;
  private final boolean recordConsole;
  private final Function<String, Command> commandLookup;
  private final LongSupplier clock;

  /**
   * Creates the listener.
   *
   * @param repository where commands are stored
   * @param rules which commands are recorded and redacted
   * @param recordConsole whether console and RCON commands are recorded
   * @param commandLookup finds a registered command by label, returning null when there is none
   * @param clock the current time in epoch milliseconds
   */
  public CommandTrackerListener(
      CommandLogRepository repository,
      CommandRules rules,
      boolean recordConsole,
      Function<String, Command> commandLookup,
      LongSupplier clock) {
    this.repository = repository;
    this.rules = rules;
    this.recordConsole = recordConsole;
    this.commandLookup = commandLookup;
    this.clock = clock;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
    Player player = event.getPlayer();
    Location location = player.getLocation();
    CommandPosition position =
        location.getWorld() == null
            ? null
            : new CommandPosition(
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    record(
        CommandSource.PLAYER,
        player.getUniqueId(),
        player.getName(),
        event.getMessage(),
        position,
        event.isCancelled());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onServerCommand(ServerCommandEvent event) {
    if (!recordConsole) {
      return;
    }
    // Command blocks and command minecarts also fire this event; they are never recorded.
    CommandSender sender = event.getSender();
    CommandSource source;
    if (sender instanceof RemoteConsoleCommandSender) {
      source = CommandSource.RCON;
    } else if (sender instanceof ConsoleCommandSender) {
      source = CommandSource.CONSOLE;
    } else {
      return;
    }
    record(source, null, sender.getName(), event.getCommand(), null, event.isCancelled());
  }

  private void record(
      CommandSource source,
      UUID playerId,
      String name,
      String typed,
      CommandPosition position,
      boolean cancelled) {
    try {
      String line = typed.strip();
      if (!line.startsWith("/")) {
        line = "/" + line;
      }
      String label = CommandRules.labelOf(line);
      if (label.isEmpty()) {
        return;
      }
      CommandNames names = CommandNames.resolve(label, commandLookup);
      if (!rules.shouldRecord(names.all())) {
        return;
      }
      String stored = CommandRules.redact(line, rules.redaction(names.all()));
      CommandUse use =
          new CommandUse(
              clock.getAsLong(),
              source,
              playerId,
              name,
              names.primary(),
              stored,
              position,
              cancelled);
      repository
          .insert(use)
          .exceptionally(
              error -> {
                IceCream.logger.log(
                    Level.SEVERE, "Failed to store a command run by " + name, error);
                return null;
              });
    } catch (RuntimeException e) {
      IceCream.logger.log(Level.SEVERE, "Failed to record a command run by " + name, e);
    }
  }
}
