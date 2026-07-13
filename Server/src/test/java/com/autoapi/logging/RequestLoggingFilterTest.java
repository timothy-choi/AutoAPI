package com.autoapi.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.proxy.GatewayAttributes;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

class RequestLoggingFilterTest {

  private static final byte[] MOCK_UPSTREAM_BODY =
      "{\"service\":\"upstream-v1\",\"method\":\"GET\",\"path\":\"/v1/orders/smoke\",\"requestId\":\"req-1\",\"receivedHost\":\"api.autoapi.local\",\"receivedForwardedHost\":\"api.autoapi.local\",\"receivedForwardedFor\":\"127.0.0.1\",\"receivedForwardedProto\":\"http\"}"
          .getBytes(StandardCharsets.UTF_8);

  private final CopyOnWriteArrayList<Throwable> droppedErrors = new CopyOnWriteArrayList<>();

  @BeforeEach
  void trackDroppedErrors() {
    Hooks.onErrorDropped(droppedErrors::add);
  }

  @AfterEach
  void resetDroppedErrors() {
    Hooks.resetOnErrorDropped();
  }

  @Test
  void stringRequestIdAttributeCompletesWithoutClassCast() {
    WebTestClient client = clientWithRequestId("req-string-id", successHandler());

    client
        .get()
        .uri("/v1/orders/smoke")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo(new String(MOCK_UPSTREAM_BODY, StandardCharsets.UTF_8));

    assertNoClassCastDropped();
  }

  @Test
  void missingRequestIdAttributeStillCompletes() {
    WebTestClient client = clientWithRequestId(null, successHandler());

    client
        .get()
        .uri("/v1/orders/smoke")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();

    assertNoClassCastDropped();
  }

  @Test
  void nonStringRequestIdAttributeUsesSafeConversion() {
    WebTestClient client = clientWithRequestId(Integer.valueOf(42), successHandler());

    client
        .get()
        .uri("/v1/orders/smoke")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();

    assertNoClassCastDropped();
  }

  @Test
  void errorResponseLoggingDoesNotThrow() {
    WebTestClient client =
        clientWithRequestId(
            "req-error",
            exchange -> {
              exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
              return exchange.getResponse().setComplete();
            });

    client
        .get()
        .uri("/v1/orders/smoke")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isUnauthorized();

    assertNoClassCastDropped();
  }

  @Test
  void cancellationDuringBodyWriteDoesNotDropClassCastException() {
    WebTestClient client =
        clientWithRequestId(
            "req-cancel",
            exchange ->
                exchange
                    .getResponse()
                    .writeWith(Flux.error(new RuntimeException("client cancelled"))));

    client
        .get()
        .uri("/v1/orders/smoke")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .is5xxServerError();

    assertTrue(
        droppedErrors.stream().noneMatch(error -> error instanceof ClassCastException),
        () -> "dropped errors: " + droppedErrors);
  }

  @Test
  void healthEndpointsBypassLoggingFilter() {
    WebTestClient client = clientWithRequestId("ignored", successHandler());

    client.get().uri("/readyz").exchange().expectStatus().isOk();
  }

  private WebTestClient clientWithRequestId(Object requestId, WebHandler terminal) {
    WebHandler composed =
        exchange ->
            new RequestLoggingFilter()
                .filter(
                    exchange,
                    filtered -> {
                      if (requestId != null) {
                        filtered.getAttributes().put(GatewayAttributes.REQUEST_ID, requestId);
                      }
                      return terminal.handle(filtered);
                    });
    return WebTestClient.bindToWebHandler(composed).build();
  }

  private static WebHandler successHandler() {
    return exchange -> writeSuccess(exchange);
  }

  private static Mono<Void> writeSuccess(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.OK);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    exchange.getResponse().getHeaders().setContentLength(MOCK_UPSTREAM_BODY.length);
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(MOCK_UPSTREAM_BODY)));
  }

  private void assertNoClassCastDropped() {
    assertTrue(
        droppedErrors.stream().noneMatch(error -> error instanceof ClassCastException),
        () -> "dropped errors: " + droppedErrors);
  }
}
