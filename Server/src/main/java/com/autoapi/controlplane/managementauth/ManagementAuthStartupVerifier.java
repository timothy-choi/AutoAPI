package com.autoapi.controlplane.managementauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.management-auth.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class ManagementAuthStartupVerifier implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ManagementAuthStartupVerifier.class);

  private final ManagementAuthProperties properties;
  private final ManagementTokenPepperSource pepperSource;

  public ManagementAuthStartupVerifier(
      ManagementAuthProperties properties, ManagementTokenPepperSource pepperSource) {
    this.properties = properties;
    this.pepperSource = pepperSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (properties.security().allowUnauthenticatedDevelopment()) {
      log.warn(
          "Management authentication development bypass is ENABLED"
              + " (autoapi.management-auth.security.allow-unauthenticated-development=true)");
    }
    if (properties.enabled()) {
      pepperSource.requireConfigured();
    }
    if (properties.bootstrap().enabled()
        && (properties.bootstrap().token() == null || properties.bootstrap().token().isBlank())
        && !properties.security().allowUnauthenticatedDevelopment()) {
      log.warn(
          "Management bootstrap is enabled but AUTOAPI_BOOTSTRAP_ADMIN_TOKEN is not configured");
    }
  }
}
