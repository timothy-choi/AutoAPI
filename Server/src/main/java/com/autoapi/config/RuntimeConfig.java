package com.autoapi.config;

import java.util.List;
import java.util.Map;

public record RuntimeConfig(
    GatewayConfig gateway, List<RouteConfig> routes, Map<String, RuntimeApiKey> apiKeysByKeyId) {

  public RuntimeConfig(GatewayConfig gateway, List<RouteConfig> routes) {
    this(gateway, routes, Map.of());
  }

  public RuntimeConfig {
    gateway = gateway;
    routes = List.copyOf(routes);
    apiKeysByKeyId =
        apiKeysByKeyId == null || apiKeysByKeyId.isEmpty() ? Map.of() : Map.copyOf(apiKeysByKeyId);
  }
}
