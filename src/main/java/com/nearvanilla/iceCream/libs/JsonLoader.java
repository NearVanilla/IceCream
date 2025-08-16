package com.nearvanilla.iceCream.libs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class JsonLoader {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonLoader() {
    // Utility class; prevent instantiation.
  }

  /**
   * Loads a JSON file into a Java object.
   *
   * @param file The JSON file to load.
   * @param clazz The class to deserialize into.
   * @return The deserialized object.
   * @throws IOException If reading or parsing fails.
   */
  public static <T> T load(File file, Class<T> clazz) throws IOException {
    return MAPPER.readValue(file, clazz);
  }

  /**
   * Loads a JSON file into a complex type (like List, Map, etc).
   *
   * @param file The JSON file to load.
   * @param typeReference The type to deserialize into.
   * @return The deserialized object.
   * @throws IOException If reading or parsing fails.
   */
  public static <T> T load(File file, TypeReference<T> typeReference) throws IOException {
    return MAPPER.readValue(file, typeReference);
  }

  /**
   * Loads a JSON input stream into a Java object.
   *
   * @param inputStream The input stream containing JSON.
   * @param clazz The class to deserialize into.
   * @return The deserialized object.
   * @throws IOException If reading or parsing fails.
   */
  public static <T> T load(InputStream inputStream, Class<T> clazz) throws IOException {
    return MAPPER.readValue(inputStream, clazz);
  }

  /**
   * Loads a JSON input stream into a complex type (like List, Map, etc).
   *
   * @param inputStream The input stream containing JSON.
   * @param typeReference The type to deserialize into.
   * @return The deserialized object.
   * @throws IOException If reading or parsing fails.
   */
  public static <T> T load(InputStream inputStream, TypeReference<T> typeReference)
      throws IOException {
    return MAPPER.readValue(inputStream, typeReference);
  }
}
