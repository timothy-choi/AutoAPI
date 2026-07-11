package com.autoapi;

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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayIntegrationTest.Initializer.class)
class GatewayIntegrationTest {

  private static final Path tempDir;
  private static final TestUpstream upstream;
  private static final Path configPath;

  static {
    try {
      tempDir = Files.createTempDirectory("autoapi-it");
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
  void healthEndpoints() {
    webTestClient.get().uri("/healthz").exchange().expectStatus().isOk();
    webTestClient.get().uri("/readyz").exchange().expectStatus().isOk();
  }

  @Test
  void proxiesMatchingRequest() {
    webTestClient
        .get()
        .uri("/v1/orders/123?q=1")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .header("X-Request-ID", "it-req-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists("X-Request-ID")
        .expectBody()
        .jsonPath("$.path")
        .isEqualTo("/v1/orders/123");

    org.junit.jupiter.api.Assertions.assertEquals("/v1/orders/123", upstream.lastPath());
    org.junit.jupiter.api.Assertions.assertEquals("it-req-1", upstream.lastRequestId());
  }

  @Test
  void methodNotAllowed() {
    webTestClient
        .delete()
        .uri("/v1/orders/123")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
        .expectHeader()
        .valueEquals(HttpHeaders.ALLOW, "GET, POST")
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  void routeNotFound() {
    webTestClient
        .get()
        .uri("/unknown")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("ROUTE_NOT_FOUND");
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      TestUpstream.initializer(configPath).initialize(context);
    }
  }
}
