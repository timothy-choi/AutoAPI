package com.autoapi.config;

import org.springframework.http.HttpMethod;

public final class ConfigValidator {

  private ConfigValidator() {}

  public static void validate(RuntimeConfig config) {
    validateGateway(config.gateway());
    if (config.routes().isEmpty()) {
      throw new ConfigLoadException("At least one route must be configured");
    }
    validateRouteUniquenessAndAmbiguity(config.routes());
    for (RouteConfig route : config.routes()) {
      validateRoute(route);
    }
  }

  private static void validateGateway(GatewayConfig gateway) {
    if (gateway.port() < 1 || gateway.port() > 65535) {
      throw new ConfigLoadException("Gateway port must be between 1 and 65535");
    }
    if (gateway.listenAddress() == null || gateway.listenAddress().isBlank()) {
      throw new ConfigLoadException("Gateway listenAddress must not be blank");
    }
  }

  private static void validateRoute(RouteConfig route) {
    if (route.id().isBlank()) {
      throw new ConfigLoadException("Route ID must not be blank");
    }
    if (route.host().isBlank()) {
      throw new ConfigLoadException("Route host must not be blank for route: " + route.id());
    }
    HostNormalizer.normalize(route.host()); // validates parseable host literal

    String prefix = route.pathPrefix();
    if (prefix.isBlank()) {
      throw new ConfigLoadException("Route pathPrefix must not be blank for route: " + route.id());
    }
    if (!prefix.startsWith("/")) {
      throw new ConfigLoadException(
          "Route pathPrefix must begin with '/' for route: " + route.id());
    }

    if (route.methods().isEmpty()) {
      throw new ConfigLoadException("Route methods must not be empty for route: " + route.id());
    }

    URIValidator.validateUpstream(route.upstream().url(), route.id());
  }

  private static void validateRouteUniquenessAndAmbiguity(java.util.List<RouteConfig> routes) {
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (RouteConfig route : routes) {
      if (!ids.add(route.id())) {
        throw new ConfigLoadException("Duplicate route ID: " + route.id());
      }
    }

    for (int i = 0; i < routes.size(); i++) {
      RouteConfig a = routes.get(i);
      String hostA = HostNormalizer.normalize(a.host());
      String prefixA = normalizePrefixForValidation(a.pathPrefix());
      for (int j = i + 1; j < routes.size(); j++) {
        RouteConfig b = routes.get(j);
        String hostB = HostNormalizer.normalize(b.host());
        String prefixB = normalizePrefixForValidation(b.pathPrefix());
        if (hostA.equals(hostB) && prefixA.equals(prefixB)) {
          for (HttpMethod method : a.methods()) {
            if (b.methods().contains(method)) {
              throw new ConfigLoadException(
                  "Ambiguous routes '"
                      + a.id()
                      + "' and '"
                      + b.id()
                      + "' for host "
                      + hostA
                      + ", path prefix "
                      + prefixA
                      + ", method "
                      + method.name());
            }
          }
        }
      }
    }
  }

  private static String normalizePrefixForValidation(String prefix) {
    if ("/".equals(prefix)) {
      return "/";
    }
    return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }
}
