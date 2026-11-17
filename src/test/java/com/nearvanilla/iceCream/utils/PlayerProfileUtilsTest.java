package com.nearvanilla.iceCream.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PlayerProfileUtilsTest {
  private static final String SKIN_URL = "http://textures.minecraft.net/texture/a6cc486c2be1cb9d";
  private static final String WITH_TIMESTAMP =
      "{\"timestamp\":1700000000000,\"profileId\":\"0123456789abcdef0123456789abcdef\","
          + "\"profileName\":\"Steve\",\"textures\":{\"SKIN\":{\"url\":\""
          + SKIN_URL
          + "\"}}}";

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private PlayerProfile profileWithTextures(String json) {
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");
    profile.setProperty(new ProfileProperty("textures", encode(json)));
    return profile;
  }

  private String encode(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private List<ProfileProperty> textures(PlayerProfile profile) {
    return profile.getProperties().stream()
        .filter(property -> property.getName().equals("textures"))
        .toList();
  }

  private JsonNode decodedTextures(PlayerProfile profile) {
    List<ProfileProperty> textures = textures(profile);
    assertEquals(1, textures.size());
    return mapper.readTree(Base64.getDecoder().decode(textures.getFirst().getValue()));
  }

  @Test
  void removesTheTimestampFromTheTextures() {
    PlayerProfile profile = profileWithTextures(WITH_TIMESTAMP);

    PlayerProfileUtils.sanitizeTextures(profile);

    assertFalse(decodedTextures(profile).has("timestamp"));
  }

  @Test
  void keepsEveryOtherFieldOfTheTextures() {
    PlayerProfile profile = profileWithTextures(WITH_TIMESTAMP);

    PlayerProfileUtils.sanitizeTextures(profile);

    JsonNode decoded = decodedTextures(profile);
    assertEquals("0123456789abcdef0123456789abcdef", decoded.path("profileId").asString());
    assertEquals("Steve", decoded.path("profileName").asString());
    assertEquals(SKIN_URL, decoded.path("textures").path("SKIN").path("url").asString());
    assertEquals(3, decoded.size());
  }

  @Test
  void leavesATimestampNestedInsideTheTexturesAlone() {
    PlayerProfile profile =
        profileWithTextures(
            "{\"timestamp\":1,\"textures\":{\"SKIN\":{\"url\":\"http://x\"},\"timestamp\":2}}");

    PlayerProfileUtils.sanitizeTextures(profile);

    JsonNode decoded = decodedTextures(profile);
    assertFalse(decoded.has("timestamp"));
    assertEquals(2, decoded.path("textures").path("timestamp").asInt());
  }

  @Test
  void leavesTextureWithoutATimestampWithTheSameContent() {
    String withoutTimestamp =
        "{\"profileName\":\"Steve\",\"textures\":{\"SKIN\":{\"url\":\"http://x\"}}}";
    PlayerProfile profile = profileWithTextures(withoutTimestamp);

    PlayerProfileUtils.sanitizeTextures(profile);

    assertEquals(mapper.readTree(withoutTimestamp), decodedTextures(profile));
  }

  @Test
  void keepsASingleTexturesProperty() {
    PlayerProfile profile = profileWithTextures(WITH_TIMESTAMP);

    PlayerProfileUtils.sanitizeTextures(profile);

    assertEquals(1, textures(profile).size());
  }

  @Test
  void leavesOtherPropertiesAlone() {
    PlayerProfile profile = profileWithTextures(WITH_TIMESTAMP);
    profile.setProperty(new ProfileProperty("other", "value"));

    PlayerProfileUtils.sanitizeTextures(profile);

    List<ProfileProperty> others =
        profile.getProperties().stream()
            .filter(property -> property.getName().equals("other"))
            .toList();
    assertEquals(1, others.size());
    assertEquals("value", others.getFirst().getValue());
  }

  @Test
  void aProfileWithoutTexturesStaysWithoutAndLogsNothing() {
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");
    profile.setProperty(new ProfileProperty("other", "value"));

    try (LogCapture log = LogCapture.of(utilityLogger())) {
      PlayerProfileUtils.sanitizeTextures(profile);

      assertTrue(log.records().isEmpty());
    }

    assertTrue(textures(profile).isEmpty());
    assertEquals(1, profile.getProperties().size());
  }

  @Test
  void aProfileWithNoPropertiesStaysEmptyAndLogsNothing() {
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");

    try (LogCapture log = LogCapture.of(utilityLogger())) {
      PlayerProfileUtils.sanitizeTextures(profile);

      assertTrue(log.records().isEmpty());
    }

    assertTrue(profile.getProperties().isEmpty());
  }

  @Test
  void findsTheTexturesAmongOtherProperties() {
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");
    profile.setProperty(new ProfileProperty("a", "1"));
    profile.setProperty(new ProfileProperty("b", "2"));
    profile.setProperty(new ProfileProperty("c", "3"));
    profile.setProperty(new ProfileProperty("d", "4"));
    profile.setProperty(new ProfileProperty("textures", encode(WITH_TIMESTAMP)));

    try (LogCapture log = LogCapture.of(utilityLogger())) {
      PlayerProfileUtils.sanitizeTextures(profile);

      assertTrue(log.records().isEmpty());
    }

    assertFalse(decodedTextures(profile).has("timestamp"));
    assertEquals(5, profile.getProperties().size());
  }

  @Test
  void sanitizingTwiceGivesTheSameTextures() {
    PlayerProfile profile = profileWithTextures(WITH_TIMESTAMP);
    PlayerProfileUtils.sanitizeTextures(profile);
    String once = textures(profile).getFirst().getValue();

    PlayerProfileUtils.sanitizeTextures(profile);

    assertEquals(once, textures(profile).getFirst().getValue());
  }

  @Test
  void texturesThatAreNotBase64AreReportedAndNotThrown() {
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");
    profile.setProperty(new ProfileProperty("textures", "!!! not base64 !!!"));

    assertWarned(profile);
  }

  @Test
  void texturesThatAreNotJsonAreReportedAndNotThrown() {
    PlayerProfile profile = profileWithTextures("this is not json");

    assertWarned(profile);
  }

  // The utility logs to its own logger, not to the plugin's.
  private Logger utilityLogger() {
    return Logger.getLogger(PlayerProfileUtils.class.getName());
  }

  private void assertWarned(PlayerProfile profile) {
    try (LogCapture log = LogCapture.of(utilityLogger())) {
      PlayerProfileUtils.sanitizeTextures(profile);

      assertEquals(
          List.of("Failed to sanitize player profile textures; head will retain its timestamp"),
          log.messages(Level.WARNING));
      LogRecord record = log.records().getFirst();
      assertNotNull(record.getThrown());
      assertInstanceOf(Exception.class, record.getThrown());
    }
  }

  @Test
  void sanitizingValidTexturesLogsNothing() {
    try (LogCapture log = LogCapture.of(utilityLogger())) {
      PlayerProfileUtils.sanitizeTextures(profileWithTextures(WITH_TIMESTAMP));

      assertTrue(log.records().isEmpty());
    }
  }
}
