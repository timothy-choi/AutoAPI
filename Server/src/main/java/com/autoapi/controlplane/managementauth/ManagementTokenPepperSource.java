package com.autoapi.controlplane.managementauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ManagementTokenPepperSource {

  private final ManagementAuthProperties properties;
  private final Environment environment;

  public ManagementTokenPepperSource(ManagementAuthProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  public String pepper() {
    String configured = properties.token().pepper();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    String fromEnv = environment.getProperty("AUTOAPI_MANAGEMENT_TOKEN_PEPPER");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return environment.getProperty("autoapi.management-auth.token.pepper", "");
  }

  public void requireConfigured() {
    ManagementTokenPepperValidator.requireConfiguredOrDevBypass(pepper(), properties);
  }
}
