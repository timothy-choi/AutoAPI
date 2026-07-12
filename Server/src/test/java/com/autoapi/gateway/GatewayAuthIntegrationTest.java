package com.autoapi.gateway;

import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.support.SecurityTestFixtures;
import com.autoapi.support.TestUpstream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=combined",
      "autoapi.controlplane.enabled=false",
      "spring.flyway.enabled=false",
      "autoapi.security.api-key-pepper=" + SecurityTestFixtures.TEST_PEPPER
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayAuthIntegrationTest.Initializer.class)
class GatewayAuthIntegrationTest {

  private static final Path tempDir;
  private static final TestUpstream upstream;
  private static final ApiKeyGenerator.GeneratedApiKeyMaterial keyMaterial =
      ApiKeyGenerator.generate();

  static {
    try {
      tempDir = Files.createTempDirectory("autoapi-auth-it");
      upstream = TestUpstream.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private WebTestClient webTestClient;

  @BeforeAll
  static void verifyUpstream() {
    org.junit.jupiter.api.Assertions.assertTrue(upstream.port() > 0);
  }

  @AfterAll
  static void shutdown() {
    upstream.stop();
  }

  @Test
  void missingApiKeyReturns401() {
    webTestClient
        .get()
        .uri("/v1/orders/1")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_API_KEY");
  }

  @Test
  void malformedApiKeyReturns401() {
    webTestClient
        .get()
        .uri("/v1/orders/1")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .header(HttpHeaders.AUTHORIZATION, "Bearer not-valid")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_API_KEY");
  }

  @Test
  void validApiKeyProxiesRequest() {
    webTestClient
        .get()
        .uri("/v1/orders/1")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + keyMaterial.plaintextKey())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.path")
        .isEqualTo("/v1/orders/1");
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      RuntimeConfig config =
          SecurityTestFixtures.protectedRouteConfig(upstream.port(), keyMaterial, false);
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayAuthIntegrationTest",
              java.util.Map.of(
                  "spring.autoconfigure.exclude",
                  String.join(
                      ",",
                      "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration",
                      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                      "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration")));
      context.getEnvironment().getPropertySources().addFirst(propertySource);
      GatewayBootstrap.initializer(config).initialize(context);
    }
  }
}
