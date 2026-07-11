package com.autoapi;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.support.TestUpstream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = UpstreamFailureIntegrationTest.Initializer.class)
class UpstreamFailureIntegrationTest {

  private static final Path tempDir;
  private static final Path configPath;

  static {
    try {
      tempDir = Files.createTempDirectory("autoapi-upstream-fail");
      configPath = TestUpstream.writeConfigWithUpstreamUrl(tempDir, "http://127.0.0.1:1");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private WebTestClient webTestClient;

  @Test
  void connectionRefusalReturnsControlled502() {
    webTestClient
        .get()
        .uri("/v1/orders/123")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isEqualTo(502)
        .expectBody(String.class)
        .consumeWith(
            result -> {
              String body = result.getResponseBody();
              assertNotNull(body);
              org.junit.jupiter.api.Assertions.assertTrue(body.contains("UPSTREAM_UNAVAILABLE"));
              org.junit.jupiter.api.Assertions.assertFalse(
                  body.contains("WebClientRequestException"));
              org.junit.jupiter.api.Assertions.assertFalse(body.contains("Connection refused"));
            });
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "upstreamFailureIntegrationTest",
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
                      "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")));
      context.getEnvironment().getPropertySources().addFirst(propertySource);
      TestUpstream.initializer(configPath).initialize(context);
    }
  }
}
