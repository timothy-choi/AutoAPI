package com.autoapi.security;

import java.util.regex.Pattern;

/** Parses and validates {@code ak_live_<keyId>.<secret>} API keys. */
public record StructuredApiKey(String keyId, String secret) {

  private static final Pattern FORMAT =
      Pattern.compile("^ak_live_([A-Z0-9]{16})\\.([A-Za-z0-9_-]{43})$");

  public static StructuredApiKey parse(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new ApiKeyFormatException("API key token is blank");
    }
    String trimmed = rawToken.trim();
    var matcher = FORMAT.matcher(trimmed);
    if (!matcher.matches()) {
      throw new ApiKeyFormatException("API key token is malformed");
    }
    return new StructuredApiKey(matcher.group(1), matcher.group(2));
  }

  public String plaintextForm() {
    return "ak_live_" + keyId + "." + secret;
  }

  public static final class ApiKeyFormatException extends RuntimeException {
    ApiKeyFormatException(String message) {
      super(message);
    }
  }
}
