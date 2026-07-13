package com.autoapi.gateway.retry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayRetryMetrics {

  private final MeterRegistry meterRegistry;

  public GatewayRetryMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordAttempt(String routeId, String policyId) {
    counter("autoapi_gateway_upstream_attempts_total", routeId, policyId, "attempt", null)
        .increment();
  }

  public void recordRetryAttempted(String routeId, String policyId, String failureCategory) {
    counter("autoapi_gateway_retries_attempted_total", routeId, policyId, "retry", failureCategory)
        .increment();
  }

  public void recordRetrySucceeded(String routeId, String policyId) {
    counter("autoapi_gateway_retries_succeeded_total", routeId, policyId, "success", null)
        .increment();
  }

  public void recordRetryExhausted(String routeId, String policyId, String failureCategory) {
    counter(
            "autoapi_gateway_retries_exhausted_total",
            routeId,
            policyId,
            "exhausted",
            failureCategory)
        .increment();
  }

  public void recordBudgetDenied(String routeId, String policyId) {
    counter("autoapi_gateway_retries_budget_denied_total", routeId, policyId, "budget_denied", null)
        .increment();
  }

  public void recordMethodDenied(String routeId, String policyId) {
    counter("autoapi_gateway_retries_method_denied_total", routeId, policyId, "method_denied", null)
        .increment();
  }

  public void recordBodyUnreplayable(String routeId, String policyId) {
    counter(
            "autoapi_gateway_retries_body_unreplayable_total",
            routeId,
            policyId,
            "body_unreplayable",
            null)
        .increment();
  }

  private Counter counter(
      String name, String routeId, String policyId, String result, String failureCategory) {
    var builder =
        Counter.builder(name)
            .tag("route_id", routeId)
            .tag("policy_id", policyId)
            .tag("result", result);
    if (failureCategory != null) {
      builder.tag("failure_category", failureCategory);
    }
    return builder.register(meterRegistry);
  }
}
