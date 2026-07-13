package com.autoapi.gateway;

import com.autoapi.config.BackendHealthPolicyConfig;
import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetHealthState;
import com.autoapi.gateway.health.TargetKey;
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
  @Autowired private TargetHealthRegistry targetHealthRegistry;

  @BeforeEach
  void resetUpstreamsAndClock() throws IOException {
    CLOCK.setInstant(Instant.parse("2026-01-01T00:00:00Z"));
    healthyUpstream.resumeAccepting();
    healthyUpstream.respondWithStatus(200);
    failingUpstream.resumeAccepting();
    failingUpstream.stopAccepting();
  }

  @AfterAll
  static void shutdownUpstreams() {
    healthyUpstream.shutdown();
    failingUpstream.shutdown();
  }

  @Test
  void firstTransportFailureIncrementsInternalHealthCount() {
    expectUpstreamSuccess("/v1/orders/first-fail-1");
    expectUpstreamFailure("/v1/orders/first-fail-2");

    webTestClient
        .get()
        .uri("/internal/v1/upstream-health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(
            "$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].consecutiveFailures")
        .isEqualTo(1)
        .jsonPath("$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].state")
        .isEqualTo("HEALTHY")
        .jsonPath(
            "$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].lastFailureCategory")
        .isEqualTo("CONNECTION_REFUSED");
  }

  @Test
  void controlled502DoesNotRecordSuccessForSameAttempt() {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);

    driveUntilFailureCount(failingKey, 1);

    TargetHealthState state = targetHealthRegistry.getState(failingKey);
    org.junit.jupiter.api.Assertions.assertEquals(1, state.consecutiveQualifyingFailures());
    org.junit.jupiter.api.Assertions.assertEquals(
        com.autoapi.gateway.health.FailureCategory.CONNECTION_REFUSED, state.lastFailureCategory());
  }

  @Test
  void successOnHealthyPeerDoesNotResetFailingTargetCount() {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);

    driveUntilFailureCount(failingKey, 1);
    org.junit.jupiter.api.Assertions.assertEquals(
        1, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());

    expectUpstreamSuccess("/v1/orders/isolation-2");
    org.junit.jupiter.api.Assertions.assertEquals(
        1, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());
  }

  @Test
  void upstreamHttp500RecordsTransportSuccess() {
    TargetKey healthyKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, HEALTHY_TARGET_ID);
    targetHealthRegistry.recordFailure(
        healthyKey,
        com.autoapi.gateway.health.FailureCategory.CONNECTION_REFUSED,
        new com.autoapi.gateway.health.PassiveHealthPolicy(2, java.time.Duration.ofSeconds(60), 50),
        2);
    org.junit.jupiter.api.Assertions.assertEquals(
        1, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());

    healthyUpstream.respondWithStatus(500);
    boolean observedHttp500 = false;
    for (int attempt = 0; attempt < 6; attempt++) {
      var exchange =
          webTestClient
              .get()
              .uri("/v1/orders/http-500-" + attempt)
              .header(HttpHeaders.HOST, "api.autoapi.local")
              .exchange();
      if (exchange.returnResult(String.class).getStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
        observedHttp500 = true;
        break;
      }
    }

    org.junit.jupiter.api.Assertions.assertTrue(observedHttp500);
    org.junit.jupiter.api.Assertions.assertEquals(
        0, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());
    org.junit.jupiter.api.Assertions.assertTrue(
        healthyUpstream.lastPath().startsWith("/v1/orders/http-500"));
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

  private void driveUntilFailureCount(TargetKey failingKey, int expectedCount) {
    for (int attempt = 0;
        attempt < 8
            && targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures()
                < expectedCount;
        attempt++) {
      var result =
          webTestClient
              .get()
              .uri("/v1/orders/drive-failure-" + expectedCount + "-" + attempt)
              .header(HttpHeaders.HOST, "api.autoapi.local")
              .exchange()
              .returnResult(String.class);
      if (result.getStatus() != HttpStatus.BAD_GATEWAY && result.getStatus() != HttpStatus.OK) {
        org.junit.jupiter.api.Assertions.fail("Unexpected status " + result.getStatus());
      }
    }
    org.junit.jupiter.api.Assertions.assertEquals(
        expectedCount, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());
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
