package com.autoapi.controlplane.managementauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ManagementScopeCodec {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private ManagementScopeCodec() {}

  public static String encode(Set<String> scopes, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(scopes == null ? List.of() : scopes);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to encode scopes", e);
    }
  }

  public static Set<String> decode(String json, ObjectMapper objectMapper) {
    if (json == null || json.isBlank()) {
      return Set.of();
    }
    try {
      List<String> values = objectMapper.readValue(json, STRING_LIST);
      return Set.copyOf(new LinkedHashSet<>(values));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to decode scopes", e);
    }
  }

  public static Set<ManagementPermission> decodePermissions(
      String json, ObjectMapper objectMapper) {
    return ManagementPermission.parseAll(decode(json, objectMapper));
  }
}
