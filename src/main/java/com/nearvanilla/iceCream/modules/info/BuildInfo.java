package com.nearvanilla.iceCream.modules.info;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * Describes the build the running JAR came from. The values are written into {@code
 * build-info.properties} by the Gradle build, so they always describe the JAR itself rather than
 * the server it runs on.
 *
 * @param version the plugin version
 * @param buildNumber the CI run number, or {@code local} for a build made outside CI
 * @param buildTime when the JAR was built, as an ISO-8601 timestamp
 * @param commitId the full hash of the commit the JAR was built from
 * @param commitSubject the first line of that commit's message
 * @param commitTime when that commit was made, as an ISO-8601 timestamp
 * @param branch the branch the JAR was built from
 * @param dirty whether the working tree had uncommitted changes at build time
 * @author 105hua
 * @version 1.0
 * @since 2026-11-12
 */
public record BuildInfo(
    String version,
    String buildNumber,
    String buildTime,
    String commitId,
    String commitSubject,
    String commitTime,
    String branch,
    boolean dirty) {

  /** The value of any field the build could not determine. */
  public static final String UNKNOWN = "unknown";

  private static final String RESOURCE = "/build-info.properties";
  private static final int SHORT_COMMIT_LENGTH = 7;

  private static final String VERSION_KEY = "version";
  private static final String BUILD_NUMBER_KEY = "build.number";
  private static final String BUILD_TIME_KEY = "build.time";
  private static final String COMMIT_ID_KEY = "git.commit.id";
  private static final String COMMIT_SUBJECT_KEY = "git.commit.subject";
  private static final String COMMIT_TIME_KEY = "git.commit.time";
  private static final String BRANCH_KEY = "git.branch";
  private static final String DIRTY_KEY = "git.dirty";

  /** Every key read from the properties file, which the Gradle build must write. */
  static final Set<String> KEYS =
      Set.of(
          VERSION_KEY,
          BUILD_NUMBER_KEY,
          BUILD_TIME_KEY,
          COMMIT_ID_KEY,
          COMMIT_SUBJECT_KEY,
          COMMIT_TIME_KEY,
          BRANCH_KEY,
          DIRTY_KEY);

  /**
   * Loads the build information bundled in the JAR.
   *
   * @return the build information, with unknown fields when the file is missing
   */
  public static BuildInfo load() {
    try (InputStream stream = BuildInfo.class.getResourceAsStream(RESOURCE)) {
      return read(stream);
    } catch (IOException exception) {
      // Only closing the stream can fail here, and read has already fallen back by then.
      return read(null);
    }
  }

  /**
   * Reads build information from a properties stream.
   *
   * @param stream the properties to read, or null when there are none
   * @return the build information, with unknown fields for missing or blank values
   */
  static BuildInfo read(InputStream stream) {
    Properties properties = new Properties();
    if (stream != null) {
      try {
        properties.load(stream);
      } catch (IOException | IllegalArgumentException exception) {
        // A file that cannot be read is treated like a missing one.
        properties.clear();
      }
    }
    return new BuildInfo(
        value(properties, VERSION_KEY),
        value(properties, BUILD_NUMBER_KEY),
        value(properties, BUILD_TIME_KEY),
        value(properties, COMMIT_ID_KEY),
        value(properties, COMMIT_SUBJECT_KEY),
        value(properties, COMMIT_TIME_KEY),
        value(properties, BRANCH_KEY),
        Boolean.parseBoolean(properties.getProperty(DIRTY_KEY, "").trim()));
  }

  private static String value(Properties properties, String key) {
    String value = properties.getProperty(key);
    return value == null || value.isBlank() ? UNKNOWN : value.trim();
  }

  /**
   * Whether the commit the JAR was built from is known.
   *
   * @return true if the commit is known, false otherwise
   */
  public boolean hasCommit() {
    return !UNKNOWN.equals(commitId);
  }

  /**
   * The abbreviated commit hash, as shown by {@code git log --oneline}.
   *
   * @return the first seven characters of the commit hash, or all of it when shorter
   */
  public String shortCommitId() {
    return commitId.length() <= SHORT_COMMIT_LENGTH
        ? commitId
        : commitId.substring(0, SHORT_COMMIT_LENGTH);
  }
}
