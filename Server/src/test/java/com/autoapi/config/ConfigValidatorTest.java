package com.autoapi.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ConfigValidatorTest {

  private static RouteConfig route(
      String id, String host, String prefix, Set<HttpMethod> methods, String upstream) {
    return new RouteConfig(id, host, prefix, methods, new UpstreamConfig(URI.create(upstream)));
  }

  @Test
  void rejectsInvalidPort() {
    RuntimeConfig config =
        new RuntimeConfig(new GatewayConfig("0.0.0.0", 70000), List.of(validRoute()));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsNoRoutes() {
    RuntimeConfig config = new RuntimeConfig(new GatewayConfig("0.0.0.0", 8080), List.of());
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsDuplicateRouteIds() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(
                route("dup", "h", "/a", Set.of(HttpMethod.GET), "http://u:8080"),
                route("dup", "h", "/b", Set.of(HttpMethod.GET), "http://u:8080")));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsAmbiguousRoutes() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(
                route("a", "api.local", "/v1/orders", Set.of(HttpMethod.GET), "http://u:8080"),
                route(
                    "b",
                    "API.LOCAL",
                    "/v1/orders",
                    Set.of(HttpMethod.GET, HttpMethod.POST),
                    "http://u:8080")));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsInvalidPathPrefix() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(route("r", "h", "v1", Set.of(HttpMethod.GET), "http://u:8080")));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsUpstreamWithCredentials() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(route("r", "h", "/v1", Set.of(HttpMethod.GET), "http://user:pass@u:8080")));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  @Test
  void rejectsUnsupportedScheme() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(route("r", "h", "/v1", Set.of(HttpMethod.GET), "ftp://u:8080")));
    assertThrows(ConfigLoadException.class, () -> ConfigValidator.validate(config));
  }

  private RouteConfig validRoute() {
    return route("r", "h", "/v1", Set.of(HttpMethod.GET), "http://u:8080");
  }
}
