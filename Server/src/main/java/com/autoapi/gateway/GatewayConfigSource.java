package com.autoapi.gateway;

public enum GatewayConfigSource {
  STATIC,
  CONTROL_PLANE;

  public static GatewayConfigSource parse(String value) {
    if (value == null || value.isBlank()) {
      return STATIC;
    }
    return GatewayConfigSource.valueOf(value.trim().toUpperCase().replace('-', '_'));
  }
}
