package com.autoapi.proxy;

public final class GatewayAttributes {

  public static final String RUNTIME_CONFIG = "autoapi.runtime.config";
  public static final String ACTIVE_RUNTIME_BUNDLE = "autoapi.active.runtime.bundle";
  public static final String REQUEST_ID = "autoapi.request.id";
  public static final String MATCHED_ROUTE_ID = "autoapi.matched.route.id";
  public static final String UPSTREAM_AUTHORITY = "autoapi.upstream.authority";
  public static final String SELECTED_TARGET_ID = "autoapi.selected.target.id";
  public static final String UPSTREAM_ATTEMPTS = "autoapi.upstream.attempts";
  public static final String REQUEST_START_NANOS = "autoapi.request.start.nanos";
  public static final String AUTHENTICATED_API_KEY = "autoapi.authenticated.api.key";

  private GatewayAttributes() {}
}
