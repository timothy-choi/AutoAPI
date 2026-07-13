package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.support.ControllableClock;
import com.autoapi.web.ErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RetryingProxyExecutorTerminationTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID TARGET_V1 = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID TARGET_V2 = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000040");

  private RetryBudgetRegistry budgetRegistry;
  private HealthAwareTargetSelector targetSelector;
  private ErrorResponseWriter errorWriter;
  private GatewayProperties gatewayProperties;
  private AtomicInteger attemptCounter;

  @BeforeEach
  void setUp() {
    budgetRegistry =
        new RetryBudgetRegistry(ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
    targetSelector = mock(HealthAwareTargetSelector.class);
    errorWriter = new ErrorResponseWriter(new ObjectMapper());
    gatewayProperties = new GatewayProperties();
    attemptCounter = new AtomicInteger();
  }

  @Test
  void connectFailureThenRetrySuccessTerminates() {
    UpstreamAttemptExecutor attemptExecutor = mock(UpstreamAttemptExecutor.class);
    when(attemptExecutor.execute(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              int attempt = attemptCounter.incrementAndGet();
              TargetKey key =
                  attempt == 1
                      ? new TargetKey(API_ID, POOL_ID, TARGET_V1)
                      : new TargetKey(API_ID, POOL_ID, TARGET_V2);
              if (attempt == 1) {
                return Mono.just(
                    UpstreamAttemptExecutor.AttemptResult.failure(
                        connectFailure(), RetryFailureCategory.CONNECT_FAILURE, false, key));
              }
              return Mono.just(
                  UpstreamAttemptExecutor.AttemptResult.success(mockClientResponse(), key));
            });

    RetryingProxyExecutor executor = executor(attemptExecutor);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/retry").build());

    StepVerifier.create(
            executor.executeWithRetries(
                exchange,
                bundle(),
                route(retryPolicy()),
                exchange.getRequest(),
                "req-1",
                upstream(),
                upstream().targets()))
        .verifyComplete();

    assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    assertEquals(2, attemptCounter.get());
  }

  @Test
  void maxAttemptsReachedTerminatesWithBadGateway() {
    UpstreamAttemptExecutor attemptExecutor = mock(UpstreamAttemptExecutor.class);
    when(attemptExecutor.execute(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              attemptCounter.incrementAndGet();
              return Mono.just(
                  UpstreamAttemptExecutor.AttemptResult.failure(
                      connectFailure(),
                      RetryFailureCategory.CONNECT_FAILURE,
                      false,
                      new TargetKey(API_ID, POOL_ID, TARGET_V1)));
            });

    RetryingProxyExecutor executor = executor(attemptExecutor);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/fail").build());

    StepVerifier.create(
            executor.executeWithRetries(
                exchange,
                bundle(),
                route(retryPolicy()),
                exchange.getRequest(),
                "req-2",
                upstream(),
                upstream().targets()))
        .verifyComplete();

    assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
    assertEquals(2, attemptCounter.get());
  }

  @Test
  void budgetDenialTerminatesImmediatelyWithoutThirdAttempt() {
    RuntimeRetryPolicyConfig tightBudget =
        new RuntimeRetryPolicyConfig(
            POLICY_ID, 3, 1000, true, true, true, true, List.of("GET"), true, 10, 0, 10);
    RetryBudgetKey budgetKey = new RetryBudgetKey(API_ID, "orders-route", POLICY_ID);
    for (int i = 0; i < 10; i++) {
      budgetRegistry.recordOriginalRequest(budgetKey, tightBudget);
    }

    UpstreamAttemptExecutor attemptExecutor = mock(UpstreamAttemptExecutor.class);
    when(attemptExecutor.execute(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              attemptCounter.incrementAndGet();
              return Mono.just(
                  UpstreamAttemptExecutor.AttemptResult.failure(
                      connectFailure(),
                      RetryFailureCategory.CONNECT_FAILURE,
                      false,
                      new TargetKey(API_ID, POOL_ID, TARGET_V1)));
            });

    RetryingProxyExecutor executor = executor(attemptExecutor);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/budget").build());

    StepVerifier.create(
            executor.executeWithRetries(
                exchange,
                bundle(),
                route(tightBudget),
                exchange.getRequest(),
                "req-3",
                upstream(),
                upstream().targets()))
        .verifyComplete();

    assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
    assertEquals(2, attemptCounter.get());
  }

  @Test
  void unsafeMethodWithoutIdempotencyKeyTerminatesAfterOneAttempt() {
    UpstreamAttemptExecutor attemptExecutor = mock(UpstreamAttemptExecutor.class);
    when(attemptExecutor.execute(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              attemptCounter.incrementAndGet();
              return Mono.just(
                  UpstreamAttemptExecutor.AttemptResult.failure(
                      connectFailure(),
                      RetryFailureCategory.CONNECT_FAILURE,
                      false,
                      new TargetKey(API_ID, POOL_ID, TARGET_V1)));
            });

    RetryingProxyExecutor executor = executor(attemptExecutor);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("http://api.autoapi.local/v1/orders/post", "{}").build());

    StepVerifier.create(
            executor.executeWithRetries(
                exchange,
                bundle(),
                route(retryPolicy()),
                exchange.getRequest(),
                "req-4",
                upstream(),
                upstream().targets()))
        .verifyComplete();

    assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
    assertEquals(1, attemptCounter.get());
  }

  private RetryingProxyExecutor executor(UpstreamAttemptExecutor attemptExecutor) {
    @SuppressWarnings("unchecked")
    ObjectProvider<HealthAwareTargetSelector> selectorProvider = mock(ObjectProvider.class);
    when(selectorProvider.getIfAvailable()).thenReturn(targetSelector);
    @SuppressWarnings("unchecked")
    ObjectProvider<GatewayRetryMetrics> metricsProvider = mock(ObjectProvider.class);
    when(metricsProvider.getIfAvailable()).thenReturn(null);

    when(targetSelector.select(any(), any(), anyList(), any()))
        .thenReturn(new SelectedTarget(targetRef(TARGET_V1), false))
        .thenReturn(new SelectedTarget(targetRef(TARGET_V2), false));

    return new RetryingProxyExecutor(
        attemptExecutor,
        selectorProvider,
        budgetRegistry,
        metricsProvider,
        errorWriter,
        gatewayProperties);
  }

  private static Throwable connectFailure() {
    return new WebClientRequestException(
        new ConnectException("connection refused"),
        HttpMethod.GET,
        URI.create("http://upstream-v1:8080/v1/orders"),
        HttpHeaders.EMPTY);
  }

  private static ClientResponse mockClientResponse() {
    ClientResponse response = mock(ClientResponse.class);
    ClientResponse.Headers headers = mock(ClientResponse.Headers.class);
    when(response.statusCode()).thenReturn(HttpStatus.OK);
    when(response.headers()).thenReturn(headers);
    when(headers.asHttpHeaders()).thenReturn(new HttpHeaders());
    when(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class))
        .thenReturn(Flux.empty());
    return response;
  }

  private static ActiveRuntimeBundle bundle() {
    RuntimeConfig config = new RuntimeConfig(new GatewayConfig("127.0.0.1", 8080), List.of());
    return new ActiveRuntimeBundle(API_ID, 1, "hash", config);
  }

  private static RouteConfig route(RuntimeRetryPolicyConfig retry) {
    return new RouteConfig(
        "orders-route",
        "api.autoapi.local",
        "/v1/orders",
        Set.of(HttpMethod.GET, HttpMethod.POST),
        upstream(),
        null,
        null,
        retry);
  }

  private static RuntimeRetryPolicyConfig retryPolicy() {
    return new RuntimeRetryPolicyConfig(
        POLICY_ID, 2, 1000, true, true, true, true, List.of("GET", "POST"), true, 50, 2, 10);
  }

  private static UpstreamConfig upstream() {
    return UpstreamConfig.roundRobin(
        POOL_ID, List.of(targetRef(TARGET_V1), targetRef(TARGET_V2)), null);
  }

  private static UpstreamTargetReference targetRef(UUID id) {
    return new UpstreamTargetReference(id, URI.create("http://upstream-" + id + ":8080"), 1);
  }
}
