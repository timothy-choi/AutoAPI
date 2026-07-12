package com.autoapi.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.gateway.redis.FixedWindowRateLimiter;
import com.autoapi.gateway.redis.GatewayRateLimitService;
import com.autoapi.support.SecurityTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

class GatewayRedisRateLimitAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "autoapi.role=gateway",
              "autoapi.security.api-key-pepper=" + SecurityTestFixtures.TEST_PEPPER)
          .withBean(ReactiveRedisConnectionFactory.class, () -> mock(ReactiveRedisConnectionFactory.class))
          .withBean(GatewaySecurityMetrics.class, () -> mock(GatewaySecurityMetrics.class))
          .withUserConfiguration(GatewayRedisRateLimitAutoConfiguration.class);

  @Test
  void registersLimiterAndServiceTogether() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(FixedWindowRateLimiter.class);
          assertThat(context).hasSingleBean(GatewayRateLimitService.class);
        });
  }
}
