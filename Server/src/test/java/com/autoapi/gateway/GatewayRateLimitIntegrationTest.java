package com.autoapi.gateway;

import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.gateway.redis.FixedWindowRateLimiter;
import com.autoapi.gateway.redis.GatewayRateLimitService;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.support.RedisDynamicProperties;
import com.autoapi.support.SecurityTestFixtures;
import com.autoapi.support.TestUpstream;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=gateway",
      "autoapi.controlplane.enabled=false",
      "spring.flyway.enabled=false",
      "autoapi.gateway.api-id=00000000-0000-0000-0000-000000000001",
      "autoapi.security.api-key-pepper=" + SecurityTestFixtures.TEST_PEPPER
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayRateLimitIntegrationTest.Initializer.class)
class GatewayRateLimitIntegrationTest {

  private static final TestUpstream upstream;
  private static final ApiKeyGenerator.GeneratedApiKeyMaterial keyMaterial =
      ApiKeyGenerator.generate();

  static {
    try {
      upstream = TestUpstream.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private WebTestClient webTestClient;
  @Autowired private FixedWindowRateLimiter fixedWindowRateLimiter;
  @Autowired private GatewayRateLimitService gatewayRateLimitService;

  @BeforeEach
  void requireRateLimitBeans() {
    org.junit.jupiter.api.Assertions.assertNotNull(fixedWindowRateLimiter);
    org.junit.jupiter.api.Assertions.assertNotNull(gatewayRateLimitService);
  }

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisDynamicProperties.registerRedisProperties(registry);
  }

  @AfterAll
  static void shutdown() {
    upstream.stop();
  }

  @Test
  void sixthRequestReturns429AcrossSharedRedis() {
    for (int i = 0; i < 5; i++) {
      var spec =
          webTestClient
              .get()
              .uri("/v1/orders/" + i)
              .header(HttpHeaders.HOST, "api.autoapi.local")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + keyMaterial.plaintextKey())
              .exchange()
              .expectStatus()
              .isOk()
              .expectHeader()
              .exists("RateLimit-Limit");
      if (i == 4) {
        spec.expectHeader().valueEquals("RateLimit-Remaining", "0");
      }
    }

    webTestClient
        .get()
        .uri("/v1/orders/exceeded")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + keyMaterial.plaintextKey())
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .exists("RateLimit-Limit")
        .expectHeader()
        .exists("RateLimit-Remaining")
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      RuntimeConfig config =
          SecurityTestFixtures.protectedRouteConfig(upstream.port(), keyMaterial, true);
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayRateLimitIntegrationTest",
              java.util.Map.of(
                  "spring.autoconfigure.exclude",
                  String.join(
                      ",",
                      "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration",
                      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                      "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")));
      context.getEnvironment().getPropertySources().addFirst(propertySource);
      GatewayBootstrap.initializer(config).initialize(context);
    }
  }
}
