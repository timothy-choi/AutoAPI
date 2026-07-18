package com.autoapi.gateway.observability;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes metric label values to bounded, safe strings. */
public final class MetricLabelNormalizer {

  private static final int MAX_LABEL_LENGTH = 64;
  private static final Pattern SAFE_LABEL = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");

  private MetricLabelNormalizer() {}

  public static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String trimmed = value.trim();
    if (trimmed.length() > MAX_LABEL_LENGTH) {
      trimmed = trimmed.substring(0, MAX_LABEL_LENGTH);
    }
    String lowered = trimmed.toLowerCase(Locale.ROOT);
    if (SAFE_LABEL.matcher(lowered).matches()) {
      return lowered;
    }
    return fallback;
  }

  public static String route(String routeId) {
    return normalize(routeId, "unknown");
  }

  public static String method(String method) {
    return normalize(method, "unknown");
  }

  public static String gateway(String gatewayId) {
    return normalize(gatewayId, "unknown");
  }

  public static String api(String apiId) {
    return normalize(apiId, "unknown");
  }

  public static String pool(String pool) {
    return normalize(pool, "unknown");
  }

  public static String reason(GatewayErrorType errorType) {
    return errorType == null ? GatewayErrorType.NONE.metricValue() : errorType.metricValue();
  }

  public static String result(String result) {
    return normalize(result, "unknown");
  }

  public static String policyType(String policyType) {
    return normalize(policyType, "unknown");
  }

  public static String breakerState(String state) {
    return normalize(state, "unknown");
  }

  public static String healthState(String state) {
    return normalize(state, "unknown");
  }

  public static String attemptNumber(int attemptNumber) {
    if (attemptNumber <= 0) {
      return "0";
    }
    if (attemptNumber >= 10) {
      return "10+";
    }
    return String.valueOf(attemptNumber);
  }
}
