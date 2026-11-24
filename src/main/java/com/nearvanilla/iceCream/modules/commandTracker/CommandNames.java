package com.nearvanilla.iceCream.modules.commandTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.command.Command;

/**
 * The names a typed command label is known by, so rules and lookups work whichever alias was typed.
 *
 * @param primary the command's real name if it is registered, otherwise the typed label, normalized
 * @param all the typed label, the real name and every alias, normalized
 */
public record CommandNames(String primary, List<String> all) {

  /**
   * Resolves a typed label.
   *
   * @param label the label as typed, without the slash, such as {@code minecraft:tp}
   * @param lookup finds a registered command by label, returning null when there is none
   */
  public static CommandNames resolve(String label, Function<String, Command> lookup) {
    String typed = CommandRules.normalize(label);
    Command command = label.isEmpty() ? null : lookup.apply(label.toLowerCase(Locale.ROOT));
    if (command == null) {
      return new CommandNames(typed, List.of(typed));
    }
    String primary = CommandRules.normalize(command.getName());
    List<String> all = new ArrayList<>();
    all.add(typed);
    all.add(primary);
    for (String alias : command.getAliases()) {
      all.add(CommandRules.normalize(alias));
    }
    return new CommandNames(primary, all.stream().distinct().toList());
  }
}
