package com.nearvanilla.iceCream.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Helpers for testing Cloud command classes by calling their methods directly: command sources and
 * contexts that answer what the methods ask of them, a player that records teleports, and readers
 * for the click and hover events inside a message.
 */
public final class CommandTestUtils {
  private CommandTestUtils() {}

  /**
   * Creates the source a command method receives.
   *
   * @param sender who ran the command
   * @return a source whose {@code getSender()} is the sender
   */
  public static CommandSourceStack stack(CommandSender sender) {
    CommandSourceStack stack = mock(CommandSourceStack.class);
    when(stack.getSender()).thenReturn(sender);
    return stack;
  }

  /**
   * Creates the context a suggestion method receives while a player is typing.
   *
   * @param stack the source of the command, which {@code sender()} answers
   * @param rawInput everything typed so far, such as {@code maptools set claim home }
   * @return the context
   */
  @SuppressWarnings("unchecked")
  public static CommandContext<CommandSourceStack> context(
      CommandSourceStack stack, String rawInput) {
    CommandContext<CommandSourceStack> context = mock(CommandContext.class);
    CommandInput input = mock(CommandInput.class);
    when(input.input()).thenReturn(rawInput);
    when(context.sender()).thenReturn(stack);
    when(context.rawInput()).thenReturn(input);
    return context;
  }

  /**
   * Adds a player that may manage areas, stands in the given world and records where it was
   * teleported, since MockBukkit does not implement asynchronous teleporting.
   *
   * @param server the mock server
   * @param name the player name
   * @param home the world the player stands in
   * @return the added player
   */
  public static TeleportingPlayer addTeleportingPlayer(ServerMock server, String name, World home) {
    TeleportingPlayer player = new TeleportingPlayer(server, name);
    server.addPlayer(player);
    player.addAttachment(IceCream.instance, MapToolsUtils.PERMISSION, true);
    player.setLocation(new Location(home, 0, 64, 0, 30f, 10f));
    return player;
  }

  /** A player that records the dialogs shown to it and the places it was teleported to. */
  public static final class TeleportingPlayer extends DialogPlayer {
    private final List<Location> teleports = new ArrayList<>();

    private TeleportingPlayer(ServerMock server, String name) {
      super(server, name);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
      teleports.add(location.clone());
      return CompletableFuture.completedFuture(true);
    }

    /**
     * Returns every place the player was teleported to.
     *
     * @return the locations, oldest first
     */
    public List<Location> teleports() {
      return List.copyOf(teleports);
    }
  }

  /**
   * Finds the commands that clicking parts of a message would run.
   *
   * @param message the message
   * @return the commands, in the order they appear in the message
   */
  public static List<String> clickCommands(Component message) {
    return clicks(message, ClickEvent.Action.RUN_COMMAND);
  }

  /**
   * Finds the text that clicking parts of a message would put into the chat box, as a command to
   * finish typing.
   *
   * @param message the message
   * @return the suggested commands, in the order they appear in the message
   */
  public static List<String> clickSuggestions(Component message) {
    return clicks(message, ClickEvent.Action.SUGGEST_COMMAND);
  }

  private static List<String> clicks(Component message, ClickEvent.Action action) {
    List<String> values = new ArrayList<>();
    collectClicks(message, action, values);
    return values;
  }

  private static void collectClicks(
      Component component, ClickEvent.Action action, List<String> values) {
    ClickEvent click = component.clickEvent();
    if (click != null && click.action() == action) {
      values.add(((ClickEvent.Payload.Text) click.payload()).value());
    }
    for (Component child : component.children()) collectClicks(child, action, values);
  }

  /**
   * Reads the texts that hovering over parts of the message shows.
   *
   * @param message the message
   * @return the plain text of every hover text, in the order they appear in the message
   */
  public static List<String> hoverTexts(Component message) {
    List<String> texts = new ArrayList<>();
    collectHovers(message, texts);
    return texts;
  }

  private static void collectHovers(Component component, List<String> texts) {
    HoverEvent<?> hover = component.hoverEvent();
    if (hover != null && hover.value() instanceof Component text) {
      texts.add(MessageTestUtils.plainText(text));
    }
    for (Component child : component.children()) collectHovers(child, texts);
  }
}
