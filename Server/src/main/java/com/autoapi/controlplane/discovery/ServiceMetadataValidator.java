package com.autoapi.controlplane.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

public final class ServiceMetadataValidator {

  public static final int MAX_METADATA_ENTRIES = 16;
  public static final int MAX_KEY_LENGTH = 64;
  public static final int MAX_VALUE_LENGTH = 256;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ServiceMetadataValidator() {}

  public static String normalizeOrEmpty(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "{}";
    }
    if (metadata.size() > MAX_METADATA_ENTRIES) {
      throw new IllegalArgumentException(
          "metadata must contain at most " + MAX_METADATA_ENTRIES + " entries");
    }
    ObjectNode node = MAPPER.createObjectNode();
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      validateEntry(entry.getKey(), entry.getValue());
      node.put(entry.getKey(), entry.getValue());
    }
    return node.toString();
  }

  public static String normalizeJsonOrEmpty(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) {
      return "{}";
    }
    try {
      JsonNode node = MAPPER.readTree(metadataJson);
      if (!node.isObject()) {
        throw new IllegalArgumentException("metadata must be a JSON object");
      }
      ObjectNode normalized = MAPPER.createObjectNode();
      int count = 0;
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (++count > MAX_METADATA_ENTRIES) {
          throw new IllegalArgumentException(
              "metadata must contain at most " + MAX_METADATA_ENTRIES + " entries");
        }
        if (!field.getValue().isTextual()) {
          throw new IllegalArgumentException("metadata values must be strings");
        }
        validateEntry(field.getKey(), field.getValue().asText());
        normalized.put(field.getKey(), field.getValue().asText());
      }
      return normalized.toString();
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("metadata must be valid JSON", ex);
    }
  }

  private static void validateEntry(String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("metadata keys must not be blank");
    }
    if (key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("metadata key exceeds max length");
    }
    if (value == null) {
      throw new IllegalArgumentException("metadata values must not be null");
    }
    if (value.length() > MAX_VALUE_LENGTH) {
      throw new IllegalArgumentException("metadata value exceeds max length");
    }
    String lower = key.toLowerCase();
    if (lower.contains("secret")
        || lower.contains("password")
        || lower.contains("token")
        || lower.contains("credential")) {
      throw new IllegalArgumentException("metadata key is not allowed");
    }
  }
}
