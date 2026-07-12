package com.autoapi.gateway;

import com.autoapi.config.BackendHealthPolicyConfig;
import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.support.ControllableClock;
import com.autoapi.support.ControllableTestUpstream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=gateway",
      "autoapi.controlplane.enabled=false",
      "spring.flyway.enabled=false",
      "autoapi.gateway.api-id=" + GatewayPassiveHealthIntegrationTest.API_ID
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayPassiveHealthIntegrationTest.Initializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GatewayPassiveHealthIntegrationTest {

  static final String API_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID HEALTHY_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID FAILING_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000031");

  static final ControllableClock CLOCK =
      ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z"));

  private static final ControllableTestUpstream healthyUpstream;
  private static final ControllableTestUpstream failingUpstream;

  static {
    try {
      healthyUpstream = ControllableTestUpstream.start();
      failingUpstream = ControllableTestUpstream.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private WebTestClient webTestClient;

  @BeforeEach
  void resetUpstreamsAndClock() throws IOException {
    CLOCK.setInstant(Instant.parse("2026-01-01T00:00:00Z"));
    healthyUpstream.resumeAccepting();
    failingUpstream.resumeAccepting();
    failingUpstream.stopAccepting();
  }

  @AfterAll
  static void shutdownUpstreams() {
    healthyUpstream.shutdown();
    failingUpstream.shutdown();
  }

  @Test
  void ejectsFailingTargetAndRoutesToHealthyPeer() {
    expectUpstreamSuccess("/v1/orders/eject-1");
    expectUpstreamFailure("/v1/orders/eject-2");
    expectUpstreamSuccess("/v1/orders/eject-3");
    expectUpstreamFailure("/v1/orders/eject-4");

    expectUpstreamSuccess("/v1/orders/eject-5");
    org.junit.jupiter.api.Assertions.assertEquals("/v1/orders/eject-5", healthyUpstream.lastPath());

    expectUpstreamSuccess("/v1/orders/eject-6");
    org.junit.jupiter.api.Assertions.assertEquals("/v1/orders/eject-6", healthyUpstream.lastPath());
  }

  @Test
  void internalHealthEndpointReportsEjection() {
    expectUpstreamSuccess("/v1/orders/health-1");
    expectUpstreamFailure("/v1/orders/health-2");
    expectUpstreamSuccess("/v1/orders/health-3");
    expectUpstreamFailure("/v1/orders/health-4");

    webTestClient
        .get()
        .uri("/internal/v1/upstream-health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.pools[0].poolId")
        .isEqualTo(POOL_ID.toString())
        .jsonPath("$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].state")
        .isEqualTo("EJECTED")
        .jsonPath("$.pools[0].targets[?(@.targetId=='" + HEALTHY_TARGET_ID + "')].state")
        .isEqualTo("HEALTHY");
  }

  @Test
  void recoversAfterEjectionExpiresAndTargetAcceptsAgain() throws IOException {
    expectUpstreamSuccess("/v1/orders/recover-1");
    expectUpstreamFailure("/v1/orders/recover-2");
    expectUpstreamSuccess("/v1/orders/recover-3");
    expectUpstreamFailure("/v1/orders/recover-4");

    CLOCK.advance(Duration.ofSeconds(61));
    failingUpstream.resumeAccepting();

    expectUpstreamSuccess("/v1/orders/recover-5");
    expectUpstreamSuccess("/v1/orders/recover-6");

    webTestClient
        .get()
        .uri("/internal/v1/upstream-health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].state")
        .isEqualTo("HEALTHY")
        .jsonPath(
            "$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].consecutiveFailures")
        .isEqualTo(0);
  }

  private void expectUpstreamSuccess(String path) {
    webTestClient
        .get()
        .uri(path)
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void expectUpstreamFailure(String path) {
    webTestClient
        .get()
        .uri(path)
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("UPSTREAM_UNAVAILABLE");
  }

  static RuntimeConfig passiveHealthConfig() {
    BackendHealthPolicyConfig health = new BackendHealthPolicyConfig(2, 60, 50);
    List<UpstreamTargetReference> targets =
        List.of(
            new UpstreamTargetReference(HEALTHY_TARGET_ID, URI.create(healthyUpstream.url()), 1),
            new UpstreamTargetReference(FAILING_TARGET_ID, URI.create(failingUpstream.url()), 1));
    UpstreamConfig upstream = UpstreamConfig.roundRobin(POOL_ID, targets, health);
    RouteConfig route =
        new RouteConfig(
            "orders-route", "api.autoapi.local", "/v1/orders", Set.of(HttpMethod.GET), upstream);
    return new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of(route));
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayPassiveHealthIntegrationTest",
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
      context.getBeanFactory().registerSingleton("gatewayHealthClock", CLOCK);
      GatewayBootstrap.initializer(passiveHealthConfig()).initialize(context);
    }
  }
}
