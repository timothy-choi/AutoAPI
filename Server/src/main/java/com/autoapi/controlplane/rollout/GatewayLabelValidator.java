package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates bounded gateway and group label maps. */
public final class GatewayLabelValidator {

  private static final Pattern LABEL_KEY = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");
  private static final Pattern LABEL_VALUE = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");
  private static final Pattern SYSTEM_PREFIX = Pattern.compile("^autoapi\\.");

  private GatewayLabelValidator() {}

  public static Map<String, String> validateLabels(
      Map<String, String> labels, RolloutsProperties.Labels limits) {
    if (labels == null || labels.isEmpty()) {
      return Map.of();
    }
    if (labels.size() > limits.maxLabels()) {
      throw ControlPlaneException.invalidRequest(
          "labels exceed maximum count of " + limits.maxLabels());
    }
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : labels.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isBlank()) {
        throw ControlPlaneException.invalidRequest("label key must not be blank");
      }
      if (key.length() > limits.maxKeyLength()) {
        throw ControlPlaneException.invalidRequest("label key exceeds maximum length");
      }
      if (value == null || value.isBlank()) {
        throw ControlPlaneException.invalidRequest("label value must not be blank");
      }
      if (value.length() > limits.maxValueLength()) {
        throw ControlPlaneException.invalidRequest("label value exceeds maximum length");
      }
      if (!LABEL_KEY.matcher(key).matches()) {
        throw ControlPlaneException.invalidRequest("invalid label key: " + key);
      }
      if (!LABEL_VALUE.matcher(value).matches()) {
        throw ControlPlaneException.invalidRequest("invalid label value for key: " + key);
      }
      if (SYSTEM_PREFIX.matcher(key).matches()) {
        throw ControlPlaneException.invalidRequest("system label namespace is reserved: " + key);
      }
      if (looksLikeSecret(key, value)) {
        throw ControlPlaneException.invalidRequest("labels must not contain secrets");
      }
      normalized.put(key, value);
    }
    return Map.copyOf(normalized);
  }

  public static Map<String, String> mergeGatewayLabels(
      Map<String, String> adminLabels, Map<String, String> gatewaySupplied) {
    Map<String, String> merged = new LinkedHashMap<>();
    if (adminLabels != null) {
      merged.putAll(adminLabels);
    }
    if (gatewaySupplied != null) {
      for (Map.Entry<String, String> entry : gatewaySupplied.entrySet()) {
        if (SYSTEM_PREFIX.matcher(entry.getKey()).matches()) {
          continue;
        }
        if (!merged.containsKey(entry.getKey())) {
          merged.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return Map.copyOf(merged);
  }

  private static boolean looksLikeSecret(String key, String value) {
    String lowerKey = key.toLowerCase();
    if (lowerKey.contains("secret")
        || lowerKey.contains("password")
        || lowerKey.contains("token")
        || lowerKey.contains("credential")
        || lowerKey.contains("authorization")) {
      return true;
    }
    return value.startsWith("ak_live_") || value.startsWith("sr_live_");
  }
}
