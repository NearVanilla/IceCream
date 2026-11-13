package com.nearvanilla.iceCream.modules.info;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Checks the file the Gradle build really generates against what {@link BuildInfo} reads, so a
 * renamed key or file fails the build instead of quietly showing "unknown" in game.
 */
class GeneratedBuildInfoTest {

  @Test
  void generatedFileHasExactlyTheKeysBuildInfoReads() throws IOException {
    Properties generated = new Properties();
    try (InputStream stream = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
      assertNotNull(stream, "The Gradle build did not put build-info.properties on the classpath");
      generated.load(stream);
    }

    assertEquals(BuildInfo.KEYS, generated.stringPropertyNames());
  }

  @Test
  void loadReadsTheGeneratedFile() {
    BuildInfo info = BuildInfo.load();

    // Gradle always knows these three, even when git is unavailable.
    assertNotEquals(BuildInfo.UNKNOWN, info.version());
    assertNotEquals(BuildInfo.UNKNOWN, info.buildNumber());
    assertDoesNotThrow(() -> OffsetDateTime.parse(info.buildTime()));
  }
}
