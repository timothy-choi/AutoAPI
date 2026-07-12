package com.autoapi.gateway.config;

import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.gateway.redis.FixedWindowRateLimiter;
import com.autoapi.gateway.redis.GatewayRateLimitService;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.security.ConditionalOnConfiguredApiKeyPepper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnConfiguredApiKeyPepper
@AutoConfigureAfter(GatewayRedisRateLimitAutoConfiguration.class)
@ConditionalOnBean(FixedWindowRateLimiter.class)
public class GatewayRateLimitServiceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(GatewayRateLimitService.class)
  GatewayRateLimitService gatewayRateLimitService(
      FixedWindowRateLimiter rateLimiter, GatewaySecurityMetrics metrics) {
    return new GatewayRateLimitService(rateLimiter, metrics);
  }
}
