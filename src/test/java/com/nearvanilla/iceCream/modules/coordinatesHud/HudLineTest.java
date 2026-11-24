package com.nearvanilla.iceCream.modules.coordinatesHud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HudLineTest {
  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @ParameterizedTest
  @CsvSource({
    "0, S",
    "22.5, S",
    "23, SW",
    "45, SW",
    "90, W",
    "135, NW",
    "180, N",
    "225, NE",
    "270, E",
    "315, SE",
    "337.5, SE",
    "338, S",
    "360, S",
    "450, W",
    "-90, E",
    "-180, N",
    "-270, W",
    "-0.5, S",
    "-360, S"
  })
  void givesCompassDirectionForYaw(float yaw, String expected) {
    assertEquals(expected, HudLine.direction(yaw));
  }

  @ParameterizedTest
  @CsvSource({
    "0, 06:00",
    "1000, 07:00",
    "1500, 07:30",
    "6000, 12:00",
    "12000, 18:00",
    "18000, 00:00",
    "23999, 05:59",
    "24000, 06:00",
    "30000, 12:00"
  })
  void givesTimeOfDay(long worldTime, String expected) {
    assertEquals(expected, HudLine.clock(worldTime));
  }

  @Test
  void rendersPaperTweaksLineByDefault() {
    Component line = new HudLine(HudLine.DEFAULT_TEMPLATE).render(12, 64, -300, 180, 6000);

    assertEquals("XYZ: 12 64 -300   N      12:00", plain(line));
  }

  @Test
  void colorsLabelGoldAndCoordinatesWhite() {
    Component line = new HudLine(HudLine.DEFAULT_TEMPLATE).render(1, 2, 3, 0, 0);

    assertEquals(NamedTextColor.GOLD, line.color());
    Component coordinates = line.children().get(0);
    assertEquals(NamedTextColor.WHITE, coordinates.color());
    assertEquals("1 2 3", plain(coordinates));
  }

  @Test
  void padsDirectionToTwoCharacters() {
    HudLine line = new HudLine("[<direction>]");

    assertEquals("[ S]", plain(line.render(0, 0, 0, 0, 0)));
    assertEquals("[SW]", plain(line.render(0, 0, 0, 45, 0)));
  }

  @Test
  void usesCustomTemplate() {
    Component line = new HudLine("<x>/<y>/<z> <time>").render(-5, 70, 8, 0, 18000);

    assertEquals("-5/70/8 00:00", plain(line));
  }

  @Test
  void doesNotParseTagsInValues() {
    // Values are plain text, so they can never inject formatting into the template.
    Component line = new HudLine("<x>").render(7, 0, 0, 0, 0);

    assertEquals(Component.text("7"), line);
  }
}
