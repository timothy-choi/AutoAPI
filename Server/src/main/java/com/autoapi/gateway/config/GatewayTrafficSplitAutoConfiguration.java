package com.autoapi.gateway.config;

import com.autoapi.gateway.traffic.GatewayTrafficSplitMetrics;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayTrafficSplitAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  GatewayTrafficSplitMetrics gatewayTrafficSplitMetrics(MeterRegistry meterRegistry) {
    return new GatewayTrafficSplitMetrics(meterRegistry);
  }
}
