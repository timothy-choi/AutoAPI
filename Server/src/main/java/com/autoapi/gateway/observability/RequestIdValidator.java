package com.autoapi.gateway.observability;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates and resolves inbound request IDs. */
public final class RequestIdValidator {

  public static final int MAX_REQUEST_ID_LENGTH = 128;
  private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

  private RequestIdValidator() {}

  public static String resolve(List<String> headerValues) {
    if (headerValues != null) {
      for (String value : headerValues) {
        if (value == null) {
          continue;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        if (isValid(trimmed)) {
          return trimmed;
        }
      }
    }
    return generate();
  }

  public static boolean isValid(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return false;
    }
    if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
      return false;
    }
    return VALID_REQUEST_ID.matcher(requestId).matches();
  }

  public static String generate() {
    return "req-" + UUID.randomUUID();
  }

  public static String attemptId(String requestId, int attemptNumber) {
    return requestId + "-attempt-" + attemptNumber;
  }
}
