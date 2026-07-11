package com.autoapi.routing;

import com.autoapi.config.HostNormalizer;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

public final class RouteMatcher {

  private final RuntimeConfig config;

  public RouteMatcher(RuntimeConfig config) {
    this.config = config;
  }

  public RouteMatchResult match(String hostHeader, String path, HttpMethod method) {
    String normalizedHost = HostNormalizer.normalize(hostHeader);
    String requestPath = normalizeRequestPath(path);

    List<RouteConfig> hostPathCandidates = new ArrayList<>();
    for (RouteConfig route : config.routes()) {
      if (!HostNormalizer.normalize(route.host()).equals(normalizedHost)) {
        continue;
      }
      if (matchesPathPrefix(requestPath, route.pathPrefix())) {
        hostPathCandidates.add(route);
      }
    }

    if (hostPathCandidates.isEmpty()) {
      return RouteMatchResult.notFound();
    }

    hostPathCandidates.sort(Comparator.comparingInt(r -> -effectivePrefixLength(r.pathPrefix())));

    List<RouteConfig> methodMatches = new ArrayList<>();
    Set<HttpMethod> allowed = new LinkedHashSet<>();
    for (RouteConfig route : hostPathCandidates) {
      allowed.addAll(route.methods());
      if (route.methods().contains(method)) {
        methodMatches.add(route);
      }
    }

    if (methodMatches.isEmpty()) {
      return RouteMatchResult.methodNotAllowed(allowed);
    }

    methodMatches.sort(Comparator.comparingInt(r -> -effectivePrefixLength(r.pathPrefix())));

    int longest = effectivePrefixLength(methodMatches.getFirst().pathPrefix());
    List<RouteConfig> longestMatches = new ArrayList<>();
    for (RouteConfig route : methodMatches) {
      if (effectivePrefixLength(route.pathPrefix()) == longest) {
        longestMatches.add(route);
      }
    }

    if (longestMatches.size() > 1) {
      throw new IllegalStateException(
          "Ambiguous route match at runtime — configuration should have rejected this");
    }

    return RouteMatchResult.matched(longestMatches.getFirst());
  }

  static boolean matchesPathPrefix(String requestPath, String configuredPrefix) {
    String prefix = normalizeConfiguredPrefix(configuredPrefix);
    if ("/".equals(prefix)) {
      return requestPath.startsWith("/");
    }
    if (requestPath.equals(prefix)) {
      return true;
    }
    return requestPath.startsWith(prefix + "/");
  }

  static String normalizeRequestPath(String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  static String normalizeConfiguredPrefix(String prefix) {
    if ("/".equals(prefix)) {
      return "/";
    }
    return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  static int effectivePrefixLength(String prefix) {
    return normalizeConfiguredPrefix(prefix).length();
  }
}
