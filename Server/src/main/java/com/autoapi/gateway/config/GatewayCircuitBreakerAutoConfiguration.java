package com.autoapi.gateway.config;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.circuitbreaker.CircuitBreakerRegistry;
import com.autoapi.gateway.circuitbreaker.GatewayCircuitBreakerMetrics;
import com.autoapi.gateway.circuitbreaker.GatewayInternalCircuitBreakerHandler;
import com.autoapi.gateway.circuitbreaker.GatewayTargetSelector;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayCircuitBreakerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  GatewayCircuitBreakerMetrics gatewayCircuitBreakerMetrics(MeterRegistry meterRegistry) {
    return new GatewayCircuitBreakerMetrics(meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  CircuitBreakerRegistry circuitBreakerRegistry(
      Clock gatewayHealthClock,
      GatewayProperties gatewayProperties,
      org.springframework.beans.factory.ObjectProvider<GatewayCircuitBreakerMetrics>
          metricsProvider) {
    String gatewayId =
        gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId();
    return new CircuitBreakerRegistry(gatewayHealthClock, gatewayId, metricsProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  GatewayTargetSelector gatewayTargetSelector(
      HealthAwareTargetSelector healthAwareTargetSelector,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new GatewayTargetSelector(healthAwareTargetSelector, circuitBreakerRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  GatewayInternalCircuitBreakerHandler gatewayInternalCircuitBreakerHandler(
      CircuitBreakerRegistry circuitBreakerRegistry, GatewayProperties gatewayProperties) {
    return new GatewayInternalCircuitBreakerHandler(circuitBreakerRegistry, gatewayProperties);
  }
}
