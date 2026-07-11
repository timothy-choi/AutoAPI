package com.autoapi.proxy;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;

/** Removes hop-by-hop headers and sanitizes forwarding headers for Phase 1. */
public final class HopByHopHeaders {

  static final Set<String> HOP_BY_HOP =
      Set.of(
          HttpHeaders.CONNECTION,
          "Keep-Alive",
          "Proxy-Authenticate",
          "Proxy-Authorization",
          HttpHeaders.TE,
          HttpHeaders.TRAILER,
          HttpHeaders.TRANSFER_ENCODING,
          HttpHeaders.UPGRADE);

  static final Set<String> SANITIZE_REQUEST =
      Set.of("X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto");

  private HopByHopHeaders() {}

  static void sanitizeClientRequestHeaders(HttpHeaders headers) {
    removeHopByHop(headers);
    SANITIZE_REQUEST.forEach(headers::remove);
  }

  static void sanitizeUpstreamResponseHeaders(HttpHeaders headers) {
    removeHopByHop(headers);
  }

  private static void removeHopByHop(HttpHeaders headers) {
    Set<String> connectionTokens = connectionHeaderNames(headers);
    HOP_BY_HOP.forEach(headers::remove);
    connectionTokens.forEach(name -> headers.remove(name));
  }

  private static Set<String> connectionHeaderNames(HttpHeaders headers) {
    Set<String> names = new java.util.LinkedHashSet<>();
    for (String value : headers.getOrEmpty(HttpHeaders.CONNECTION)) {
      for (String token : value.split(",")) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty()) {
          names.add(trimmed.toLowerCase(Locale.ROOT));
        }
      }
    }
    return names;
  }
}
