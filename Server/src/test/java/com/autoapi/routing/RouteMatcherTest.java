package com.autoapi.routing;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RouteMatcherTest {

  private RouteMatcher matcher;

  @BeforeEach
  void setUp() {
    RuntimeConfig config =
        new RuntimeConfig(
            new GatewayConfig("0.0.0.0", 8080),
            List.of(
                route("root", "api.local", "/", Set.of(HttpMethod.GET)),
                route("orders", "api.local", "/v1/orders", Set.of(HttpMethod.GET, HttpMethod.POST)),
                route("health", "api.local", "/v1/health", Set.of(HttpMethod.GET)),
                route("other-host", "other.local", "/v1", Set.of(HttpMethod.GET))));
    matcher = new RouteMatcher(config);
  }

  @Test
  void longestPrefixWins() {
    RouteMatchResult result = matcher.match("api.local", "/v1/orders/123", HttpMethod.GET);
    assertTrue(result.isMatched());
    assertEquals("orders", result.matchedRoute().orElseThrow().id());
  }

  @Test
  void segmentBoundaryPreventsPartialMatch() {
    RouteMatcher boundaryMatcher =
        new RouteMatcher(
            new RuntimeConfig(
                new GatewayConfig("0.0.0.0", 8080),
                List.of(
                    route(
                        "orders",
                        "api.local",
                        "/v1/orders",
                        Set.of(HttpMethod.GET, HttpMethod.POST)))));
    RouteMatchResult result = boundaryMatcher.match("api.local", "/v1/orders-old", HttpMethod.GET);
    assertFalse(result.isMatched());
    assertEquals(RouteNotFoundReason.NOT_FOUND, result.reason());
  }

  @Test
  void rootPrefixMatchesNestedPaths() {
    RouteMatchResult result = matcher.match("api.local", "/anything", HttpMethod.GET);
    assertTrue(result.isMatched());
    assertEquals("root", result.matchedRoute().orElseThrow().id());
  }

  @Test
  void methodNotAllowedIncludesAllowList() {
    RouteMatchResult result = matcher.match("api.local", "/v1/orders/1", HttpMethod.DELETE);
    assertEquals(RouteNotFoundReason.METHOD_NOT_ALLOWED, result.reason());
    assertTrue(result.allowedMethods().contains(HttpMethod.GET));
    assertTrue(result.allowedMethods().contains(HttpMethod.POST));
  }

  @Test
  void hostCaseInsensitive() {
    RouteMatchResult result = matcher.match("API.LOCAL:8080", "/v1/health", HttpMethod.GET);
    assertTrue(result.isMatched());
    assertEquals("health", result.matchedRoute().orElseThrow().id());
  }

  @Test
  void unknownHostNotFound() {
    RouteMatchResult result = matcher.match("missing.local", "/v1", HttpMethod.GET);
    assertEquals(RouteNotFoundReason.NOT_FOUND, result.reason());
  }

  @Test
  void methodAwareLongestPrefixAmongMethodMatches() {
    RouteMatcher methodAwareMatcher =
        new RouteMatcher(
            new RuntimeConfig(
                new GatewayConfig("0.0.0.0", 8080),
                List.of(
                    route("route-a", "api.autoapi.local", "/v1", Set.of(HttpMethod.POST)),
                    route("route-b", "api.autoapi.local", "/v1/orders", Set.of(HttpMethod.GET)))));
    RouteMatchResult result =
        methodAwareMatcher.match("api.autoapi.local", "/v1/orders/123", HttpMethod.POST);
    assertTrue(result.isMatched());
    assertEquals("route-a", result.matchedRoute().orElseThrow().id());
  }

  private static RouteConfig route(String id, String host, String prefix, Set<HttpMethod> methods) {
    return new RouteConfig(
        id, host, prefix, methods, new UpstreamConfig(URI.create("http://127.0.0.1:8080")));
  }
}
