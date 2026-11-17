package com.nearvanilla.iceCream.modules.playerSeen;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import com.nearvanilla.iceCream.testutil.PlayerSeenTestUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PlayerSeenUtilsTest {
  private static final Duration MINUTE = Duration.ofMinutes(1);
  private static final Duration HOUR = Duration.ofHours(1);
  private static final Duration DAY = Duration.ofDays(1);
  private static final Duration WEEK = Duration.ofDays(7);

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    PlayerSeenTestUtils.resetDateFormatter();
  }

  @AfterEach
  void tearDown() {
    PlayerSeenTestUtils.resetDateFormatter();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void config(String configYaml) {
    IceCreamFixture.install(server, configYaml);
  }

  // The moment the given time ago. The method under test reads the clock itself a few milliseconds
  // later, so each duration is chosen to sit at least a second inside the unit it should read as.
  private static long ago(Duration duration) {
    return System.currentTimeMillis() - duration.toMillis();
  }

  private static long localTime(int year, int month, int day, int hour, int minute, int second) {
    return LocalDateTime.of(year, month, day, hour, minute, second)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();
  }

  @Test
  void getMessageReturnsTheConfiguredMessage() {
    config("modules:\n  playerseen:\n    messages:\n      online: '<red>Here</red>'\n");

    assertEquals("<red>Here</red>", PlayerSeenUtils.getMessage("online", "fallback"));
  }

  @Test
  void getMessageReadsEachKeyFromItsOwnPath() {
    config("modules:\n  playerseen:\n    messages:\n      online: one\n      last-seen: two\n");

    assertEquals("one", PlayerSeenUtils.getMessage("online", "fallback"));
    assertEquals("two", PlayerSeenUtils.getMessage("last-seen", "fallback"));
  }

  @Test
  void getMessageFallsBackToTheDefault() {
    config("");

    assertEquals("fallback", PlayerSeenUtils.getMessage("online", "fallback"));
  }

  @Test
  void getMessageFallsBackToTheDefaultForAKeyThatIsNotConfigured() {
    config("modules:\n  playerseen:\n    messages:\n      online: one\n");

    assertEquals("fallback", PlayerSeenUtils.getMessage("never-played", "fallback"));
  }

  @Test
  void buildMessageParsesMiniMessage() {
    Component message = PlayerSeenUtils.buildMessage("<red>Stopped</red>");

    assertEquals("Stopped", plainText(message));
    assertEquals(NamedTextColor.RED, message.color());
  }

  @Test
  void buildMessageKeepsPlainText() {
    assertEquals("Just text", plainText(PlayerSeenUtils.buildMessage("Just text")));
  }

  @Test
  void buildMessageOfNothingIsEmpty() {
    assertEquals("", plainText(PlayerSeenUtils.buildMessage("")));
  }

  @Test
  void formatDateUsesTheDefaultFormat() {
    config("");

    assertEquals(
        "2024-03-05 14:07:09", PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9)));
  }

  @Test
  void formatDateUsesTheServersTimeZone() {
    config("");
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));

      assertEquals(
          "2024-03-05 13:00:00",
          PlayerSeenUtils.formatDate(Instant.parse("2024-03-05T00:00:00Z").toEpochMilli()));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  void formatDatePadsEveryField() {
    config("");

    assertEquals("2001-02-03 04:05:06", PlayerSeenUtils.formatDate(localTime(2001, 2, 3, 4, 5, 6)));
  }

  @Test
  void formatDateUsesTheConfiguredFormat() {
    config("modules:\n  playerseen:\n    date-format: 'dd/MM/yyyy HH:mm'\n");

    assertEquals("05/03/2024 14:07", PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9)));
  }

  @Test
  void formatDateFallsBackToTheDefaultFormatWhenTheConfiguredOneIsInvalid() {
    config("modules:\n  playerseen:\n    date-format: '{broken'\n");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      assertEquals(
          "2024-03-05 14:07:09", PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9)));

      assertEquals(
          List.of("Invalid date format for PlayerSeen module: {broken. Using the default instead."),
          log.messages(Level.WARNING));
    }
  }

  @Test
  void formatDateReportsAnInvalidFormatOnlyOnce() {
    config("modules:\n  playerseen:\n    date-format: '{broken'\n");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9));
      PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9));

      assertEquals(1, log.messages(Level.WARNING).size());
    }
  }

  @Test
  void formatDateStaysSilentForAValidFormat() {
    config("");

    try (LogCapture log = LogCapture.of(IceCream.logger)) {
      PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9));

      assertTrue(log.records().isEmpty());
    }
  }

  @Test
  void formatDateKeepsTheFormatItFirstBuilt() {
    config("modules:\n  playerseen:\n    date-format: 'yyyy'\n");
    assertEquals("2024", PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9)));

    config("modules:\n  playerseen:\n    date-format: 'MM'\n");

    assertEquals("2024", PlayerSeenUtils.formatDate(localTime(2024, 3, 5, 14, 7, 9)));
  }

  @Test
  void lastSeenJustNowIsGreen() {
    assertEquals("<green>just now", PlayerSeenUtils.formatLastSeenTime(ago(Duration.ZERO)));
  }

  @Test
  void lastSeenInTheFutureReadsAsJustNow() {
    assertEquals("<green>just now", PlayerSeenUtils.formatLastSeenTime(ago(Duration.ofHours(-3))));
  }

  @Test
  void lastSeenSecondsAgo() {
    assertEquals(
        "<green>30 seconds ago", PlayerSeenUtils.formatLastSeenTime(ago(Duration.ofSeconds(30))));
  }

  @Test
  void lastSeenOneSecondAgoIsSingular() {
    assertEquals(
        "<green>1 second ago", PlayerSeenUtils.formatLastSeenTime(ago(Duration.ofSeconds(1))));
  }

  @Test
  void lastSeenOneMinuteAgoIsSingular() {
    assertEquals("<green>1 minute ago", PlayerSeenUtils.formatLastSeenTime(ago(MINUTE)));
  }

  @Test
  void lastSeenWholeMinutesAgoHasNoSeconds() {
    assertEquals(
        "<green>5 minutes ago", PlayerSeenUtils.formatLastSeenTime(ago(MINUTE.multipliedBy(5))));
  }

  @Test
  void lastSeenMinutesAgoShowsSecondsWhenTheyAreTheSmallestUnit() {
    assertEquals(
        "<green>1 minute and 30 seconds ago",
        PlayerSeenUtils.formatLastSeenTime(ago(MINUTE.plusSeconds(30))));
  }

  @Test
  void lastSeenJustUnderAnHourAgoIsStillGreen() {
    assertEquals(
        "<green>59 minutes and 30 seconds ago",
        PlayerSeenUtils.formatLastSeenTime(ago(HOUR.minusSeconds(30))));
  }

  @Test
  void lastSeenAnHourAgoTurnsYellow() {
    assertEquals("<yellow>1 hour ago", PlayerSeenUtils.formatLastSeenTime(ago(HOUR)));
  }

  @Test
  void lastSeenHoursAndMinutesAgo() {
    assertEquals(
        "<yellow>2 hours and 30 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(
            ago(HOUR.multipliedBy(2).plus(MINUTE.multipliedBy(30)))));
  }

  @Test
  void lastSeenHoursAgoDropsTheSeconds() {
    assertEquals(
        "<yellow>2 hours and 30 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(
            ago(HOUR.multipliedBy(2).plus(MINUTE.multipliedBy(30)).plusSeconds(30))));
  }

  @Test
  void lastSeenWeeksAndMinutesAgoDropsTheSeconds() {
    assertEquals(
        "<red>1 week and 5 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(ago(WEEK.plus(MINUTE.multipliedBy(5)).plusSeconds(30))));
  }

  @Test
  void lastSeenDaysAndMinutesAgoDropsTheSeconds() {
    assertEquals(
        "<gold>1 day and 5 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(ago(DAY.plus(MINUTE.multipliedBy(5)).plusSeconds(30))));
  }

  @Test
  void lastSeenWholeHoursAgoDropsTheSecondsToo() {
    assertEquals(
        "<yellow>3 hours ago",
        PlayerSeenUtils.formatLastSeenTime(ago(HOUR.multipliedBy(3).plusSeconds(20))));
  }

  @Test
  void lastSeenJustUnderADayAgoIsStillYellow() {
    assertEquals(
        "<yellow>23 hours and 59 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(ago(DAY.minusSeconds(30))));
  }

  @Test
  void lastSeenADayAgoTurnsGold() {
    assertEquals("<gold>1 day ago", PlayerSeenUtils.formatLastSeenTime(ago(DAY)));
  }

  @Test
  void lastSeenDaysAgoListsEveryUnit() {
    assertEquals(
        "<gold>3 days, 4 hours and 5 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(
            ago(DAY.multipliedBy(3).plus(HOUR.multipliedBy(4)).plus(MINUTE.multipliedBy(5)))));
  }

  @Test
  void lastSeenDaysAndHoursAgo() {
    assertEquals(
        "<gold>2 days and 12 hours ago",
        PlayerSeenUtils.formatLastSeenTime(ago(DAY.multipliedBy(2).plus(HOUR.multipliedBy(12)))));
  }

  @Test
  void lastSeenJustUnderAWeekAgoIsStillGold() {
    assertEquals(
        "<gold>6 days, 23 hours and 59 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(ago(WEEK.minusSeconds(30))));
  }

  @Test
  void lastSeenAWeekAgoTurnsRed() {
    assertEquals("<red>1 week ago", PlayerSeenUtils.formatLastSeenTime(ago(WEEK)));
  }

  @Test
  void lastSeenWeeksAndDaysAgo() {
    assertEquals(
        "<red>2 weeks and 3 days ago",
        PlayerSeenUtils.formatLastSeenTime(ago(WEEK.multipliedBy(2).plus(DAY.multipliedBy(3)))));
  }

  @Test
  void lastSeenOneWeekAndOneDayAgoAreSingular() {
    assertEquals(
        "<red>1 week and 1 day ago", PlayerSeenUtils.formatLastSeenTime(ago(WEEK.plus(DAY))));
  }

  @Test
  void lastSeenLongAgoListsWeeksDaysHoursAndMinutes() {
    Duration elapsed =
        WEEK.multipliedBy(10)
            .plus(DAY.multipliedBy(6))
            .plus(HOUR.multipliedBy(23))
            .plus(MINUTE.multipliedBy(59));

    assertEquals(
        "<red>10 weeks, 6 days, 23 hours and 59 minutes ago",
        PlayerSeenUtils.formatLastSeenTime(ago(elapsed)));
  }

  @Test
  void lastSeenSingularUnitsInALongList() {
    Duration elapsed = DAY.plus(HOUR).plus(MINUTE);

    assertEquals(
        "<gold>1 day, 1 hour and 1 minute ago", PlayerSeenUtils.formatLastSeenTime(ago(elapsed)));
  }
}
