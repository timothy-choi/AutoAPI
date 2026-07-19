package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;

public final class ManagementTokenPepperValidator {

  public static final int MIN_PEPPER_LENGTH = 16;

  private ManagementTokenPepperValidator() {}

  public static void requireConfigured(String pepper) {
    if (pepper == null || pepper.length() < MIN_PEPPER_LENGTH) {
      throw new IllegalStateException(
          "Management token pepper must be configured with at least "
              + MIN_PEPPER_LENGTH
              + " characters (autoapi.management-auth.token.pepper)");
    }
  }

  public static void requireConfiguredOrDevBypass(
      String pepper, ManagementAuthProperties properties) {
    if (properties.security().allowUnauthenticatedDevelopment()) {
      return;
    }
    requireConfigured(pepper);
  }

  public static ControlPlaneException authenticationMisconfigured() {
    return ControlPlaneException.authenticationRequired(
        "Management authentication is misconfigured");
  }
}
