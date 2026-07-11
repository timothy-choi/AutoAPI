package com.autoapi.web;

/** Reserved AutoAPI path namespaces that must never be proxied to configured upstreams. */
final class GatewayReservedPaths {

  static final String MANAGEMENT_API_PREFIX = "/api/v1";

  private GatewayReservedPaths() {}

  static boolean isReservedPath(String path) {
    return "/healthz".equals(path)
        || "/readyz".equals(path)
        || MANAGEMENT_API_PREFIX.equals(path)
        || path.startsWith(MANAGEMENT_API_PREFIX + "/");
  }
}
