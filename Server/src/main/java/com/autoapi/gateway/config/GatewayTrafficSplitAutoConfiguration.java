package com.autoapi.gateway.config;

import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.traffic.GatewayTrafficSplitMetrics;
import com.autoapi.gateway.traffic.TrafficSplitRegistry;
import com.autoapi.gateway.traffic.TrafficSplitSelector;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayTrafficSplitAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  TrafficSplitRegistry trafficSplitRegistry() {
    return new TrafficSplitRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  TrafficSplitSelector trafficSplitSelector(
      HealthAwareTargetSelector targetSelector,
      TrafficSplitRegistry registry,
      ObjectProvider<GatewayTrafficSplitMetrics> metricsProvider) {
    return new TrafficSplitSelector(targetSelector, registry, metricsProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  GatewayTrafficSplitMetrics gatewayTrafficSplitMetrics(MeterRegistry meterRegistry) {
    return new GatewayTrafficSplitMetrics(meterRegistry);
  }
}
