package com.autoapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoapi.security")
public class ApiKeyPepperProperties {

  /** Minimum length for a production pepper (development/test may use shorter via explicit env). */
  public static final int MIN_PEPPER_LENGTH = 16;

  private String apiKeyPepper = "";

  public String apiKeyPepper() {
    return apiKeyPepper;
  }

  public void setApiKeyPepper(String apiKeyPepper) {
    this.apiKeyPepper = apiKeyPepper == null ? "" : apiKeyPepper;
  }
}
