package com.nearvanilla.iceCream.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utilities for working with Paper {@link PlayerProfile} instances.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-06-12
 */
public final class PlayerProfileUtils {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = Logger.getLogger(PlayerProfileUtils.class.getName());
  private static final String TEXTURES_PROPERTY = "textures";
  private static final String TIMESTAMP_FIELD = "timestamp";

  private PlayerProfileUtils() {
    // Utility class; prevent instantiation.
  }

  /**
   * Strips the {@code timestamp} field from the player's {@code textures} Property so that the
   * dropped head's skin does not change if the player's skin is regenerated later. The sanitized
   * texture is re-applied to the profile; if sanitization fails for any reason the original profile
   * is left in an unsanitized state.
   *
   * <p>Adapted from {@code PTUtils.sanitizeTextures} in PaperTweaks by Machine_Maker (GPL-3.0).
   * Source:
   * [github.com/MC-Machinations/PaperTweaks](https://github.com/MC-Machinations/PaperTweaks).
   * Adaptations: replaced Gson with Jackson 3.x (already a project dependency); wrapped parsing in
   * a try/catch that logs a warning on failure rather than propagating the checked exception.
   *
   * @param profile the profile whose textures should be sanitized
   */
  public static void sanitizeTextures(PlayerProfile profile) {
    ProfileProperty textures =
        profile.getProperties().stream()
            .filter(property -> TEXTURES_PROPERTY.equals(property.getName()))
            .findFirst()
            .orElse(null);
    profile.removeProperty(TEXTURES_PROPERTY);
    if (textures == null) {
      return;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(textures.getValue());
      JsonNode node = MAPPER.readTree(decoded);
      if (node instanceof ObjectNode object) {
        object.remove(TIMESTAMP_FIELD);
        byte[] encoded = MAPPER.writeValueAsBytes(object);
        profile.setProperty(
            new ProfileProperty(TEXTURES_PROPERTY, Base64.getEncoder().encodeToString(encoded)));
      }
    } catch (Exception e) {
      LOGGER.log(
          Level.WARNING,
          "Failed to sanitize player profile textures; head will retain its timestamp",
          e);
    }
  }
}
