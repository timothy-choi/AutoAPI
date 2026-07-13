package com.autoapi.gateway.retry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.support.ControllableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

class GatewayInternalRetryHandlerTest {

  @Test
  void blankGatewayIdReportedAsUnknown() {
    RetryBudgetRegistry registry =
        new RetryBudgetRegistry(ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
    GatewayProperties properties = mock(GatewayProperties.class);
    when(properties.gatewayId()).thenReturn("   ");

    GatewayInternalRetryHandler handler = new GatewayInternalRetryHandler(registry, properties);
    RouterFunction<ServerResponse> route =
        RouterFunctions.route(GET("/internal/v1/retry-status"), handler::retryStatus);
    WebTestClient client = WebTestClient.bindToRouterFunction(route).build();

    client
        .get()
        .uri("/internal/v1/retry-status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.gatewayId")
        .isEqualTo("unknown")
        .jsonPath("$.budgets")
        .isArray()
        .jsonPath("$.budgets.length()")
        .isEqualTo(0);
  }

  @Test
  void configuredGatewayIdAndBudgetsReflectedInStatus() {
    RetryBudgetRegistry registry =
        new RetryBudgetRegistry(ControllableClock.fixed(Instant.parse("2026-01-01T00:00:00Z")));
    UUID apiId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    var policy =
        new com.autoapi.config.RuntimeRetryPolicyConfig(
            policyId, 2, 1000, true, true, true, true, List.of("GET"), true, 50, 2, 10);
    RetryBudgetKey key = new RetryBudgetKey(apiId, "orders-route", policyId);
    registry.recordOriginalRequest(key, policy);
    registry.tryConsumeRetry(key, policy);

    GatewayProperties properties = mock(GatewayProperties.class);
    when(properties.gatewayId()).thenReturn("gateway-a");

    GatewayInternalRetryHandler handler = new GatewayInternalRetryHandler(registry, properties);
    RouterFunction<ServerResponse> route =
        RouterFunctions.route(GET("/internal/v1/retry-status"), handler::retryStatus);
    WebTestClient client = WebTestClient.bindToRouterFunction(route).build();

    client
        .get()
        .uri("/internal/v1/retry-status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.gatewayId")
        .isEqualTo("gateway-a")
        .jsonPath("$.budgets[0].retriesUsed")
        .isEqualTo(1);
  }
}
