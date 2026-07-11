package com.autoapi.config;

import java.util.List;

public record RuntimeConfig(GatewayConfig gateway, List<RouteConfig> routes) {

  public RuntimeConfig {
    gateway = gateway;
    routes = List.copyOf(routes);
  }
}
