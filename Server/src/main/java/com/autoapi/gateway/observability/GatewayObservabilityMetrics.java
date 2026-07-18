package com.autoapi.gateway.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Unified gateway observability metrics with bounded-cardinality labels. */
@Component
public class GatewayObservabilityMetrics {

  private static final String GATEWAY_TAG = "gateway";
  private static final String API_TAG = "api";
  private static final String ROUTE_TAG = "route";
  private static final String METHOD_TAG = "method";
  private static final String STATUS_CLASS_TAG = "status_class";
  private static final String RESULT_TAG = "result";
  private static final String POOL_TAG = "pool";
  private static final String REASON_TAG = "reason";
  private static final String ATTEMPT_TAG = "attempt";

  private final MeterRegistry meterRegistry;
  private final String gatewayId;
  private final AtomicInteger inflightRequests = new AtomicInteger();
  private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();
  private final Map<String, Timer> requestTimers = new ConcurrentHashMap<>();
  private final Map<String, Counter> upstreamAttemptCounters = new ConcurrentHashMap<>();
  private final Map<String, Timer> upstreamTimers = new ConcurrentHashMap<>();
  private final Map<String, Counter> retryCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> fallbackCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> rejectionCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> upstreamErrorCounters = new ConcurrentHashMap<>();

  public GatewayObservabilityMetrics(MeterRegistry meterRegistry, String gatewayId) {
    this.meterRegistry = meterRegistry;
    this.gatewayId = MetricLabelNormalizer.gateway(gatewayId);
    Gauge.builder("autoapi_gateway_inflight_requests", inflightRequests, AtomicInteger::get)
        .tag(GATEWAY_TAG, this.gatewayId)
        .register(meterRegistry);
  }

  public void incrementInflight() {
    inflightRequests.incrementAndGet();
  }

  public void decrementInflight() {
    inflightRequests.updateAndGet(current -> Math.max(0, current - 1));
  }

  public void recordRequest(
      String apiId, String routeId, String method, HttpStatusClass statusClass, double seconds) {
    String route = MetricLabelNormalizer.route(routeId);
    String api = MetricLabelNormalizer.api(apiId);
    String normalizedMethod = MetricLabelNormalizer.method(method);
    String status = statusClass.label();
    requestCounter(route, api, normalizedMethod, status).increment();
    requestTimer(route, api, normalizedMethod, status)
        .record(java.time.Duration.ofNanos((long) (seconds * 1_000_000_000L)));
  }

  public void recordUpstreamAttempt(
      String routeId,
      String pool,
      String result,
      int attemptNumber,
      HttpStatusClass statusClass,
      double seconds) {
    upstreamAttemptCounter(
            MetricLabelNormalizer.route(routeId),
            MetricLabelNormalizer.pool(pool),
            MetricLabelNormalizer.result(result),
            MetricLabelNormalizer.attemptNumber(attemptNumber),
            statusClass.label())
        .increment();
    upstreamTimer(
            MetricLabelNormalizer.route(routeId),
            MetricLabelNormalizer.pool(pool),
            MetricLabelNormalizer.attemptNumber(attemptNumber))
        .record(java.time.Duration.ofNanos((long) (seconds * 1_000_000_000L)));
  }

  public void recordRetry(String routeId, GatewayErrorType reason) {
    retryCounter(MetricLabelNormalizer.route(routeId), MetricLabelNormalizer.reason(reason))
        .increment();
  }

  public void recordFallback(String routeId, String reason) {
    fallbackCounter(MetricLabelNormalizer.route(routeId), MetricLabelNormalizer.result(reason))
        .increment();
  }

  public void recordRejection(String routeId, GatewayErrorType reason) {
    rejectionCounter(MetricLabelNormalizer.route(routeId), MetricLabelNormalizer.reason(reason))
        .increment();
  }

  public void recordUpstreamError(String routeId, GatewayErrorType errorType) {
    upstreamErrorCounter(
            MetricLabelNormalizer.route(routeId), MetricLabelNormalizer.reason(errorType))
        .increment();
  }

  public void recordRuntimeSnapshotInfo(
      String snapshotVersion, long routeCount, long targetCount, long configurationVersion) {
    meterRegistry.gauge(
        "autoapi_gateway_runtime_snapshot_info",
        java.util.List.of(
            io.micrometer.core.instrument.Tag.of(GATEWAY_TAG, gatewayId),
            io.micrometer.core.instrument.Tag.of(
                "snapshot_version", MetricLabelNormalizer.normalize(snapshotVersion, "unknown"))),
        routeCount,
        ignored -> routeCount);
    meterRegistry
        .counter(
            "autoapi_gateway_runtime_snapshot_activations_total",
            GATEWAY_TAG,
            gatewayId,
            "snapshot_version",
            MetricLabelNormalizer.normalize(snapshotVersion, "unknown"))
        .increment();
  }

  private Counter requestCounter(String route, String api, String method, String statusClass) {
    return requestCounters.computeIfAbsent(
        route + "|" + api + "|" + method + "|" + statusClass,
        ignored ->
            Counter.builder("autoapi_gateway_requests_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(API_TAG, api)
                .tag(ROUTE_TAG, route)
                .tag(METHOD_TAG, method)
                .tag(STATUS_CLASS_TAG, statusClass)
                .register(meterRegistry));
  }

  private Timer requestTimer(String route, String api, String method, String statusClass) {
    return requestTimers.computeIfAbsent(
        route + "|" + api + "|" + method + "|" + statusClass,
        ignored ->
            Timer.builder("autoapi_gateway_request_duration_seconds")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(API_TAG, api)
                .tag(ROUTE_TAG, route)
                .tag(METHOD_TAG, method)
                .tag(STATUS_CLASS_TAG, statusClass)
                .publishPercentileHistogram()
                .register(meterRegistry));
  }

  private Counter upstreamAttemptCounter(
      String route, String pool, String result, String attempt, String statusClass) {
    String key = route + "|" + pool + "|" + result + "|" + attempt + "|" + statusClass;
    return upstreamAttemptCounters.computeIfAbsent(
        key,
        ignored ->
            Counter.builder("autoapi_gateway_upstream_attempts_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(POOL_TAG, pool)
                .tag(RESULT_TAG, result)
                .tag(ATTEMPT_TAG, attempt)
                .tag(STATUS_CLASS_TAG, statusClass)
                .register(meterRegistry));
  }

  private Timer upstreamTimer(String route, String pool, String attempt) {
    String key = route + "|" + pool + "|" + attempt;
    return upstreamTimers.computeIfAbsent(
        key,
        ignored ->
            Timer.builder("autoapi_gateway_upstream_duration_seconds")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(POOL_TAG, pool)
                .tag(ATTEMPT_TAG, attempt)
                .publishPercentileHistogram()
                .register(meterRegistry));
  }

  private Counter retryCounter(String route, String reason) {
    return retryCounters.computeIfAbsent(
        route + "|" + reason,
        ignored ->
            Counter.builder("autoapi_gateway_retries_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(REASON_TAG, reason)
                .register(meterRegistry));
  }

  private Counter fallbackCounter(String route, String reason) {
    return fallbackCounters.computeIfAbsent(
        route + "|" + reason,
        ignored ->
            Counter.builder("autoapi_gateway_fallback_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(REASON_TAG, reason)
                .register(meterRegistry));
  }

  private Counter rejectionCounter(String route, String reason) {
    return rejectionCounters.computeIfAbsent(
        route + "|" + reason,
        ignored ->
            Counter.builder("autoapi_gateway_rejections_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(REASON_TAG, reason)
                .register(meterRegistry));
  }

  private Counter upstreamErrorCounter(String route, String reason) {
    return upstreamErrorCounters.computeIfAbsent(
        route + "|" + reason,
        ignored ->
            Counter.builder("autoapi_gateway_upstream_errors_total")
                .tag(GATEWAY_TAG, gatewayId)
                .tag(ROUTE_TAG, route)
                .tag(REASON_TAG, reason)
                .register(meterRegistry));
  }
}
