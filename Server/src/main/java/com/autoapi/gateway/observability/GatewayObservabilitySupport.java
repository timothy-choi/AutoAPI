package com.autoapi.gateway.observability;

import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.proxy.GatewayAttributes;
import org.springframework.web.server.ServerWebExchange;

/** Helpers for storing and reading observability state on the exchange. */
public final class GatewayObservabilitySupport {

  public static final String TRACE_SCOPE = "autoapi.observability.trace.scope";
  public static final String OBSERVABILITY_CONTEXT = "autoapi.observability.context";

  private GatewayObservabilitySupport() {}

  public static GatewayObservabilityContext context(ServerWebExchange exchange) {
    Object existing = exchange.getAttribute(OBSERVABILITY_CONTEXT);
    if (existing instanceof GatewayObservabilityContext context) {
      return context;
    }
    GatewayObservabilityContext created = new GatewayObservabilityContext();
    exchange.getAttributes().put(OBSERVABILITY_CONTEXT, created);
    return created;
  }

  public static GatewayTraceScope traceScope(ServerWebExchange exchange) {
    Object existing = exchange.getAttribute(TRACE_SCOPE);
    return existing instanceof GatewayTraceScope scope ? scope : null;
  }

  public static void setTraceScope(ServerWebExchange exchange, GatewayTraceScope scope) {
    exchange.getAttributes().put(TRACE_SCOPE, scope);
  }

  public static GatewayErrorType mapFailureCategory(FailureCategory category) {
    if (category == null) {
      return GatewayErrorType.INTERNAL_ERROR;
    }
    return switch (category) {
      case CONNECTION_TIMEOUT -> GatewayErrorType.CONNECT_TIMEOUT;
      case RESPONSE_TIMEOUT -> GatewayErrorType.READ_TIMEOUT;
      case CONNECTION_REFUSED -> GatewayErrorType.CONNECTION_REFUSED;
      case CONNECTION_RESET, DNS_FAILURE, PREMATURE_UPSTREAM_CLOSE ->
          GatewayErrorType.INTERNAL_ERROR;
    };
  }

  public static String normalizedPath(ServerWebExchange exchange) {
    Object routeId = exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID);
    if (routeId != null) {
      return "route:" + routeId;
    }
    return exchange.getRequest().getPath().pathWithinApplication().value();
  }
}
