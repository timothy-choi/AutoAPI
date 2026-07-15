package com.autoapi.gateway.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.proxy.GatewayAttributes;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class UpstreamAttemptExecutorBodyTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final String BODY = "{\"path\":\"/v1/orders/baseline\"}";

  private DisposableServer upstream;
  private UpstreamAttemptExecutor executor;

  @BeforeEach
  void setUp() {
    upstream =
        HttpServer.create()
            .port(0)
            .route(
                routes ->
                    routes.get(
                        "/v1/orders/baseline",
                        (request, response) ->
                            response
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(BODY.length()))
                                .status(200)
                                .sendString(Mono.just(BODY))))
            .bindNow();

    WebClient webClient = WebClient.builder().build();
    @SuppressWarnings("unchecked")
    ObjectProvider<com.autoapi.gateway.health.TargetHealthRegistry> healthRegistryProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(healthRegistryProvider.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<com.autoapi.gateway.health.GatewayUpstreamHealthMetrics> metricsProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(metricsProvider.getIfAvailable()).thenReturn(null);

    executor =
        new UpstreamAttemptExecutor(
            webClient,
            new FailureClassifier(),
            healthRegistryProvider,
            metricsProvider,
            "test-gateway");
  }

  @AfterEach
  void tearDown() {
    if (upstream != null) {
      upstream.disposeNow();
    }
  }

  @Test
  void buffersUpstreamBodyForDeferredWrite() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/baseline").build());
    exchange.getAttributes().put(GatewayAttributes.REQUEST_ID, "req-body");

    URI targetUri = URI.create("http://127.0.0.1:" + upstream.port() + "/v1/orders/baseline");
    TargetKey targetKey = new TargetKey(API_ID, POOL_ID, TARGET_ID);

    StepVerifier.create(
            executor.execute(
                exchange,
                exchange.getRequest(),
                targetUri,
                "127.0.0.1:" + upstream.port(),
                "req-body",
                targetKey,
                null,
                2,
                "route-1",
                null,
                Duration.ofSeconds(5)))
        .assertNext(
            result -> {
              assertTrue(result instanceof UpstreamAttemptExecutor.AttemptResult.Success);
              UpstreamAttemptExecutor.AttemptResult.Success success =
                  (UpstreamAttemptExecutor.AttemptResult.Success) result;
              assertEquals(HttpStatus.OK, success.statusCode());
              assertEquals(1, success.body().size());
              DataBuffer buffer = success.body().getFirst();
              byte[] bytes = new byte[buffer.readableByteCount()];
              buffer.read(bytes);
              assertEquals(BODY, new String(bytes, StandardCharsets.UTF_8));
            })
        .verifyComplete();
  }

  @Test
  void retryExecutorWritesBufferedBodyToClient() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://api.autoapi.local/v1/orders/baseline").build());
    exchange.getAttributes().put(GatewayAttributes.REQUEST_ID, "req-write");

    URI targetUri = URI.create("http://127.0.0.1:" + upstream.port() + "/v1/orders/baseline");
    TargetKey targetKey = new TargetKey(API_ID, POOL_ID, TARGET_ID);

    StepVerifier.create(
            executor
                .execute(
                    exchange,
                    exchange.getRequest(),
                    targetUri,
                    "127.0.0.1:" + upstream.port(),
                    "req-write",
                    targetKey,
                    null,
                    2,
                    "route-1",
                    null,
                    Duration.ofSeconds(5))
                .flatMap(
                    result -> {
                      UpstreamAttemptExecutor.AttemptResult.Success success =
                          (UpstreamAttemptExecutor.AttemptResult.Success) result;
                      exchange.getResponse().setStatusCode(success.statusCode());
                      exchange.getResponse().getHeaders().addAll(success.headers());
                      return exchange
                          .getResponse()
                          .writeWith(
                              success.body().isEmpty()
                                  ? reactor.core.publisher.Flux.empty()
                                  : reactor.core.publisher.Flux.fromIterable(success.body()));
                    }))
        .verifyComplete();

    assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    DataBuffer written = exchange.getResponse().getBody().blockFirst(Duration.ofSeconds(2));
    assertTrue(written != null);
    byte[] bytes = new byte[written.readableByteCount()];
    written.read(bytes);
    assertEquals(BODY, new String(bytes, StandardCharsets.UTF_8));
  }
}
