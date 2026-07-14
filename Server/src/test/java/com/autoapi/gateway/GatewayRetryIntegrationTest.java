package com.autoapi.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.autoapi.config.BackendHealthPolicyConfig;
import com.autoapi.config.GatewayBootstrap;
import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetHealthState;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.support.ControllableClock;
import com.autoapi.support.ControllableTestUpstream;
import java.io.IOException;
import java.net.URI;
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
      "autoapi.gateway.api-id=" + GatewayRetryIntegrationTest.API_ID
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayRetryIntegrationTest.Initializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GatewayRetryIntegrationTest {

  static final String API_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID HEALTHY_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID FAILING_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID RETRY_POLICY_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000040");

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
  void resetUpstreams() throws IOException {
    healthyUpstream.resumeAccepting();
    healthyUpstream.resumeResponses();
    healthyUpstream.respondWithStatus(200);
    failingUpstream.resumeAccepting();
    failingUpstream.resumeResponses();
    failingUpstream.respondWithStatus(200);
    failingUpstream.stopAccepting();
  }

  @AfterAll
  static void shutdownUpstreams() {
    healthyUpstream.shutdown();
    failingUpstream.shutdown();
  }

  @Test
  void retriesToHealthyTargetAfterTransportFailure() {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);
    TargetKey healthyKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, HEALTHY_TARGET_ID);

    webTestClient
        .get()
        .uri("/v1/orders/retry-success")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();

    TargetHealthState failingState = targetHealthRegistry.getState(failingKey);
    assertEquals(1, failingState.consecutiveQualifyingFailures());
    assertEquals(FailureCategory.CONNECTION_REFUSED, failingState.lastFailureCategory());
    assertEquals(0, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());
  }

  @Test
  void retrySuccessDoesNotResetFailedTarget() {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);
    TargetKey healthyKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, HEALTHY_TARGET_ID);

    webTestClient
        .get()
        .uri("/v1/orders/retry-isolation-seed")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();
    assertEquals(1, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());

    targetHealthRegistry.recordSuccess(healthyKey);
    assertEquals(1, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());
    assertEquals(0, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());
  }

  @Test
  void responseTimeoutOnFirstAttemptRecordsHealthBeforeRetrySuccess() throws IOException {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);
    TargetKey healthyKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, HEALTHY_TARGET_ID);

    failingUpstream.resumeAccepting();
    failingUpstream.hangOnRequests();

    webTestClient
        .get()
        .uri("/v1/orders/retry-timeout-failover")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();

    TargetHealthState failingState = targetHealthRegistry.getState(failingKey);
    assertEquals(1, failingState.consecutiveQualifyingFailures());
    assertEquals(FailureCategory.RESPONSE_TIMEOUT, failingState.lastFailureCategory());
    assertEquals(0, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());
  }

  @Test
  void internalHealthEndpointReportsFailedTargetAfterRetryFailover() throws IOException {
    failingUpstream.resumeAccepting();
    failingUpstream.hangOnRequests();

    webTestClient
        .get()
        .uri("/v1/orders/retry-health-endpoint")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();

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
        .isEqualTo(1)
        .jsonPath(
            "$.pools[0].targets[?(@.targetId=='" + FAILING_TARGET_ID + "')].lastFailureCategory")
        .isEqualTo("RESPONSE_TIMEOUT");
  }

  @Test
  void terminalFirstAttemptFailureRecordsHealthWhenRetryDenied() {
    TargetKey failingKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, FAILING_TARGET_ID);

    webTestClient
        .post()
        .uri("/v1/orders/retry-terminal-post")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .bodyValue("{\"x\":1}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.BAD_GATEWAY);

    assertEquals(1, targetHealthRegistry.getState(failingKey).consecutiveQualifyingFailures());
  }

  @Test
  void upstreamHttp500RecordsTransportSuccessWithRetryEnabled() {
    TargetKey healthyKey = new TargetKey(UUID.fromString(API_ID), POOL_ID, HEALTHY_TARGET_ID);
    targetHealthRegistry.recordFailure(
        healthyKey,
        FailureCategory.CONNECTION_REFUSED,
        new com.autoapi.gateway.health.PassiveHealthPolicy(2, java.time.Duration.ofSeconds(60), 50),
        2);
    assertEquals(1, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());

    healthyUpstream.respondWithStatus(500);
    webTestClient
        .get()
        .uri("/v1/orders/retry-http-500")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    assertEquals(0, targetHealthRegistry.getState(healthyKey).consecutiveQualifyingFailures());
  }

  @Test
  void postWithoutIdempotencyKeyDoesNotRetry() {
    webTestClient
        .post()
        .uri("/v1/orders/retry-post-no-key")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .bodyValue("{\"x\":1}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void postWithIdempotencyKeyMayRetry() {
    webTestClient
        .post()
        .uri("/v1/orders/retry-post-with-key")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .header("Idempotency-Key", "retry-test-key-1")
        .bodyValue("{\"x\":1}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  static RuntimeConfig retryConfig() {
    BackendHealthPolicyConfig health = new BackendHealthPolicyConfig(2, 60, 50);
    RuntimeRetryPolicyConfig retry =
        new RuntimeRetryPolicyConfig(
            RETRY_POLICY_ID,
            2,
            1000,
            true,
            true,
            true,
            true,
            List.of("GET", "POST"),
            true,
            50,
            2,
            10);
    List<UpstreamTargetReference> targets =
        List.of(
            new UpstreamTargetReference(FAILING_TARGET_ID, URI.create(failingUpstream.url()), 1),
            new UpstreamTargetReference(HEALTHY_TARGET_ID, URI.create(healthyUpstream.url()), 1));
    UpstreamConfig upstream = UpstreamConfig.roundRobin(POOL_ID, targets, health);
    RouteConfig route =
        new RouteConfig(
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET, HttpMethod.POST),
            upstream,
            null,
            null,
            retry);
    return new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of(route));
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayRetryIntegrationTest",
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
      context
          .getBeanFactory()
          .registerSingleton(
              "gatewayHealthClock", ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
      GatewayBootstrap.initializer(retryConfig()).initialize(context);
    }
  }
}
