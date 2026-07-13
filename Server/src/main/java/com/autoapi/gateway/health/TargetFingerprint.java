package com.autoapi.gateway.health;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/**
 * Configuration fingerprint for reconciling gateway-local health state on activation. State is
 * preserved only when both target ID and normalized URL remain unchanged.
 */
public record TargetFingerprint(UUID targetId, String normalizedUrl) {

  public static TargetFingerprint of(UUID targetId, URI url) {
    return new TargetFingerprint(targetId, normalizeUrl(url));
  }

  static String normalizeUrl(URI uri) {
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    int effectivePort = port == -1 ? defaultPort(scheme) : port;
    return scheme + "://" + host + ":" + effectivePort;
  }

  private static int defaultPort(String scheme) {
    return "https".equals(scheme) ? 443 : 80;
  }
}
