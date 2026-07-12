package com.autoapi.security;

public final class ApiKeyPepperValidator {

  private ApiKeyPepperValidator() {}

  public static void requireConfigured(String pepper) {
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalStateException(
          "autoapi.security.api-key-pepper (AUTOAPI_API_KEY_PEPPER) is required when API key"
              + " authentication is enabled");
    }
    if (pepper.length() < ApiKeyPepperProperties.MIN_PEPPER_LENGTH) {
      throw new IllegalStateException(
          "autoapi.security.api-key-pepper must be at least "
              + ApiKeyPepperProperties.MIN_PEPPER_LENGTH
              + " characters");
    }
  }
}
