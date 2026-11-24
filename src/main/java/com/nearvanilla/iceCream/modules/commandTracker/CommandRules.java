package com.nearvanilla.iceCream.modules.commandTracker;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which commands are recorded and which of their arguments are hidden. Every rule matches a
 * command by any of its names, so a rule for {@code login} also covers an alias such as {@code l}
 * when the caller passes both.
 */
public final class CommandRules {
  /** Stands in for hidden arguments. */
  public static final String REDACTED = "[redacted]";

  /** How much of a command line is hidden. */
  public enum Redaction {
    /** The whole line is kept. */
    NONE,
    /** The first argument (such as a message recipient) is kept and the rest is hidden. */
    AFTER_FIRST_ARGUMENT,
    /** Every argument is hidden. */
    ALL_ARGUMENTS
  }

  private final Set<String> ignored;
  private final Set<String> tracked;
  private final Set<String> redactAll;
  private final Set<String> redactAfterFirst;

  /**
   * Creates the rules. Names may be written with a slash, a namespace or in any case.
   *
   * @param ignored commands that are never recorded
   * @param tracked when not empty, only these commands are recorded
   * @param redactAll commands whose arguments are all hidden
   * @param redactAfterFirst commands whose arguments after the first are hidden
   */
  public CommandRules(
      Collection<String> ignored,
      Collection<String> tracked,
      Collection<String> redactAll,
      Collection<String> redactAfterFirst) {
    this.ignored = normalizeAll(ignored);
    this.tracked = normalizeAll(tracked);
    this.redactAll = normalizeAll(redactAll);
    this.redactAfterFirst = normalizeAll(redactAfterFirst);
  }

  private static Set<String> normalizeAll(Collection<String> names) {
    return names.stream()
        .map(CommandRules::normalize)
        .filter(name -> !name.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Turns a command name into the form rules and storage use: lowercase, without a leading slash or
   * a namespace such as {@code minecraft:}.
   */
  public static String normalize(String name) {
    String trimmed = name.strip();
    if (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    int colon = trimmed.lastIndexOf(':');
    if (colon >= 0) {
      trimmed = trimmed.substring(colon + 1);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  /** Reads the command label, as typed, from a command line with or without a leading slash. */
  public static String labelOf(String commandLine) {
    String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
    int space = line.indexOf(' ');
    return space < 0 ? line : line.substring(0, space);
  }

  /**
   * Whether a command is recorded.
   *
   * @param names the command's normalized names, such as the typed label and its real name
   */
  public boolean shouldRecord(Collection<String> names) {
    if (names.stream().anyMatch(ignored::contains)) {
      return false;
    }
    return tracked.isEmpty() || names.stream().anyMatch(tracked::contains);
  }

  /**
   * How much of a command is hidden.
   *
   * @param names the command's normalized names, such as the typed label and its real name
   */
  public Redaction redaction(Collection<String> names) {
    if (names.stream().anyMatch(redactAll::contains)) {
      return Redaction.ALL_ARGUMENTS;
    }
    if (names.stream().anyMatch(redactAfterFirst::contains)) {
      return Redaction.AFTER_FIRST_ARGUMENT;
    }
    return Redaction.NONE;
  }

  /**
   * Hides arguments of a command line.
   *
   * @param commandLine the command line, starting with the label
   * @param redaction how much to hide
   * @return the line with hidden arguments replaced by {@link #REDACTED}
   */
  public static String redact(String commandLine, Redaction redaction) {
    if (redaction == Redaction.NONE) {
      return commandLine;
    }
    int kept = redaction == Redaction.ALL_ARGUMENTS ? 1 : 2;
    String[] words = commandLine.strip().split("\\s+", kept + 1);
    if (words.length <= kept) {
      return String.join(" ", words);
    }
    String[] prefix = Arrays.copyOf(words, kept);
    return String.join(" ", prefix) + " " + REDACTED;
  }
}
