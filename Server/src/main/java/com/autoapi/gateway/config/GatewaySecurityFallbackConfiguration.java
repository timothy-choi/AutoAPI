package com.autoapi.gateway.config;

import com.autoapi.gateway.security.GatewaySecurityEnforcer;
import com.autoapi.gateway.security.GatewaySecurityPipeline;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewaySecurityFallbackConfiguration {

  @Bean
  @ConditionalOnMissingBean(GatewaySecurityPipeline.class)
  GatewaySecurityEnforcer noopGatewaySecurityEnforcer() {
    return (exchange, bundle, route) -> reactor.core.publisher.Mono.empty();
  }
}
