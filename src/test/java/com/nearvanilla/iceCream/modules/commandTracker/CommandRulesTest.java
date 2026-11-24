package com.nearvanilla.iceCream.modules.commandTracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.commandTracker.CommandRules.Redaction;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRulesTest {
  private static final CommandRules NO_RULES =
      new CommandRules(List.of(), List.of(), List.of(), List.of());

  @Test
  void normalizeLowercasesAndStripsTheSlashAndNamespace() {
    assertEquals("tp", CommandRules.normalize("/Minecraft:TP"));
    assertEquals("msg", CommandRules.normalize("MSG"));
    assertEquals("say", CommandRules.normalize(" /say "));
  }

  @Test
  void labelOfReadsTheFirstWord() {
    assertEquals("Minecraft:tp", CommandRules.labelOf("/Minecraft:tp Bob"));
    assertEquals("spawn", CommandRules.labelOf("spawn"));
    assertEquals("", CommandRules.labelOf("/"));
  }

  @Test
  void recordsEverythingWithoutRules() {
    assertTrue(NO_RULES.shouldRecord(List.of("spawn")));
  }

  @Test
  void ignoredCommandsAreNotRecordedByAnyOfTheirNames() {
    CommandRules rules =
        new CommandRules(List.of("/Spawn", "minecraft:me"), List.of(), List.of(), List.of());

    assertFalse(rules.shouldRecord(List.of("spawn")));
    assertFalse(rules.shouldRecord(List.of("s", "spawn")));
    assertFalse(rules.shouldRecord(List.of("me")));
    assertTrue(rules.shouldRecord(List.of("tp")));
  }

  @Test
  void trackedCommandsLimitRecordingToThoseCommands() {
    CommandRules rules =
        new CommandRules(List.of("give"), List.of("tp", "give"), List.of(), List.of());

    assertTrue(rules.shouldRecord(List.of("teleport", "tp")));
    assertFalse(rules.shouldRecord(List.of("spawn")));
    // Ignoring wins over tracking.
    assertFalse(rules.shouldRecord(List.of("give")));
  }

  @Test
  void redactionIsChosenByAnyNameAndAllArgumentsWins() {
    CommandRules rules =
        new CommandRules(List.of(), List.of(), List.of("login", "msg"), List.of("msg", "tell"));

    assertEquals(Redaction.ALL_ARGUMENTS, rules.redaction(List.of("l", "login")));
    assertEquals(Redaction.ALL_ARGUMENTS, rules.redaction(List.of("msg")));
    assertEquals(Redaction.AFTER_FIRST_ARGUMENT, rules.redaction(List.of("w", "tell")));
    assertEquals(Redaction.NONE, rules.redaction(List.of("tp")));
  }

  @Test
  void redactAllArgumentsKeepsOnlyTheLabel() {
    assertEquals(
        "/login " + CommandRules.REDACTED,
        CommandRules.redact("/login hunter2 hunter2", Redaction.ALL_ARGUMENTS));
    assertEquals("/login", CommandRules.redact("/login", Redaction.ALL_ARGUMENTS));
    assertEquals("/login", CommandRules.redact("/login   ", Redaction.ALL_ARGUMENTS));
  }

  @Test
  void redactAfterFirstArgumentKeepsTheRecipient() {
    assertEquals(
        "/msg Bob " + CommandRules.REDACTED,
        CommandRules.redact("/msg Bob meet me   at spawn", Redaction.AFTER_FIRST_ARGUMENT));
    assertEquals("/msg Bob", CommandRules.redact("/msg Bob", Redaction.AFTER_FIRST_ARGUMENT));
    assertEquals("/msg", CommandRules.redact("/msg", Redaction.AFTER_FIRST_ARGUMENT));
  }

  @Test
  void noRedactionKeepsTheLineAsIs() {
    assertEquals("/tp  Bob", CommandRules.redact("/tp  Bob", Redaction.NONE));
  }
}
