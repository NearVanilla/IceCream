package com.nearvanilla.iceCream.modules.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The messages the tag module sends, as MiniMessage templates.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 */
public final class TagMessages {
  private static final MiniMessage MM = MiniMessage.miniMessage();

  public static final Component AFK = MM.deserialize("<red>You cannot tag an AFK player!</red>");
  public static final Component OPTED_OUT =
      MM.deserialize(
          "<gray>You are no longer playing tag. Nobody can tag you and you cannot tag anyone."
              + " <aqua>[<click:run_command:'/tag optin'><hover:show_text:'Play tag again'>"
              + "Opt back in</hover></click>]</aqua></gray>");
  public static final Component ALREADY_OPTED_OUT =
      MM.deserialize("<gray>You are already not playing tag.</gray>");
  public static final Component OPTED_IN =
      MM.deserialize("<green>You are playing tag again.</green>");
  public static final Component ALREADY_OPTED_IN =
      MM.deserialize("<gray>You are already playing tag.</gray>");
  public static final Component RESET_NONE = MM.deserialize("<gray>Nobody is \"it\".</gray>");
  public static final Component COUNTER_ALREADY =
      MM.deserialize("<red>The tag counter is already displayed there.</red>");
  public static final Component COUNTER_HIDDEN =
      MM.deserialize("<green>Hid the tag counter.</green>");
  public static final Component PLAYERS_ONLY =
      MM.deserialize("<red>This command can only be used by players.</red>");

  private TagMessages() {}

  public static Component tagged(String tagger, String player) {
    return MM.deserialize(
        "<yellow><tagger> tagged <player>!</yellow>",
        Placeholder.unparsed("tagger", tagger),
        Placeholder.unparsed("player", player));
  }

  public static Component cooldown(String timeLeft) {
    return MM.deserialize(
        "<red>Tag game on cooldown!</red> <yellow>Time left: <time></yellow>",
        Placeholder.unparsed("time", timeLeft));
  }

  public static Component alreadyIt(String player) {
    return withPlayer("<red><player> is already it!</red>", player);
  }

  public static Component notPlaying(String player) {
    return withPlayer("<red><player> is not playing tag.</red>", player);
  }

  public static Component stoppedWhileIt(String player) {
    return withPlayer("<yellow><player> stopped playing tag and is no longer it.</yellow>", player);
  }

  public static Component resetRemoved(String player) {
    return withPlayer("<green>Removed <gold><player></gold> from being \"it\".</green>", player);
  }

  public static Component counterShown(String slot) {
    return MM.deserialize(
        "<green>Displayed the tag counter on <gold><slot></gold>.</green>",
        Placeholder.unparsed("slot", slot));
  }

  private static Component withPlayer(String template, String player) {
    TagResolver resolver = Placeholder.unparsed("player", player);
    return MM.deserialize(template, resolver);
  }
}
