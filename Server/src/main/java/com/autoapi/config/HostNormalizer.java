package com.autoapi.config;

import java.util.Locale;

/** Normalizes HTTP Host header values for route matching. */
public final class HostNormalizer {

  private HostNormalizer() {}

  /**
   * Normalizes host for comparison: lowercase, port stripped. IPv6 bracket form {@code [::1]:8080}
   * is supported. Bare IPv6 without brackets is not supported in Phase 1.
   */
  public static String normalize(String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) {
      return "";
    }
    String trimmed = hostHeader.trim();
    if (trimmed.startsWith("[")) {
      int end = trimmed.indexOf(']');
      if (end <= 0) {
        throw new ConfigLoadException("Invalid IPv6 host header: " + hostHeader);
      }
      return trimmed.substring(1, end).toLowerCase(Locale.ROOT);
    }
    int colon = trimmed.lastIndexOf(':');
    if (colon > 0 && trimmed.indexOf(':') == colon) {
      return trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }
}
