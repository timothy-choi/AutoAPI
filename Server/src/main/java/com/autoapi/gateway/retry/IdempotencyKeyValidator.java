package com.autoapi.gateway.retry;

/** Validates {@code Idempotency-Key} presence and shape without logging the value. */
public final class IdempotencyKeyValidator {

  public static final String HEADER = "Idempotency-Key";
  private static final int MAX_LENGTH = 255;

  private IdempotencyKeyValidator() {}

  public static boolean isValid(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (value.length() > MAX_LENGTH) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch < 32 || ch > 126) {
        return false;
      }
    }
    return true;
  }

  public static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
