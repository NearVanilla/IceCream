package com.nearvanilla.iceCream.modules.info.commands;

import com.nearvanilla.iceCream.modules.info.BuildInfo;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * InfoCommand prints the version, build and source commit of the running plugin, so a deployment
 * can be verified without checking the git history.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-12
 * @see BuildInfo
 */
public class InfoCommand {
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

  private static final String VERSION_LINE =
      "<gold><bold>IceCream</bold></gold> <white><version></white>";
  private static final String BUILD_LINE =
      "<gray>Build:</gray> <white><number></white><gray>, built <time></gray>";
  private static final String COMMIT_LINE =
      "<gray>Commit:</gray> <white><commit></white><red><dirty></red>";
  private static final String BRANCH_PART = " <gray>(<branch>)</gray>";
  private static final String COMMIT_TIME_PART = "<gray>, <time></gray>";
  private static final String SUBJECT_LINE = "<white><subject></white>";

  private final BuildInfo buildInfo;

  /**
   * Creates the command for the given build.
   *
   * @param buildInfo the build to describe
   */
  public InfoCommand(BuildInfo buildInfo) {
    this.buildInfo = buildInfo;
  }

  /**
   * The logic for the "icecream info" command, sending the build information to whoever executed
   * it. It works for players and the console alike.
   *
   * @param commandSourceStack The command source stack, containing a sender, executor and location,
   *     where applicable.
   */
  @Command("icecream info")
  @CommandDescription("Shows the version, build and commit of the running plugin.")
  @Permission("icecream.modules.info")
  @SuppressWarnings("unused")
  public void infoCommand(CommandSourceStack commandSourceStack) {
    CommandSender sender = commandSourceStack.getSender();
    sender.sendMessage(
        MINI_MESSAGE.deserialize(
            VERSION_LINE, Placeholder.unparsed("version", buildInfo.version())));
    sender.sendMessage(
        MINI_MESSAGE.deserialize(
            BUILD_LINE,
            Placeholder.unparsed("number", buildInfo.buildNumber()),
            Placeholder.unparsed("time", formatTime(buildInfo.buildTime()))));
    // Details the build could not determine are left out rather than shown as "unknown". Without
    // a commit, none of them mean anything.
    boolean hasCommit = buildInfo.hasCommit();
    String commitLine = COMMIT_LINE;
    if (hasCommit && isKnown(buildInfo.branch())) {
      commitLine += BRANCH_PART;
    }
    if (hasCommit && isKnown(buildInfo.commitTime())) {
      commitLine += COMMIT_TIME_PART;
    }
    sender.sendMessage(
        MINI_MESSAGE.deserialize(
            commitLine,
            Placeholder.unparsed(
                "commit", hasCommit ? buildInfo.shortCommitId() : BuildInfo.UNKNOWN),
            Placeholder.unparsed("dirty", buildInfo.dirty() ? "-dirty" : ""),
            Placeholder.unparsed("branch", buildInfo.branch()),
            Placeholder.unparsed("time", formatTime(buildInfo.commitTime()))));
    if (hasCommit && isKnown(buildInfo.commitSubject())) {
      sender.sendMessage(
          MINI_MESSAGE.deserialize(
              SUBJECT_LINE, Placeholder.unparsed("subject", buildInfo.commitSubject())));
    }
  }

  private static boolean isKnown(String value) {
    return !BuildInfo.UNKNOWN.equals(value);
  }

  /**
   * Formats an ISO-8601 timestamp as a UTC date and time to the minute.
   *
   * @param iso the timestamp to format
   * @return the formatted time, or the input unchanged when it is not a timestamp
   */
  static String formatTime(String iso) {
    try {
      return OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC).format(TIME_FORMAT);
    } catch (DateTimeException exception) {
      return iso;
    }
  }
}
