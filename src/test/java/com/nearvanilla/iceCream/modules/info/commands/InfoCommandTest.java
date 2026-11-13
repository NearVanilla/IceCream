package com.nearvanilla.iceCream.modules.info.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.info.BuildInfo;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class InfoCommandTest {
  private static final String SUBJECT =
      "Hide spectators from tab completion and block staff mode in spectator. (#108)";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private static BuildInfo buildInfo(String subject, boolean dirty) {
    return new BuildInfo(
        "1.1-SNAPSHOT",
        "142",
        "2026-11-12T14:03:22Z",
        "61e62e9a1b2c3d4e5f60718293a4b5c6d7e8f901",
        subject,
        "2026-11-10T21:14:03+00:00",
        "main",
        dirty);
  }

  private static BuildInfo unknownBuildInfo() {
    String unknown = BuildInfo.UNKNOWN;
    return new BuildInfo(unknown, unknown, unknown, unknown, unknown, unknown, unknown, false);
  }

  private static void run(BuildInfo buildInfo, CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    new InfoCommand(buildInfo).infoCommand(source);
  }

  private List<String> runAsPlayer(BuildInfo buildInfo) {
    PlayerMock player = server.addPlayer("Staff");
    run(buildInfo, player);
    List<String> lines = new ArrayList<>();
    for (Component line = player.nextComponentMessage();
        line != null;
        line = player.nextComponentMessage()) {
      lines.add(PlainTextComponentSerializer.plainText().serialize(line));
    }
    return lines;
  }

  @Test
  void printsVersionBuildAndCommit() {
    List<String> lines = runAsPlayer(buildInfo(SUBJECT, false));

    assertEquals(
        List.of(
            "IceCream 1.1-SNAPSHOT",
            "Build: 142, built 2026-11-12 14:03 UTC",
            "Commit: 61e62e9 (main), 2026-11-10 21:14 UTC",
            SUBJECT),
        lines);
  }

  @Test
  void marksDirtyBuilds() {
    List<String> lines = runAsPlayer(buildInfo(SUBJECT, true));

    assertEquals("Commit: 61e62e9-dirty (main), 2026-11-10 21:14 UTC", lines.get(2));
  }

  @Test
  void unknownCommitOmitsSubject() {
    List<String> lines = runAsPlayer(unknownBuildInfo());

    assertEquals(
        List.of("IceCream unknown", "Build: unknown, built unknown", "Commit: unknown"), lines);
  }

  @Test
  void commitWithoutOtherGitDetailsShowsOnlyTheHash() {
    String unknown = BuildInfo.UNKNOWN;
    BuildInfo hashOnly =
        new BuildInfo(
            "1.1-SNAPSHOT",
            "142",
            "2026-11-12T14:03:22Z",
            "abc1234",
            unknown,
            unknown,
            unknown,
            false);

    List<String> lines = runAsPlayer(hashOnly);

    assertEquals(
        List.of(
            "IceCream 1.1-SNAPSHOT", "Build: 142, built 2026-11-12 14:03 UTC", "Commit: abc1234"),
        lines);
  }

  @Test
  void unknownBranchIsLeftOut() {
    BuildInfo detached =
        new BuildInfo(
            "1.1-SNAPSHOT",
            "142",
            "2026-11-12T14:03:22Z",
            "61e62e9a1b2c3d4e5f60718293a4b5c6d7e8f901",
            SUBJECT,
            "2026-11-10T21:14:03+00:00",
            BuildInfo.UNKNOWN,
            false);

    List<String> lines = runAsPlayer(detached);

    assertEquals("Commit: 61e62e9, 2026-11-10 21:14 UTC", lines.get(2));
    assertEquals(SUBJECT, lines.get(3));
  }

  @Test
  void subjectIsNotParsedAsMiniMessage() {
    List<String> lines = runAsPlayer(buildInfo("<red>boom</red>", false));

    assertEquals("<red>boom</red>", lines.get(3));
  }

  @Test
  void worksFromConsole() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    run(buildInfo(SUBJECT, false), console);

    assertEquals(
        "IceCream 1.1-SNAPSHOT",
        PlainTextComponentSerializer.plainText().serialize(console.nextComponentMessage()));
  }

  @Test
  void formatTimeFallsBackToRawValue() {
    assertEquals("not-a-time", InfoCommand.formatTime("not-a-time"));
    assertEquals("unknown", InfoCommand.formatTime("unknown"));
  }

  @Test
  void formatTimeFallsBackWhenTimestampCannotBeConverted() {
    // Parses, but moving it to UTC overflows the supported year range.
    String outOfRange = "+999999999-12-31T23:59:59-01:00";

    assertEquals(outOfRange, InfoCommand.formatTime(outOfRange));
  }

  @Test
  void formatTimeConvertsToUtc() {
    assertEquals("2026-11-10 21:14 UTC", InfoCommand.formatTime("2026-11-10T22:14:03+01:00"));
  }
}
