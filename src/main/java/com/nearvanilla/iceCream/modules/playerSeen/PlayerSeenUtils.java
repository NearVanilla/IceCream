package com.nearvanilla.iceCream.modules.playerSeen;

import com.nearvanilla.iceCream.IceCream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class PlayerSeenUtils {

  private static final long SECOND_MILLIS = 1000L;
  private static final long MINUTE_MILLIS = 60L * SECOND_MILLIS;
  private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
  private static final long DAY_MILLIS = 24L * HOUR_MILLIS;
  private static final long WEEK_MILLIS = 7L * DAY_MILLIS;

  private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  private static DateTimeFormatter dateFormatter;

  public static String getMessage(String key, String defaultMessage) {
    return IceCream.config.getString("modules.playerseen.messages." + key, defaultMessage);
  }

  public static Component buildMessage(String miniMessage) {
    return MiniMessage.miniMessage().deserialize(miniMessage);
  }

  public static String formatDate(long timestamp) {
    if (dateFormatter == null) {
      String pattern =
          IceCream.config.getString("modules.playerseen.date-format", DEFAULT_DATE_FORMAT);
      try {
        dateFormatter = DateTimeFormatter.ofPattern(pattern);
      } catch (IllegalArgumentException e) {
        IceCream.logger.warning(
            "Invalid date format for PlayerSeen module: "
                + pattern
                + ". Using the default instead.");
        dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
      }
    }
    LocalDateTime dateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return dateTime.format(dateFormatter);
  }

  /**
   * Formats how long ago the given timestamp was, prefixed with a colour tag: green under an hour,
   * yellow under a day, gold under a week and red beyond that.
   */
  public static String formatLastSeenTime(long lastSeen) {
    long difference = System.currentTimeMillis() - lastSeen;

    long seconds = (difference / SECOND_MILLIS) % 60;
    long minutes = (difference / MINUTE_MILLIS) % 60;
    long hours = (difference / HOUR_MILLIS) % 24;
    long days = (difference / DAY_MILLIS) % 7;
    long weeks = difference / WEEK_MILLIS;

    String color;
    if (difference < HOUR_MILLIS) {
      color = "<green>";
    } else if (difference < DAY_MILLIS) {
      color = "<yellow>";
    } else if (difference < WEEK_MILLIS) {
      color = "<gold>";
    } else {
      color = "<red>";
    }

    List<String> parts = new ArrayList<>();
    if (weeks > 0) {
      parts.add(weeks + (weeks == 1 ? " week" : " weeks"));
    }
    if (days > 0) {
      parts.add(days + (days == 1 ? " day" : " days"));
    }
    if (hours > 0) {
      parts.add(hours + (hours == 1 ? " hour" : " hours"));
    }
    if (minutes > 0) {
      parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
    }
    // Seconds only show when they are the smallest meaningful unit.
    if (seconds > 0
        && ((minutes > 0 && weeks == 0 && days == 0 && hours == 0) || parts.isEmpty())) {
      parts.add(seconds + (seconds == 1 ? " second" : " seconds"));
    }

    if (parts.isEmpty()) {
      return color + "just now";
    }

    StringBuilder timeBuilder = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      timeBuilder.append(parts.get(i));
      if (i < parts.size() - 2) {
        timeBuilder.append(", ");
      } else if (i == parts.size() - 2) {
        timeBuilder.append(" and ");
      }
    }
    timeBuilder.append(" ago");

    return color + timeBuilder;
  }
}
