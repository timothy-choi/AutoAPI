package com.autoapi.controlplane.discovery;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ServiceInstanceValidation {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final Pattern HOST_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,253}[a-zA-Z0-9]$|^[a-zA-Z0-9]$");
  private static final Pattern INSTANCE_ID_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");

  private ServiceInstanceValidation() {}

  public static void validateInstanceId(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId is required");
    }
    if (!INSTANCE_ID_PATTERN.matcher(instanceId).matches()) {
      throw new IllegalArgumentException("instanceId format is invalid");
    }
  }

  public static void validateHost(String host) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("host is required");
    }
    if (host.length() > 255) {
      throw new IllegalArgumentException("host exceeds max length");
    }
    if (!HOST_PATTERN.matcher(host).matches()) {
      throw new IllegalArgumentException("host format is invalid");
    }
  }

  public static void validatePort(int port) {
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
  }

  public static String normalizeScheme(String scheme, String defaultScheme) {
    String effective = scheme == null || scheme.isBlank() ? defaultScheme : scheme;
    String normalized = effective.toLowerCase(Locale.ROOT);
    if (!ALLOWED_SCHEMES.contains(normalized)) {
      throw new IllegalArgumentException("scheme must be http or https");
    }
    return normalized;
  }

  public static void validateWeight(int weight) {
    if (weight < 1 || weight > 10000) {
      throw new IllegalArgumentException("weight must be between 1 and 10000");
    }
  }

  public static void validateLeaseDurationSeconds(
      int leaseDurationSeconds, DiscoveryProperties properties) {
    if (leaseDurationSeconds <= 0) {
      throw new IllegalArgumentException("leaseDurationSeconds must be positive");
    }
    long seconds = leaseDurationSeconds;
    if (seconds < properties.minLeaseDuration().getSeconds()) {
      throw new IllegalArgumentException("leaseDurationSeconds is below minimum");
    }
    if (seconds > properties.maxLeaseDuration().getSeconds()) {
      throw new IllegalArgumentException("leaseDurationSeconds exceeds maximum");
    }
  }

  public static void validateOptionalLabel(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return;
    }
    if (value.length() > 64) {
      throw new IllegalArgumentException(fieldName + " exceeds max length");
    }
  }
}
