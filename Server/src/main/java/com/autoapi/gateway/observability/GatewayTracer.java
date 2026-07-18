package com.autoapi.gateway.observability;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/** Distributed tracing abstraction with OpenTelemetry-compatible semantics. */
public interface GatewayTracer {

  GatewayTraceScope startServerSpan(ServerWebExchange exchange, String spanName);

  GatewayTraceScope startClientSpan(GatewayTraceScope parent, String spanName, String attemptId);

  void inject(GatewayTraceScope scope, HttpHeaders headers);

  boolean enabled();
}
