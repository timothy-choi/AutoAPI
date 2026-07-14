package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.BackendHealthPolicyConfig;
import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.support.ControllableClock;
import com.autoapi.support.ControllableTestUpstream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class UpstreamAttemptExecutorHealthTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID FAILING_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID HEALTHY_TARGET_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");

  private ControllableTestUpstream hangingUpstream;
  private ControllableTestUpstream healthyUpstream;
  private TargetHealthRegistry registry;
  private UpstreamAttemptExecutor executor;
  private PassiveHealthPolicy healthPolicy;

  @BeforeEach
  void setUp() throws IOException {
    hangingUpstream = ControllableTestUpstream.start();
    healthyUpstream = ControllableTestUpstream.start();
    registry =
        new TargetHealthRegistry(ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
    healthPolicy = PassiveHealthPolicy.from(new BackendHealthPolicyConfig(2, 60, 50));

    @SuppressWarnings("unchecked")
    ObjectProvider<TargetHealthRegistry> healthRegistryProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(healthRegistryProvider.getIfAvailable()).thenReturn(registry);
    @SuppressWarnings("unchecked")
    ObjectProvider<com.autoapi.gateway.health.GatewayUpstreamHealthMetrics> metricsProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(metricsProvider.getIfAvailable()).thenReturn(null);

    executor =
        new UpstreamAttemptExecutor(
            WebClient.builder().build(),
            new FailureClassifier(),
            healthRegistryProvider,
            metricsProvider,
            "test-gateway");
  }

  @AfterEach
  void tearDown() {
    hangingUpstream.shutdown();
    healthyUpstream.shutdown();
  }

  @Test
  void responseTimeoutRecordsHealthFailureBeforeRetryDecision() {
    hangingUpstream.hangOnRequests();
    TargetKey failingKey = new TargetKey(API_ID, POOL_ID, FAILING_TARGET_ID);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/timeout").build());

    StepVerifier.create(
            executor.execute(
                exchange,
                exchange.getRequest(),
                URI.create(hangingUpstream.url() + "/v1/orders/timeout"),
                hangingUpstream.url().substring("http://".length()),
                "req-timeout",
                failingKey,
                healthPolicy,
                2,
                "orders-route",
                null,
                Duration.ofMillis(200)))
        .assertNext(
            result -> {
              assertTrue(result instanceof UpstreamAttemptExecutor.AttemptResult.Failure);
              UpstreamAttemptExecutor.AttemptResult.Failure failure =
                  (UpstreamAttemptExecutor.AttemptResult.Failure) result;
              assertEquals(RetryFailureCategory.RESPONSE_TIMEOUT, failure.category());
            })
        .verifyComplete();

    assertEquals(1, registry.getState(failingKey).consecutiveQualifyingFailures());
    assertEquals(
        FailureCategory.RESPONSE_TIMEOUT, registry.getState(failingKey).lastFailureCategory());
  }

  @Test
  void retrySuccessOnSecondAttemptDoesNotResetFirstTargetFailure() {
    TargetKey failingKey = new TargetKey(API_ID, POOL_ID, FAILING_TARGET_ID);
    TargetKey healthyKey = new TargetKey(API_ID, POOL_ID, HEALTHY_TARGET_ID);

    registry.recordFailure(failingKey, FailureCategory.RESPONSE_TIMEOUT, healthPolicy, 2);
    assertEquals(1, registry.getState(failingKey).consecutiveQualifyingFailures());

    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/success").build());

    StepVerifier.create(
            executor.execute(
                exchange,
                exchange.getRequest(),
                URI.create(healthyUpstream.url() + "/v1/orders/success"),
                healthyUpstream.url().substring("http://".length()),
                "req-success",
                healthyKey,
                healthPolicy,
                2,
                "orders-route",
                null,
                Duration.ofSeconds(2)))
        .assertNext(
            result -> assertTrue(result instanceof UpstreamAttemptExecutor.AttemptResult.Success))
        .verifyComplete();

    assertEquals(1, registry.getState(failingKey).consecutiveQualifyingFailures());
    assertEquals(0, registry.getState(healthyKey).consecutiveQualifyingFailures());
  }
}
