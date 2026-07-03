package com.nearvanilla.iceCream.modules.worldTour.commands;

import com.nearvanilla.iceCream.modules.worldTour.WorldTourModule;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

/**
 * WorldTourCommand provides all subcommands for the World Tour module: join, leave, start,
 * takeover, tpall, glow, end, forceend, weather, and time.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2026-06-26
 */
@SuppressWarnings("unused")
public class WorldTourCommand {

  private static final Set<String> WEATHER_TYPES = Set.of("clear", "rain", "thunder");
  private static final Set<String> TIME_TYPES = Set.of("day", "night", "noon", "midnight");

  private static final Component JOINED =
      MiniMessage.miniMessage().deserialize("<green>✓ You have joined the World Tour!</green>");
  private static final Component LEFT =
      MiniMessage.miniMessage().deserialize("<red>✓ You have left the World Tour.</red>");
  private static final Component ALREADY_JOINED =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ You are already participating in the World Tour!</red>");
  private static final Component NOT_JOINED =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ You are not participating in the World Tour.</red>");
  private static final Component PLAYERS_ONLY =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ This command can only be used by players.</red>");
  private static final Component NO_HOST =
      MiniMessage.miniMessage().deserialize("<red>✗ There is no World Tour host set.</red>");
  private static final Component HOST_SET =
      MiniMessage.miniMessage().deserialize("<green>✓ You are now the World Tour host!</green>");

  private static final Component TAKEOVER_REQUEST_SENT =
      MiniMessage.miniMessage()
          .deserialize(
              "<yellow>Takeover request sent. Waiting for the current host to respond.</yellow>");
  private static final Component TAKEOVER_DENIED =
      MiniMessage.miniMessage().deserialize("<red>✗ Your takeover request was denied.</red>");
  private static final Component TAKEOVER_NO_REQUEST =
      MiniMessage.miniMessage().deserialize("<red>✗ No pending takeover request found.</red>");
  private static final Component TPALL_SUCCESS =
      MiniMessage.miniMessage()
          .deserialize("<green>✓ All participants have been teleported to you!</green>");
  private static final Component TPALL_PARTICIPANT =
      MiniMessage.miniMessage()
          .deserialize("<green>✓ You have been teleported to the World Tour host.</green>");
  private static final Component WEATHER_CHANGED =
      MiniMessage.miniMessage().deserialize("<green>✓ Weather has been updated.</green>");
  private static final Component TIME_CHANGED =
      MiniMessage.miniMessage().deserialize("<green>✓ Time has been updated.</green>");
  private static final Component UNKNOWN_WEATHER =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ Unknown weather type. Use: clear, rain, or thunder.</red>");
  private static final Component UNKNOWN_TIME =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ Unknown time type. Use: day, night, noon, or midnight.</red>");
  private static final Component TOUR_STARTED =
      MiniMessage.miniMessage()
          .deserialize(
              "<green>✓ World Tour started! Time set to the configured start time, "
                  + "weather set to clear, keepInventory enabled across all loaded dimensions.</green>");
  private static final Component TOUR_ENDED =
      MiniMessage.miniMessage()
          .deserialize("<green>✓ World Tour ended. Environment restored.</green>");
  private static final Component NOT_HOST =
      MiniMessage.miniMessage()
          .deserialize("<red>✗ Only the World Tour host can use this command.</red>");

  private final WorldTourModule module;

  public WorldTourCommand(WorldTourModule module) {
    this.module = module;
  }

  @Suggestions("weather_types")
  public List<String> suggestWeather(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return WEATHER_TYPES.stream().toList();
  }

  @Suggestions("time_types")
  public List<String> suggestTime(CommandContext<CommandSourceStack> ctx, CommandInput input) {
    return TIME_TYPES.stream().toList();
  }

  /**
   * Opt into the World Tour as a participant. Sets a persistent data flag so the player is
   * recognized on reconnect. Idempotent - returns an error if already joined.
   */
  @Command("worldtour join")
  @CommandDescription("Opt into the World Tour.")
  public void joinCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    var container = player.getPersistentDataContainer();
    if (Boolean.TRUE.equals(
        container.get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN))) {
      player.sendMessage(ALREADY_JOINED);
      return;
    }
    container.set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, true);
    player.sendMessage(JOINED);
  }

  /**
   * Opt out of the World Tour as a participant. Clears the joined persistent data flag. If the
   * player is the current host, the command is rejected - they must use /worldtour end to conclude
   * the tour for everyone.
   */
  @Command("worldtour leave")
  @CommandDescription("Opt out of the World Tour. Hosts must use /worldtour end instead.")
  public void leaveCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    if (player.equals(module.getCurrentHost())) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red>✗ You are the host. Use <click:run_command:/worldtour end>/worldtour end</click> to conclude the tour. <gray>This will end the tour for all participants.</gray></red>"));
      return;
    }

    var container = player.getPersistentDataContainer();
    if (!Boolean.TRUE.equals(
        container.get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN))) {
      player.sendMessage(NOT_JOINED);
      return;
    }
    container.set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, false);
    player.sendMessage(LEFT);
  }

  /**
   * Become host and launch the World Tour. Sets the player as current host, applies the glowing
   * effect, marks them as joined, and calls applyTourEnvironment() to set the time to the
   * configured start time, weather to clear, and keepInventory to true across all loaded
   * dimensions. Blocked if a tour is already ongoing.
   */
  @Command("worldtour start")
  @CommandDescription(
      "Start a World Tour: become host, set time to the configured start time, weather to clear, and enable keepInventory. Blocked if a tour is already ongoing.")
  @Permission("icecream.modules.worldtour.host")
  public void startCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    // A tour is already ongoing — block.
    if (module.getCurrentHost() != null) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<red>✗ A World Tour is already ongoing. Use <click:run_command:/worldtour takeover>/worldtour takeover</click> to request host.</red>"));
      return;
    }

    // Become host and apply environment.
    module.setCurrentHost(player);
    module.setGlowing(player);
    player
        .getPersistentDataContainer()
        .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, true);
    applyTourEnvironment();
    player.sendMessage(TOUR_STARTED);
  }

  /**
   * Request to take over as World Tour host. If the current host is offline, automatically
   * transfers host to the requester. If the host is online, sends them an accept/deny prompt and
   * stores the pending request for confirmation. Rejected if the requester is already the host or
   * if no host is set.
   */
  @Command("worldtour takeover")
  @CommandDescription("Request to take over as World Tour host.")
  @Permission("icecream.modules.worldtour.host")
  public void takeoverCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    Player currentHost = module.getCurrentHost();

    if (player.equals(currentHost)) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize("<red>✗ You are already the World Tour host.</red>"));
      return;
    }
    if (currentHost == null) {
      player.sendMessage(NO_HOST);
      return;
    }

    // Host is offline — auto-transfer.
    if (!currentHost.isOnline()) {
      module.clearHost(false);
      module.setCurrentHost(player);
      module.setGlowing(player);
      player
          .getPersistentDataContainer()
          .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, true);
      player.sendMessage(HOST_SET);
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize("<gray>The previous host was offline. Tour continues.</gray>"));
      return;
    }

    module.setPendingTakeover(currentHost.getUniqueId(), player.getUniqueId());

    Component acceptBtn =
        Component.text("[Accept]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/worldtour takeover confirm"));
    Component denyBtn =
        Component.text("[Deny]", NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand("/worldtour takeover deny"));
    Component requestMsg =
        Component.text(
                player.getName() + " wants to take over as World Tour host. ", NamedTextColor.GOLD)
            .append(acceptBtn)
            .append(Component.space())
            .append(denyBtn);
    currentHost.sendMessage(requestMsg);
    player.sendMessage(TAKEOVER_REQUEST_SENT);
  }

  /**
   * Teleport every online participant (excluding the host) to the host's current location. Sends
   * each teleported participant a notification. Reports the total count of teleported players back
   * to the host. Only usable by the current host.
   */
  @Command("worldtour tpall")
  @CommandDescription("Teleport all World Tour participants to the host.")
  @Permission("icecream.modules.worldtour.host")
  public void tpallCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    if (!player.equals(module.getCurrentHost())) {
      player.sendMessage(NOT_HOST);
      return;
    }

    Player host = module.getCurrentHost();

    int count = 0;
    for (Player online : player.getServer().getOnlinePlayers()) {
      if (online.equals(host)) continue;
      if (Boolean.TRUE.equals(
          online
              .getPersistentDataContainer()
              .get(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN))) {
        online.teleport(host.getLocation());
        online.sendMessage(TPALL_PARTICIPANT);
        count++;
      }
    }
    player.sendMessage(TPALL_SUCCESS);
    if (count > 0) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize("<green>" + count + " participant(s) teleported.</green>"));
    }
  }

  /**
   * Transfer the host's glowing effect to a specified target player. The target is notified that
   * they are now glowing. Only usable by the current host.
   */
  @Command("worldtour glow <player>")
  @CommandDescription("Transfer the host's Glowing effect to another player.")
  @Permission("icecream.modules.worldtour.host")
  public void glowCommand(CommandSourceStack stack, @Argument(value = "player") Player target) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    if (!player.equals(module.getCurrentHost())) {
      player.sendMessage(NOT_HOST);
      return;
    }

    module.setGlowing(target);
    player.sendMessage(
        MiniMessage.miniMessage()
            .deserialize(
                "<green>✓ Glowing effect transferred to " + target.getName() + ".</green>"));
    target.sendMessage(
        MiniMessage.miniMessage()
            .deserialize("<green>✓ You are now glowing for the World Tour!</green>"));
  }

  /**
   * Accept a pending takeover request as the current host. Removes the pending request from
   * storage, clears the current host, and transfers host and glowing effect to the requester.
   * Notifies both parties of the result. Fails gracefully if the requester is no longer online.
   */
  @Command("worldtour takeover confirm")
  @CommandDescription("Accept a pending World Tour host takeover request.")
  @Permission("icecream.modules.worldtour.host")
  public void takeoverConfirmCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    UUID requesterId = module.removePendingTakeover(player.getUniqueId());
    if (requesterId == null) {
      player.sendMessage(TAKEOVER_NO_REQUEST);
      return;
    }

    Player requester = player.getServer().getPlayer(requesterId);

    // Transfer host to the requester.
    module.clearHost(false);

    if (requester != null && requester.isOnline()) {
      module.setCurrentHost(requester);
      module.setGlowing(requester);
      requester
          .getPersistentDataContainer()
          .set(WorldTourModule.JOINED_KEY, PersistentDataType.BOOLEAN, true);
      requester.sendMessage(HOST_SET);
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize(
                  "<green>✓ " + requester.getName() + " is now the World Tour host.</green>"));
    } else {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize("<red>✗ The requesting player is no longer online.</red>"));
    }
  }

  /**
   * Deny a pending takeover request as the current host. Removes the pending request from storage
   * and notifies the requester that their request was denied. Does not affect host status or tour
   * state.
   */
  @Command("worldtour takeover deny")
  @CommandDescription("Deny a pending World Tour host takeover request.")
  @Permission("icecream.modules.worldtour.host")
  public void takeoverDenyCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    UUID requesterId = module.removePendingTakeover(player.getUniqueId());
    if (requesterId == null) {
      player.sendMessage(TAKEOVER_NO_REQUEST);
      return;
    }

    Player requester = player.getServer().getPlayer(requesterId);
    if (requester != null && requester.isOnline()) {
      requester.sendMessage(TAKEOVER_DENIED);
    }
    player.sendMessage(
        MiniMessage.miniMessage().deserialize("<green>✓ Takeover request denied.</green>"));
  }

  /**
   * End the World Tour as the current host. Calls endTour() on the module to clear the host,
   * restore original time, weather, and keepInventory settings across all loaded dimensions. Only
   * usable by the current host. Rejects if no host is set or if the sender is not the host.
   */
  @Command("worldtour end")
  @CommandDescription("End the World Tour: clear the host and restore original environment.")
  @Permission("icecream.modules.worldtour.host")
  public void endCommand(CommandSourceStack stack) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    Player currentHost = module.getCurrentHost();
    if (currentHost == null) {
      player.sendMessage(NO_HOST);
      return;
    }

    if (!player.equals(currentHost)) {
      player.sendMessage(
          MiniMessage.miniMessage()
              .deserialize("<red>✗ Only the World Tour host can end the tour.</red>"));
      return;
    }

    module.endTour();
    player.sendMessage(TOUR_ENDED);
  }

  /**
   * Force-end the World Tour regardless of host status. Calls endTour() to restore original
   * environment settings. Does not check if the sender is the host, allowing admins and moderators
   * to clean up. Fails gracefully if no tour is active.
   */
  @Command("worldtour forceend")
  @CommandDescription("Force-end the World Tour as an admin or moderator.")
  @Permission("icecream.modules.worldtour.host")
  public void forceEndCommand(CommandSourceStack stack) {
    if (module.getCurrentHost() == null) {
      stack.getSender().sendMessage(NO_HOST);
      return;
    }

    module.endTour();
    stack
        .getSender()
        .sendMessage(
            MiniMessage.miniMessage()
                .deserialize("<green>✓ World Tour force-ended. Environment restored.</green>"));
  }

  /**
   * Set the weather across all loaded World Tour dimensions. Accepts clear, rain, or thunder
   * (case-insensitive, autocompleted). Delegates to module.setWeather() which applies the change to
   * all dimensions. Only usable if a host is online. Returns an error for unknown weather types.
   */
  @Command("worldtour weather <type>")
  @CommandDescription("Change the weather across all loaded World Tour dimensions.")
  @Permission("icecream.modules.worldtour.host")
  public void weatherCommand(
      CommandSourceStack stack,
      @Argument(value = "type", suggestions = "weather_types") String type) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    Player host = module.getCurrentHost();
    if (host == null || !host.isOnline()) {
      player.sendMessage(NO_HOST);
      return;
    }

    type = type.toLowerCase(Locale.ROOT);
    if (!WEATHER_TYPES.contains(type)) {
      player.sendMessage(UNKNOWN_WEATHER);
      return;
    }

    module.setWeather(type);
    player.sendMessage(WEATHER_CHANGED);
  }

  /**
   * Set the time across all loaded World Tour dimensions. Accepts day, night, noon, or midnight
   * (case-insensitive, autocompleted). Converts the type to ticks via module.getTimeTicks() and
   * applies it via module.setTime(). Only usable if a host is online. Returns an error for unknown
   * time types.
   */
  @Command("worldtour time <type>")
  @CommandDescription("Change the time across all loaded World Tour dimensions.")
  @Permission("icecream.modules.worldtour.host")
  public void timeCommand(
      CommandSourceStack stack, @Argument(value = "type", suggestions = "time_types") String type) {
    Player player = requirePlayer(stack);
    if (player == null) return;

    Player host = module.getCurrentHost();
    if (host == null || !host.isOnline()) {
      player.sendMessage(NO_HOST);
      return;
    }

    type = type.toLowerCase(Locale.ROOT);
    if (!TIME_TYPES.contains(type)) {
      player.sendMessage(UNKNOWN_TIME);
      return;
    }

    module.setTime(module.getTimeTicks(type));
    player.sendMessage(TIME_CHANGED);
  }

  /**
   * Extracts a player from the command source stack, sending an error message if the sender is not
   * a player.
   *
   * @param stack the command source stack
   * @return the player, or {@code null} if the sender is not a player
   */
  private static Player requirePlayer(CommandSourceStack stack) {
    if (!(stack.getSender() instanceof Player player)) {
      stack.getSender().sendMessage(PLAYERS_ONLY);
      return null;
    }
    return player;
  }

  /** Saves the current environment, then applies day time, clear weather, and keepInventory. */
  private void applyTourEnvironment() {
    module.applyTourEnvironment();
  }
}
