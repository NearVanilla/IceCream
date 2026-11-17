package com.nearvanilla.iceCream.testutil;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.data.InlinedRegistryBuilderProvider;
import io.papermc.paper.registry.data.InstrumentRegistryEntry;
import io.papermc.paper.registry.data.TrimMaterialRegistryEntry;
import io.papermc.paper.registry.data.TrimPatternRegistryEntry;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.ConfirmationType;
import io.papermc.paper.registry.data.dialog.type.DialogListType;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import io.papermc.paper.registry.data.dialog.type.NoticeType;
import io.papermc.paper.registry.data.dialog.type.ServerLinksType;
import io.papermc.paper.registry.set.RegistrySet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Lets a test build, show and click Paper dialogs. Paper builds dialogs through factories that it
 * finds with a {@link java.util.ServiceLoader}, and MockBukkit ships none, so any code that opens a
 * dialog fails with a {@code NoSuchElementException}. The two services in this class ({@link
 * Provider} and {@link RegistryProvider}) are registered in {@code
 * src/test/resources/META-INF/services}. They build plain recording objects instead of real
 * dialogs, and a {@link DialogPlayer} keeps every dialog that is shown to it as a {@link
 * ShownDialog}, which a test can read and click.
 *
 * <pre>{@code
 * DialogPlayer player = DialogTestUtils.addPlayer(server, "Alex");
 * AreaForms.openCreate(player);
 * ShownDialog dialog = player.lastDialog();
 * dialog.click("Create", dialog.responseWithInitialValues().text("label", "Home"), player);
 * }</pre>
 */
public final class DialogTestUtils {

  private DialogTestUtils() {}

  /**
   * Adds a player that records the dialogs shown to it.
   *
   * @param server the mock server
   * @param name the player name
   * @return the added player
   */
  public static DialogPlayer addPlayer(ServerMock server, String name) {
    DialogPlayer player = new DialogPlayer(server, name);
    server.addPlayer(player);
    return player;
  }

  /** A player that keeps the dialogs shown to it, which {@link PlayerMock} would drop. */
  public static class DialogPlayer extends PlayerMock {
    private final List<ShownDialog> dialogs = new ArrayList<>();

    /**
     * Creates a player with a random id.
     *
     * @param server the mock server the player belongs to
     * @param name the player name
     */
    public DialogPlayer(ServerMock server, String name) {
      super(server, name, UUID.randomUUID());
    }

    @Override
    public void showDialog(DialogLike dialog) {
      dialogs.add(new ShownDialog((FakeDialog) dialog));
    }

    /**
     * Returns every dialog shown so far.
     *
     * @return the dialogs, oldest first
     */
    public List<ShownDialog> dialogs() {
      return List.copyOf(dialogs);
    }

    /**
     * Returns the newest dialog.
     *
     * @return the dialog shown last, or null when none was shown
     */
    public ShownDialog lastDialog() {
      return dialogs.isEmpty() ? null : dialogs.getLast();
    }
  }

  /** A dialog the way a test reads it: its title, body text, inputs and buttons. */
  public static final class ShownDialog {
    private final FakeDialog dialog;

    private ShownDialog(FakeDialog dialog) {
      this.dialog = dialog;
    }

    /**
     * Returns the title as plain text.
     *
     * @return the title
     */
    public String title() {
      return MessageTestUtils.plainText(dialog.base().title());
    }

    /**
     * Returns the body as plain text, one entry per body element. An item body reads as {@code
     * [MATERIAL] description}.
     *
     * @return the body lines
     */
    public List<String> body() {
      List<String> lines = new ArrayList<>();
      for (DialogBody body : dialog.base().body()) {
        if (body instanceof PlainMessageDialogBody plain) {
          lines.add(MessageTestUtils.plainText(plain.contents()));
        } else if (body instanceof ItemDialogBody item) {
          lines.add(
              "["
                  + item.item().getType()
                  + "] "
                  + MessageTestUtils.plainText(item.description().contents()));
        }
      }
      return lines;
    }

    /**
     * Returns the inputs.
     *
     * @return the inputs in the order they were added
     */
    public List<DialogInput> inputs() {
      return dialog.base().inputs();
    }

    /**
     * Returns the keys of the inputs.
     *
     * @return the keys in the order the inputs were added
     */
    public List<String> inputKeys() {
      return inputs().stream().map(DialogInput::key).toList();
    }

    /**
     * Returns a text input.
     *
     * @param key the key of the input
     * @return the input
     * @throws AssertionError if there is no text input with that key
     */
    public TextDialogInput text(String key) {
      for (DialogInput input : inputs()) {
        if (input instanceof TextDialogInput text && text.key().equals(key)) return text;
      }
      throw new AssertionError("No text input " + key + " in " + inputKeys());
    }

    /**
     * Returns a slider.
     *
     * @param key the key of the input
     * @return the input
     * @throws AssertionError if there is no slider with that key
     */
    public NumberRangeDialogInput number(String key) {
      for (DialogInput input : inputs()) {
        if (input instanceof NumberRangeDialogInput number && number.key().equals(key)) {
          return number;
        }
      }
      throw new AssertionError("No slider " + key + " in " + inputKeys());
    }

    /**
     * Returns a checkbox.
     *
     * @param key the key of the input
     * @return the input
     * @throws AssertionError if there is no checkbox with that key
     */
    public BooleanDialogInput bool(String key) {
      for (DialogInput input : inputs()) {
        if (input instanceof BooleanDialogInput bool && bool.key().equals(key)) return bool;
      }
      throw new AssertionError("No checkbox " + key + " in " + inputKeys());
    }

    /**
     * Returns the labels of the buttons, not counting the one that closes the dialog.
     *
     * @return the button labels as plain text
     */
    public List<String> buttons() {
      return buttonList().stream()
          .map(button -> MessageTestUtils.plainText(button.label()))
          .toList();
    }

    /**
     * Returns the label of the button that closes the dialog.
     *
     * @return the label, or null when the dialog has none
     */
    public String exitLabel() {
      ActionButton exit = exitButton();
      return exit == null ? null : MessageTestUtils.plainText(exit.label());
    }

    /**
     * Tells whether the button that closes the dialog has an action, which it only has when closing
     * the dialog also does something such as going back to the previous form.
     *
     * @return true when pressing the closing button runs something
     */
    public boolean exitHasAction() {
      ActionButton exit = exitButton();
      return exit != null && exit.action() != null;
    }

    /**
     * Returns how many columns the buttons are laid out in.
     *
     * @return the number of columns, or 0 when the dialog is not a multi action dialog
     */
    public int columns() {
      return dialog.type() instanceof MultiActionType multi ? multi.columns() : 0;
    }

    /**
     * Presses a button the way the client does: the callback receives the response and the player.
     * Does nothing for a button without an action, which only closes the dialog.
     *
     * @param label the plain text label of the button
     * @param response what the player typed
     * @param player the player who pressed the button
     * @throws AssertionError if there is no button with that label
     */
    public void click(String label, Response response, Audience player) {
      ActionButton button = find(label);
      if (button.action() instanceof FakeCustomClick custom) {
        custom.callback().accept(response, player);
      }
    }

    /**
     * Presses the button that closes the dialog.
     *
     * @param response what the player typed
     * @param player the player who pressed the button
     */
    public void clickExit(Response response, Audience player) {
      ActionButton exit = exitButton();
      if (exit != null && exit.action() instanceof FakeCustomClick custom) {
        custom.callback().accept(response, player);
      }
    }

    /**
     * Tells whether a button has an action, which a button that only closes the dialog does not.
     *
     * @param label the plain text label of the button
     * @return true when pressing the button runs something
     */
    public boolean hasAction(String label) {
      return find(label).action() != null;
    }

    /**
     * Builds the response the client would send if the player pressed a button without changing
     * anything: every input holds the value it was shown with.
     *
     * @return the response, ready to change
     */
    public Response responseWithInitialValues() {
      Response response = new Response();
      for (DialogInput input : inputs()) {
        if (input instanceof TextDialogInput text) {
          response.text(text.key(), text.initial());
        } else if (input instanceof BooleanDialogInput bool) {
          response.bool(bool.key(), bool.initial());
        } else if (input instanceof NumberRangeDialogInput number) {
          response.number(number.key(), number.initial());
        }
      }
      return response;
    }

    private ActionButton find(String label) {
      for (ActionButton button : buttonList()) {
        if (MessageTestUtils.plainText(button.label()).equals(label)) return button;
      }
      throw new AssertionError("No button " + label + " in " + buttons());
    }

    private List<ActionButton> buttonList() {
      if (dialog.type() instanceof MultiActionType multi) return multi.actions();
      if (dialog.type() instanceof ConfirmationType confirmation) {
        return List.of(confirmation.yesButton(), confirmation.noButton());
      }
      return List.of();
    }

    private ActionButton exitButton() {
      return dialog.type() instanceof MultiActionType multi ? multi.exitAction() : null;
    }
  }

  /** The values a player typed into a dialog, as the client sends them. */
  public static final class Response implements DialogResponseView {
    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, Boolean> bools = new HashMap<>();
    private final Map<String, Float> numbers = new HashMap<>();

    /**
     * Sets a text input.
     *
     * @param key the key of the input
     * @param value the typed text
     * @return this response
     */
    public Response text(String key, String value) {
      texts.put(key, value);
      return this;
    }

    /**
     * Sets a checkbox.
     *
     * @param key the key of the input
     * @param value whether it is ticked
     * @return this response
     */
    public Response bool(String key, boolean value) {
      bools.put(key, value);
      return this;
    }

    /**
     * Sets a slider.
     *
     * @param key the key of the input
     * @param value the chosen value
     * @return this response
     */
    public Response number(String key, Float value) {
      numbers.put(key, value);
      return this;
    }

    /**
     * Removes an input, as when the dialog has no input with that key.
     *
     * @param key the key of the input
     * @return this response
     */
    public Response without(String key) {
      texts.remove(key);
      bools.remove(key);
      numbers.remove(key);
      return this;
    }

    @Override
    public BinaryTagHolder payload() {
      return null;
    }

    @Override
    public String getText(String key) {
      return texts.get(key);
    }

    @Override
    public Boolean getBoolean(String key) {
      return bools.get(key);
    }

    @Override
    public Float getFloat(String key) {
      return numbers.get(key);
    }
  }

  /** A custom click action that keeps its callback so a test can run it. */
  private record FakeCustomClick(DialogActionCallback callback, ClickCallback.Options options)
      implements DialogAction.CustomClickAction {
    @Override
    public Key id() {
      return Key.key("icecream", "test_click");
    }

    @Override
    public BinaryTagHolder additions() {
      return null;
    }
  }

  /** The dialog Paper's registry would build, keeping what the code gave it. */
  private record FakeDialog(DialogBase base, DialogType type) implements Dialog {
    @Override
    @SuppressWarnings("removal")
    public NamespacedKey getKey() {
      return new NamespacedKey("icecream", "test_dialog");
    }
  }

  /** Builds the parts of a dialog. Registered as a service for Paper's static factories. */
  public static final class Provider implements DialogInstancesProvider {
    @Override
    public DialogBase.Builder dialogBaseBuilder(Component title) {
      return builder(DialogBase.Builder.class, DialogBase.class, Map.of("title", title));
    }

    @Override
    public ActionButton.Builder actionButtonBuilder(Component label) {
      return builder(ActionButton.Builder.class, ActionButton.class, Map.of("label", label));
    }

    @Override
    public DialogAction.CustomClickAction register(
        DialogActionCallback callback, ClickCallback.Options options) {
      return new FakeCustomClick(callback, options);
    }

    @Override
    public DialogAction.StaticAction staticAction(ClickEvent event) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DialogAction.CommandTemplateAction commandTemplate(String template) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DialogAction.CustomClickAction customClick(Key id, BinaryTagHolder additions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ItemDialogBody.Builder itemDialogBodyBuilder(ItemStack item) {
      return builder(ItemDialogBody.Builder.class, ItemDialogBody.class, Map.of("item", item));
    }

    @Override
    public PlainMessageDialogBody plainMessageDialogBody(Component contents) {
      return value(PlainMessageDialogBody.class, Map.of("contents", contents));
    }

    @Override
    public PlainMessageDialogBody plainMessageDialogBody(Component contents, int width) {
      return value(PlainMessageDialogBody.class, Map.of("contents", contents, "width", width));
    }

    @Override
    public BooleanDialogInput.Builder booleanBuilder(String key, Component label) {
      return builder(
          BooleanDialogInput.Builder.class,
          BooleanDialogInput.class,
          Map.of("key", key, "label", label));
    }

    @Override
    public NumberRangeDialogInput.Builder numberRangeBuilder(
        String key, Component label, float start, float end) {
      return builder(
          NumberRangeDialogInput.Builder.class,
          NumberRangeDialogInput.class,
          Map.of("key", key, "label", label, "start", start, "end", end));
    }

    @Override
    public SingleOptionDialogInput.Builder singleOptionBuilder(
        String key, Component label, List<SingleOptionDialogInput.OptionEntry> entries) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SingleOptionDialogInput.OptionEntry singleOptionEntry(
        String id, Component display, boolean initial) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TextDialogInput.Builder textBuilder(String key, Component label) {
      return builder(
          TextDialogInput.Builder.class, TextDialogInput.class, Map.of("key", key, "label", label));
    }

    @Override
    public TextDialogInput.MultilineOptions multilineOptions(Integer maxLines, Integer height) {
      Map<String, Object> properties = new HashMap<>();
      properties.put("maxLines", maxLines);
      properties.put("height", height);
      return value(TextDialogInput.MultilineOptions.class, properties);
    }

    @Override
    public ConfirmationType confirmation(ActionButton yes, ActionButton no) {
      return value(ConfirmationType.class, Map.of("yesButton", yes, "noButton", no));
    }

    @Override
    public DialogListType.Builder dialogList(RegistrySet<Dialog> dialogs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MultiActionType.Builder multiAction(List<ActionButton> actions) {
      return builder(
          MultiActionType.Builder.class, MultiActionType.class, Map.of("actions", actions));
    }

    @Override
    public NoticeType notice() {
      throw new UnsupportedOperationException();
    }

    @Override
    public NoticeType notice(ActionButton button) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ServerLinksType serverLinks(ActionButton exit, int columns, int width) {
      throw new UnsupportedOperationException();
    }
  }

  /** Builds a dialog from the code that fills in its parts. Registered as a service. */
  public static final class RegistryProvider implements InlinedRegistryBuilderProvider {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Dialog createDialog(
        Consumer<RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder>> filler) {
      Map<String, Object> entry = new HashMap<>();
      DialogRegistryEntry.Builder builder = value(DialogRegistryEntry.Builder.class, entry);
      RegistryBuilderFactory factory =
          value(RegistryBuilderFactory.class, Map.of("empty", builder));
      filler.accept(factory);
      return new FakeDialog((DialogBase) entry.get("base"), (DialogType) entry.get("type"));
    }

    @Override
    public org.bukkit.MusicInstrument createInstrument(
        Consumer<
                RegistryBuilderFactory<
                    org.bukkit.MusicInstrument, ? extends InstrumentRegistryEntry.Builder>>
            filler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public org.bukkit.inventory.meta.trim.TrimMaterial createTrimMaterial(
        Consumer<
                RegistryBuilderFactory<
                    org.bukkit.inventory.meta.trim.TrimMaterial,
                    ? extends TrimMaterialRegistryEntry.Builder>>
            filler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public org.bukkit.inventory.meta.trim.TrimPattern createTrimPattern(
        Consumer<
                RegistryBuilderFactory<
                    org.bukkit.inventory.meta.trim.TrimPattern,
                    ? extends TrimPatternRegistryEntry.Builder>>
            filler) {
      throw new UnsupportedOperationException();
    }
  }

  // The dialog parts are interfaces with many accessors, so they are built as proxies. A call with
  // an argument records it under the method's name and returns the proxy again, which is how the
  // fluent builders work. A call without one reads back what was recorded.
  private static <B, T> B builder(
      Class<B> builderType, Class<T> builtType, Map<String, Object> initial) {
    return proxy(
        builderType,
        new HashMap<>(initial),
        properties -> value(builtType, new HashMap<>(properties)));
  }

  private static <T> T value(Class<T> type, Map<String, Object> properties) {
    return proxy(type, properties, null);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(
      Class<T> type, Map<String, Object> properties, Function<Map<String, Object>, Object> build) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          String name = method.getName();
          if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
              case "toString" -> type.getSimpleName() + properties;
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> null;
            };
          }
          if (args == null || args.length == 0) {
            if (name.equals("build") && build != null) return build.apply(properties);
            Object recorded = properties.get(name);
            return recorded != null ? recorded : defaultValue(method.getReturnType());
          }
          properties.put(name, args.length == 1 ? args[0] : Arrays.asList(args));
          return method.getReturnType().isInstance(proxy)
              ? proxy
              : defaultValue(method.getReturnType());
        };
    return (T)
        Proxy.newProxyInstance(
            DialogTestUtils.class.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == float.class) return 0f;
    if (type == long.class) return 0L;
    if (type == double.class) return 0d;
    if (type == List.class) return List.of();
    return null;
  }
}
