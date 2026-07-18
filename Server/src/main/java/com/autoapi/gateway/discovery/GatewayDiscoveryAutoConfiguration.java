package com.autoapi.gateway.discovery;

import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayDiscoveryAutoConfiguration {

  @Bean
  DiscoveredServiceSelector discoveredServiceSelector(
      GatewayTargetSelector gatewayTargetSelector,
      ObjectProvider<GatewayDiscoveryMetrics> metricsProvider) {
    return new DiscoveredServiceSelector(gatewayTargetSelector, metricsProvider);
  }

  @Bean
  GatewayDiscoveryMetrics gatewayDiscoveryMetrics(
      ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
    io.micrometer.core.instrument.MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? null : new GatewayDiscoveryMetrics(registry);
  }
}
