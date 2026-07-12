package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.regex.Pattern;

public final class GatewayIdValidator {

  private static final Pattern GATEWAY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");

  private GatewayIdValidator() {}

  public static void validate(String gatewayId) {
    if (gatewayId == null || gatewayId.isBlank()) {
      throw ControlPlaneException.invalidGatewayId("Gateway ID must not be blank");
    }
    if (!GATEWAY_ID_PATTERN.matcher(gatewayId).matches()) {
      throw ControlPlaneException.invalidGatewayId(
          "Gateway ID must match [a-zA-Z0-9._-] and be at most 128 characters");
    }
  }
}
