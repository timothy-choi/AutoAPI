package com.autoapi.gateway.observability;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayObservabilityProperties.class)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayObservabilityAutoConfiguration {

  @Bean
  GatewayInstanceIdentity gatewayInstanceIdentity(GatewayProperties gatewayProperties) {
    String gatewayId =
        gatewayProperties.gatewayId() == null || gatewayProperties.gatewayId().isBlank()
            ? "unknown"
            : gatewayProperties.gatewayId();
    return new GatewayInstanceIdentity(gatewayId, UUID.randomUUID().toString());
  }

  @Bean
  GatewayTracer gatewayTracer(
      GatewayObservabilityProperties properties, GatewayInstanceIdentity identity) {
    return new OpenTelemetryGatewayTracer(properties, identity.gatewayId());
  }

  @Bean
  GatewayObservabilityMetrics gatewayObservabilityMetrics(
      MeterRegistry meterRegistry, GatewayInstanceIdentity identity) {
    return new GatewayObservabilityMetrics(meterRegistry, identity.gatewayId());
  }

  @Bean
  GatewayStructuredLogger gatewayStructuredLogger(
      ObjectMapper objectMapper, GatewayInstanceIdentity identity) {
    return new GatewayStructuredLogger(objectMapper, identity.gatewayId());
  }

  @Bean
  GatewayRequestSummaryBuffer gatewayRequestSummaryBuffer(
      GatewayObservabilityProperties properties) {
    return new GatewayRequestSummaryBuffer(properties);
  }
}
