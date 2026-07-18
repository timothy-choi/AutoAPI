package com.autoapi.gateway.circuitbreaker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class GatewayCircuitBreakerMetrics {

  private final MeterRegistry meterRegistry;

  public GatewayCircuitBreakerMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordOpen(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_open_total", routeId, policyId).increment();
  }

  public void recordHalfOpen(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_half_open_total", routeId, policyId).increment();
  }

  public void recordClosed(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_closed_total", routeId, policyId).increment();
  }

  public void recordTrip(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_trip_total", routeId, policyId).increment();
  }

  public void recordRecovery(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_recovery_total", routeId, policyId).increment();
  }

  public void recordRejectedRequest(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_rejected_requests_total", routeId, policyId).increment();
  }

  public void recordHalfOpenSuccess(String routeId, String policyId) {
    counter("autoapi_gateway_breaker_half_open_success_total", routeId, policyId).increment();
  }

  private Counter counter(String name, String routeId, String policyId) {
    return Counter.builder(name)
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry);
  }
}
