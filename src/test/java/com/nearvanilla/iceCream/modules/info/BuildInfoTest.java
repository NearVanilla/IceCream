package com.nearvanilla.iceCream.modules.info;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

  private static InputStream properties(String... lines) {
    return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.ISO_8859_1));
  }

  @Test
  void readsAllKeys() {
    BuildInfo info =
        BuildInfo.read(
            properties(
                "version=1.1-SNAPSHOT",
                "build.number=142",
                "build.time=2026-11-12T14:03:22Z",
                "git.commit.id=61e62e9a1b2c3d4e",
                "git.commit.subject=Hide spectators from tab completion.",
                "git.commit.time=2026-11-10T21:14:03+00:00",
                "git.branch=main",
                "git.dirty=true"));

    assertEquals("1.1-SNAPSHOT", info.version());
    assertEquals("142", info.buildNumber());
    assertEquals("2026-11-12T14:03:22Z", info.buildTime());
    assertEquals("61e62e9a1b2c3d4e", info.commitId());
    assertEquals("Hide spectators from tab completion.", info.commitSubject());
    assertEquals("2026-11-10T21:14:03+00:00", info.commitTime());
    assertEquals("main", info.branch());
    assertTrue(info.dirty());
    assertTrue(info.hasCommit());
    assertEquals("61e62e9", info.shortCommitId());
  }

  @Test
  void missingKeysAreUnknown() {
    BuildInfo info = BuildInfo.read(properties("version=1.1-SNAPSHOT"));

    assertEquals("1.1-SNAPSHOT", info.version());
    assertEquals(BuildInfo.UNKNOWN, info.buildNumber());
    assertEquals(BuildInfo.UNKNOWN, info.buildTime());
    assertEquals(BuildInfo.UNKNOWN, info.commitId());
    assertEquals(BuildInfo.UNKNOWN, info.commitSubject());
    assertEquals(BuildInfo.UNKNOWN, info.commitTime());
    assertEquals(BuildInfo.UNKNOWN, info.branch());
    assertFalse(info.dirty());
    assertFalse(info.hasCommit());
  }

  @Test
  void blankValuesAreUnknown() {
    BuildInfo info = BuildInfo.read(properties("git.branch=", "build.number=   "));

    assertEquals(BuildInfo.UNKNOWN, info.branch());
    assertEquals(BuildInfo.UNKNOWN, info.buildNumber());
  }

  @Test
  void missingFileIsUnknown() {
    BuildInfo info = BuildInfo.read(null);

    assertEquals(BuildInfo.UNKNOWN, info.version());
    assertFalse(info.hasCommit());
  }

  @Test
  void shortCommitIdHandlesShortIds() {
    BuildInfo info = BuildInfo.read(properties("git.commit.id=abc"));

    assertEquals("abc", info.shortCommitId());
  }
}
