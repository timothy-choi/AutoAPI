package com.autoapi.gateway.discovery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class GatewayDiscoveryMetrics {

  private final Counter selections;
  private final Counter noEligible;

  public GatewayDiscoveryMetrics(MeterRegistry meterRegistry) {
    this.selections =
        Counter.builder("autoapi_gateway_service_instance_selections_total")
            .description("Discovered service instance selections")
            .register(meterRegistry);
    this.noEligible =
        Counter.builder("autoapi_gateway_service_no_eligible_instance_total")
            .description("Requests with no eligible discovered service instance")
            .register(meterRegistry);
  }

  public void recordInstanceSelection(String strategy) {
    selections.increment();
  }

  public void recordNoEligibleInstance() {
    noEligible.increment();
  }
}
