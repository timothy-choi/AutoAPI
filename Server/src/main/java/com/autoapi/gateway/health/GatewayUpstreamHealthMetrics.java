package com.autoapi.gateway.health;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class GatewayUpstreamHealthMetrics {

  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, AtomicInteger> ejectedTargetGauges =
      new ConcurrentHashMap<>();

  public GatewayUpstreamHealthMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordUpstreamRequest(String routeId, String poolId, String targetId) {
    Counter.builder("autoapi_gateway_upstream_requests_total")
        .tag("route_id", routeId)
        .tag("pool_id", poolId)
        .tag("target_id", targetId)
        .register(meterRegistry)
        .increment();
  }

  public void recordTransportFailure(
      String routeId, String poolId, String targetId, String category) {
    Counter.builder("autoapi_gateway_upstream_transport_failures_total")
        .tag("route_id", routeId)
        .tag("pool_id", poolId)
        .tag("target_id", targetId)
        .tag("failure_category", category)
        .register(meterRegistry)
        .increment();
  }

  public void recordEjection(String routeId, String poolId, String targetId, String category) {
    Counter.builder("autoapi_gateway_upstream_ejections_total")
        .tag("route_id", routeId)
        .tag("pool_id", poolId)
        .tag("target_id", targetId)
        .tag("failure_category", category)
        .register(meterRegistry)
        .increment();
  }

  public void recordRecovery(String routeId, String poolId, String targetId) {
    Counter.builder("autoapi_gateway_upstream_recoveries_total")
        .tag("route_id", routeId)
        .tag("pool_id", poolId)
        .tag("target_id", targetId)
        .register(meterRegistry)
        .increment();
  }

  public void recordForcedSelection(String routeId, String poolId, String targetId) {
    Counter.builder("autoapi_gateway_upstream_forced_selections_total")
        .tag("route_id", routeId)
        .tag("pool_id", poolId)
        .tag("target_id", targetId)
        .register(meterRegistry)
        .increment();
  }

  public void setEjectedTargets(UUID poolId, int count) {
    String poolTag = poolId.toString();
    AtomicInteger gauge =
        ejectedTargetGauges.computeIfAbsent(
            poolTag,
            ignored -> {
              AtomicInteger value = new AtomicInteger(0);
              Gauge.builder("autoapi_gateway_upstream_ejected_targets", value, AtomicInteger::get)
                  .tag("pool_id", poolTag)
                  .register(meterRegistry);
              return value;
            });
    gauge.set(count);
  }
}
