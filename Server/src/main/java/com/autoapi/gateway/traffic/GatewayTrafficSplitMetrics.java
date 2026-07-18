package com.autoapi.gateway.traffic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class GatewayTrafficSplitMetrics {

  private final MeterRegistry meterRegistry;

  public GatewayTrafficSplitMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordAssignment(
      String routeId,
      String policyId,
      String destinationId,
      String destinationName,
      String fallbackMode) {
    counter(
            "autoapi_gateway_traffic_split_assignments_total",
            routeId,
            policyId,
            destinationId,
            destinationName,
            fallbackMode,
            "assignment")
        .increment();
  }

  public void recordFallback(
      String routeId,
      String policyId,
      String destinationId,
      String destinationName,
      String fallbackMode,
      String reason) {
    counter(
            "autoapi_gateway_traffic_split_fallbacks_total",
            routeId,
            policyId,
            destinationId,
            destinationName,
            fallbackMode,
            reason)
        .increment();
  }

  public void recordUnavailable(String routeId, String policyId, String fallbackMode) {
    counter(
            "autoapi_gateway_traffic_split_unavailable_total",
            routeId,
            policyId,
            "unavailable",
            "unavailable",
            fallbackMode,
            "no_eligible_destination")
        .increment();
  }

  private Counter counter(
      String name,
      String routeId,
      String policyId,
      String destinationId,
      String destinationName,
      String fallbackMode,
      String reason) {
    return Counter.builder(name)
        .tag("route_id", routeId == null ? "unknown" : routeId)
        .tag("policy_id", policyId == null ? "unknown" : policyId)
        .tag("destination_id", destinationId == null ? "unknown" : destinationId)
        .tag("destination_name", destinationName == null ? "unknown" : destinationName)
        .tag("fallback_mode", fallbackMode == null ? "unknown" : fallbackMode)
        .tag("reason", reason == null ? "none" : reason)
        .register(meterRegistry);
  }
}
