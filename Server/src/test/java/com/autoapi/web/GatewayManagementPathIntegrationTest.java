package com.autoapi.web;

import com.autoapi.support.TestUpstream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayManagementPathIntegrationTest.Initializer.class)
class GatewayManagementPathIntegrationTest {

  private static final Path tempDir;
  private static final TestUpstream upstream;
  private static final Path configPath;

  static {
    try {
      tempDir = Files.createTempDirectory("autoapi-mgmt-path-it");
      upstream = TestUpstream.start();
      configPath = TestUpstream.writeConfig(upstream, tempDir);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private WebTestClient webTestClient;

  @AfterAll
  static void stopUpstream() {
    upstream.stop();
  }

  @Test
  void reservedManagementPathsAreNotProxiedWhenControlPlaneDisabled() {
    assertUpstreamNotContactedBy(
        () ->
            webTestClient
                .get()
                .uri("/api/v1/projects")
                .header(HttpHeaders.HOST, "api.autoapi.local")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.error.code")
                .doesNotExist());
  }

  @Test
  void unknownManagementPathsRemainInManagementNamespace() {
    assertUpstreamNotContactedBy(
        () ->
            webTestClient
                .get()
                .uri("/api/v1/does-not-exist")
                .header(HttpHeaders.HOST, "api.autoapi.local")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.error.code")
                .doesNotExist());
  }

  @Test
  void similarPathsOutsideManagementNamespaceUseGatewayProxy() {
    assertUpstreamNotContactedBy(
        () ->
            webTestClient
                .get()
                .uri("/api/v1example")
                .header(HttpHeaders.HOST, "api.autoapi.local")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.error.code")
                .isEqualTo("ROUTE_NOT_FOUND"));

    assertUpstreamNotContactedBy(
        () ->
            webTestClient
                .get()
                .uri("/api/v10/example")
                .header(HttpHeaders.HOST, "api.autoapi.local")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.error.code")
                .isEqualTo("ROUTE_NOT_FOUND"));
  }

  @Test
  void dataPlaneRequestsStillProxy() {
    webTestClient
        .get()
        .uri("/v1/orders/123")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.path")
        .isEqualTo("/v1/orders/123");

    org.junit.jupiter.api.Assertions.assertEquals("/v1/orders/123", upstream.lastPath());
  }

  @Test
  void operationalEndpointsBypassGatewayProxy() {
    webTestClient.get().uri("/healthz").exchange().expectStatus().isOk();
    webTestClient.get().uri("/readyz").exchange().expectStatus().isOk();
  }

  @Test
  void postManagementPathIsNotProxiedWhenControlPlaneDisabled() {
    assertUpstreamNotContactedBy(
        () ->
            webTestClient
                .post()
                .uri("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"demo\",\"description\":\"demo\"}")
                .header(HttpHeaders.HOST, "api.autoapi.local")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error.code")
                .doesNotExist());
  }

  private void assertUpstreamNotContactedBy(Runnable request) {
    String pathBefore = upstream.lastPath();
    request.run();
    org.junit.jupiter.api.Assertions.assertEquals(
        pathBefore, upstream.lastPath(), "Expected request to stay out of the gateway data plane");
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayManagementPathIntegrationTest",
              java.util.Map.of(
                  "autoapi.controlplane.enabled",
                  "false",
                  "spring.flyway.enabled",
                  "false",
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
      TestUpstream.initializer(configPath).initialize(context);
    }
  }
}
