package com.autoapi.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapi.gateway.auth.ApiKeyAuthenticator;
import com.autoapi.gateway.auth.GatewaySecurityMetrics;
import com.autoapi.gateway.security.GatewaySecurityEnforcer;
import com.autoapi.gateway.security.GatewaySecurityPipeline;
import com.autoapi.runtime.AutoApiRuntimeConfiguration;
import com.autoapi.support.SecurityTestFixtures;
import com.autoapi.web.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

class GatewaySecurityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "autoapi.role=gateway",
              "autoapi.security.api-key-pepper=" + SecurityTestFixtures.TEST_PEPPER)
          .withBean(
              ReactiveRedisConnectionFactory.class,
              () -> mock(ReactiveRedisConnectionFactory.class))
          .withBean(GatewaySecurityMetrics.class, () -> mock(GatewaySecurityMetrics.class))
          .withBean(ErrorResponseWriter.class, () -> mock(ErrorResponseWriter.class))
          .withUserConfiguration(
              AutoApiRuntimeConfiguration.class,
              GatewaySecurityConfiguration.class,
              GatewayRedisRateLimitAutoConfiguration.class,
              GatewayRateLimitServiceAutoConfiguration.class);

  @Test
  void registersRealSecurityPipelineWhenPepperAndRedisAreConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ApiKeyAuthenticator.class);
          assertThat(context).hasSingleBean(GatewaySecurityPipeline.class);
          assertThat(context.getBean(GatewaySecurityEnforcer.class))
              .isInstanceOf(GatewaySecurityPipeline.class);
        });
  }
}
