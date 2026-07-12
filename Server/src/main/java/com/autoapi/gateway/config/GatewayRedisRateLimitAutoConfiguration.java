package com.autoapi.gateway.config;

import com.autoapi.gateway.redis.FixedWindowRateLimiter;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.security.ConditionalOnConfiguredApiKeyPepper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnClass(ReactiveRedisConnectionFactory.class)
@ConditionalOnConfiguredApiKeyPepper
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class GatewayRedisRateLimitAutoConfiguration {

  @Bean
  @ConditionalOnBean(ReactiveRedisConnectionFactory.class)
  @ConditionalOnMissingBean(FixedWindowRateLimiter.class)
  FixedWindowRateLimiter fixedWindowRateLimiter(ReactiveRedisConnectionFactory connectionFactory) {
    return new FixedWindowRateLimiter(new ReactiveStringRedisTemplate(connectionFactory));
  }
}
