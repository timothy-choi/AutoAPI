package com.autoapi.controlplane.configversion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical JSON serialization and SHA-256 hashing for compiled runtime payloads. */
public final class RuntimeContentHasher {

  private static final ObjectMapper CANONICAL_MAPPER =
      JsonMapper.builder()
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .build();

  private RuntimeContentHasher() {}

  public static String canonicalJson(Object payload) {
    try {
      return CANONICAL_MAPPER.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize canonical runtime payload", e);
    }
  }

  public static String sha256Hex(String canonicalJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public static ObjectMapper canonicalMapper() {
    return CANONICAL_MAPPER;
  }
}
