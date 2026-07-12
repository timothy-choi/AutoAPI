package com.autoapi.gateway.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewaySecurityMetrics {

  private final MeterRegistry meterRegistry;

  public GatewaySecurityMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordAuthAttempt(String routeId) {
    Counter.builder("autoapi_gateway_auth_attempts_total")
        .tag("route_id", routeId)
        .register(meterRegistry)
        .increment();
  }

  public void recordAuthFailure(String routeId, String failureCategory) {
    Counter.builder("autoapi_gateway_auth_failures_total")
        .tag("route_id", routeId)
        .tag("failure_category", failureCategory)
        .register(meterRegistry)
        .increment();
  }

  public void recordRateLimitAllowed(String routeId, String policyId) {
    Counter.builder("autoapi_gateway_rate_limit_allowed_total")
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry)
        .increment();
  }

  public void recordRateLimitRejected(String routeId, String policyId) {
    Counter.builder("autoapi_gateway_rate_limit_rejected_total")
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry)
        .increment();
  }

  public void recordRateLimitRedisError(String routeId, String policyId) {
    Counter.builder("autoapi_gateway_rate_limit_redis_errors_total")
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry)
        .increment();
  }

  public void recordRateLimitFailOpen(String routeId, String policyId) {
    Counter.builder("autoapi_gateway_rate_limit_fail_open_total")
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry)
        .increment();
  }

  public void recordRateLimitFailClosed(String routeId, String policyId) {
    Counter.builder("autoapi_gateway_rate_limit_fail_closed_total")
        .tag("route_id", routeId)
        .tag("policy_id", policyId)
        .register(meterRegistry)
        .increment();
  }
}
