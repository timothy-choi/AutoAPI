package com.autoapi.gateway.observability;

import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
@Order(0)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayObservabilityFilter implements WebFilter {

  private static final String SERVER_SPAN_NAME = "gateway.request";

  private final ObjectProvider<GatewayTracer> tracerProvider;
  private final ObjectProvider<GatewayObservabilityMetrics> metricsProvider;
  private final ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider;
  private final ObjectProvider<GatewayRequestSummaryBuffer> summaryBufferProvider;
  private final String gatewayId;

  public GatewayObservabilityFilter(
      ObjectProvider<GatewayTracer> tracerProvider,
      ObjectProvider<GatewayObservabilityMetrics> metricsProvider,
      ObjectProvider<GatewayStructuredLogger> structuredLoggerProvider,
      ObjectProvider<GatewayRequestSummaryBuffer> summaryBufferProvider,
      com.autoapi.gateway.GatewayProperties gatewayProperties) {
    this.tracerProvider = tracerProvider;
    this.metricsProvider = metricsProvider;
    this.structuredLoggerProvider = structuredLoggerProvider;
    this.summaryBufferProvider = summaryBufferProvider;
    this.gatewayId =
        gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if ("/healthz".equals(path) || "/readyz".equals(path)) {
      return chain.filter(exchange);
    }

    long startedAtNanos = System.nanoTime();
    exchange.getAttributes().put(GatewayAttributes.REQUEST_START_NANOS, startedAtNanos);
    GatewayObservabilityContext context = GatewayObservabilitySupport.context(exchange);
    context.setNormalizedPath(path);

    ActiveRuntimeBundle bundle = exchange.getAttribute(GatewayAttributes.ACTIVE_RUNTIME_BUNDLE);
    if (bundle != null) {
      context.setApiId(bundle.apiId().toString());
      context.setSnapshotVersion(String.valueOf(bundle.version()));
    }

    GatewayObservabilityMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.incrementInflight();
    }

    GatewayTracer tracer = tracerProvider.getIfAvailable();
    GatewayTraceScope traceScope = null;
    if (tracer != null) {
      try {
        traceScope = tracer.startServerSpan(exchange, SERVER_SPAN_NAME);
        GatewayObservabilitySupport.setTraceScope(exchange, traceScope);
        context.setTraceId(traceScope.traceId());
        context.setSpanId(traceScope.spanId());
      } catch (RuntimeException ex) {
        traceScope = null;
      }
    }

    GatewayTraceScope activeTraceScope = traceScope;
    return chain
        .filter(exchange)
        .doFinally(
            signalType ->
                completeSafely(
                    exchange, signalType, startedAtNanos, metrics, activeTraceScope, context));
  }

  private void completeSafely(
      ServerWebExchange exchange,
      SignalType signalType,
      long startedAtNanos,
      GatewayObservabilityMetrics metrics,
      GatewayTraceScope traceScope,
      GatewayObservabilityContext context) {
    try {
      complete(exchange, signalType, startedAtNanos, metrics, traceScope, context);
    } catch (RuntimeException ex) {
      // Observability must never fail user requests.
    } finally {
      if (metrics != null) {
        metrics.decrementInflight();
      }
      if (traceScope != null) {
        try {
          traceScope.close();
        } catch (RuntimeException ignored) {
          // ignore
        }
      }
    }
  }

  private void complete(
      ServerWebExchange exchange,
      SignalType signalType,
      long startedAtNanos,
      GatewayObservabilityMetrics metrics,
      GatewayTraceScope traceScope,
      GatewayObservabilityContext context) {
    if (signalType == SignalType.CANCEL) {
      context.setClientDisconnected(true);
      if (context.errorType() == GatewayErrorType.NONE) {
        context.setErrorType(GatewayErrorType.CLIENT_CANCELLED);
      }
    }

    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    String requestId = RequestIdSupport.getRequestId(exchange);
    String method =
        exchange.getRequest().getMethod() == null
            ? "UNKNOWN"
            : exchange.getRequest().getMethod().name();
    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
    int status = statusCode == null ? 0 : statusCode.value();
    HttpStatusClass statusClass = HttpStatusClass.fromStatusCode(status);

    Object routeId = exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID);
    if (routeId != null) {
      context.setRouteId(routeId.toString());
    }

    if (metrics != null) {
      metrics.recordRequest(
          context.apiId(), context.routeId(), method, statusClass, durationMs / 1000.0d);
      if (context.errorType() != GatewayErrorType.NONE) {
        metrics.recordRejection(context.routeId(), context.errorType());
      }
    }

    if (traceScope != null) {
      traceScope.setAttribute("request.id", requestId);
      traceScope.setAttribute("gateway.id", gatewayId);
      if (context.apiId() != null) {
        traceScope.setAttribute("api.id", context.apiId());
      }
      if (context.routeId() != null) {
        traceScope.setAttribute("route.id", context.routeId());
        traceScope.setAttribute("http.route", context.routeId());
      }
      traceScope.setAttribute("http.request.method", method);
      traceScope.setAttribute("http.response.status_code", String.valueOf(status));
      traceScope.setAttribute("network.protocol.name", "http");
      if (context.snapshotVersion() != null) {
        traceScope.setAttribute("runtime.snapshot.version", context.snapshotVersion());
      }
    }

    GatewayStructuredLogger structuredLogger = structuredLoggerProvider.getIfAvailable();
    if (structuredLogger != null) {
      structuredLogger.requestCompleted(
          requestId, context, method, status, durationMs, signalType.name());
    }

    GatewayRequestSummaryBuffer summaryBuffer = summaryBufferProvider.getIfAvailable();
    if (summaryBuffer != null) {
      summaryBuffer.offer(
          new GatewayRequestSummary(
              requestId,
              context.traceId(),
              gatewayId,
              context.apiId(),
              context.routeId(),
              method,
              status,
              durationMs,
              context.attemptCount(),
              context.retryCount(),
              context.fallbackUsed(),
              Instant.now()));
    }
  }
}
