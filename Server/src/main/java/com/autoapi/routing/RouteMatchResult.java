package com.autoapi.routing;

import com.autoapi.config.RouteConfig;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;

public record RouteMatchResult(
    RouteNotFoundReason reason,
    Optional<RouteConfig> matchedRoute,
    Set<HttpMethod> allowedMethods) {

  public static RouteMatchResult matched(RouteConfig route) {
    return new RouteMatchResult(null, Optional.of(route), Set.of());
  }

  public static RouteMatchResult notFound() {
    return new RouteMatchResult(RouteNotFoundReason.NOT_FOUND, Optional.empty(), Set.of());
  }

  public static RouteMatchResult methodNotAllowed(Set<HttpMethod> allowed) {
    return new RouteMatchResult(
        RouteNotFoundReason.METHOD_NOT_ALLOWED, Optional.empty(), Set.copyOf(allowed));
  }

  public boolean isMatched() {
    return matchedRoute.isPresent();
  }
}
