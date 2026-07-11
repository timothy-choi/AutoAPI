package com.autoapi.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class GatewayReadinessEndpointTest {

  private GatewayReadiness readiness;
  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    readiness = mock(GatewayReadiness.class);
    RouterFunction<ServerResponse> healthRoutes =
        new GatewayWebConfiguration().healthRoutes(readiness);
    webTestClient = WebTestClient.bindToRouterFunction(healthRoutes).build();
  }

  @Test
  void readyzReturns200WhenReady() {
    when(readiness.isReady()).thenReturn(Mono.just(true));

    webTestClient
        .get()
        .uri("/readyz")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void readyzReturns503WhenNotReady() {
    when(readiness.isReady()).thenReturn(Mono.just(false));

    webTestClient
        .get()
        .uri("/readyz")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("DOWN");
  }

  @Test
  void readyzReturns503WhenReadinessPublisherFails() {
    when(readiness.isReady()).thenReturn(Mono.error(new IllegalStateException("unexpected")));

    webTestClient
        .get()
        .uri("/readyz")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("DOWN");
  }

  @Test
  void healthzDoesNotInvokeReadinessEvaluation() {
    webTestClient
        .get()
        .uri("/healthz")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");

    verify(readiness, never()).isReady();
  }
}
