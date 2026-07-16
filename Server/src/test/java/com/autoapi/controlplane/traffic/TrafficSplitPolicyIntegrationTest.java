package com.autoapi.controlplane.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TrafficSplitPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private UUID apiId;
  private UUID stablePoolId;
  private UUID canaryPoolId;
  private UUID routeId;

  @BeforeEach
  void setUpApi() {
    webTestClient =
        webTestClient.mutate().responseTimeout(java.time.Duration.ofSeconds(30)).build();
    var project =
        webTestClient
            .post()
            .uri("/api/v1/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("name", "phase7-" + UUID.randomUUID()))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    UUID projectId = UUID.fromString(project.get("id").toString());

    var api =
        webTestClient
            .post()
            .uri("/api/v1/projects/{projectId}/apis", projectId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("name", "orders-api", "host", "api.autoapi.local", "basePath", "/"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    apiId = UUID.fromString(api.get("id").toString());

    stablePoolId = createPool("stable-pool", "http://stable-v1:8080");
    canaryPoolId = createPool("canary-pool", "http://canary-v1:8080");

    var route =
        webTestClient
            .post()
            .uri("/api/v1/apis/{apiId}/routes", apiId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of(
                    "name",
                    "orders-route",
                    "host",
                    "api.autoapi.local",
                    "pathPrefix",
                    "/v1/orders",
                    "methods",
                    new String[] {"GET"},
                    "upstreamPoolId",
                    stablePoolId.toString(),
                    "enabled",
                    true))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    routeId = UUID.fromString(route.get("id").toString());
  }

  @Test
  void createPolicyAddDestinationsBindAndValidate() {
    UUID policyId =
        createPolicy("orders-canary", "HEADER", "X-AutoAPI-Test-User", "FALLBACK_TO_PRIMARY");
    addDestination(policyId, "stable", stablePoolId, 80, 0, true);
    addDestination(policyId, "canary", canaryPoolId, 20, 1, false);
    bindPolicy(routeId, policyId);

    webTestClient
        .get()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies/{policyId}", apiId, policyId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.destinations.length()")
        .isEqualTo(2);

    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/validate", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(true);
  }

  @Test
  void rejectsForbiddenHeaderSelectionKey() {
    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies", apiId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "name",
                "bad-header",
                "selectionKey",
                "HEADER",
                "selectionKeyName",
                "Authorization",
                "fallbackMode",
                "STRICT",
                "enabled",
                true))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void rejectsDisabledPolicyBinding() {
    UUID policyId = createPolicy("disabled", "REQUEST_ID", null, "STRICT");
    webTestClient
        .patch()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies/{policyId}", apiId, policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("enabled", false))
        .exchange()
        .expectStatus()
        .isOk();
    addDestination(policyId, "stable", stablePoolId, 50, 0, true);
    addDestination(policyId, "canary", canaryPoolId, 50, 1, false);

    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("trafficSplitPolicyId", policyId.toString()))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private UUID createPool(String name, String targetUrl) {
    var pool =
        webTestClient
            .post()
            .uri("/api/v1/apis/{apiId}/upstream-pools", apiId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("name", name, "loadBalancing", "ROUND_ROBIN"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    UUID poolId = UUID.fromString(pool.get("id").toString());
    webTestClient
        .post()
        .uri("/api/v1/upstream-pools/{poolId}/targets", poolId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("url", targetUrl, "enabled", true, "weight", 1))
        .exchange()
        .expectStatus()
        .isCreated();
    return poolId;
  }

  private UUID createPolicy(
      String name, String selectionKey, String selectionKeyName, String fallbackMode) {
    Map<String, Object> body =
        selectionKeyName == null
            ? Map.of(
                "name", name,
                "selectionKey", selectionKey,
                "fallbackMode", fallbackMode,
                "enabled", true)
            : Map.of(
                "name", name,
                "selectionKey", selectionKey,
                "selectionKeyName", selectionKeyName,
                "fallbackMode", fallbackMode,
                "enabled", true);
    var response =
        webTestClient
            .post()
            .uri("/api/v1/apis/{apiId}/traffic-split-policies", apiId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    assertThat(response).isNotNull();
    return UUID.fromString(response.get("id").toString());
  }

  private void addDestination(
      UUID policyId, String name, UUID poolId, int weight, int priority, boolean primary) {
    webTestClient
        .post()
        .uri("/api/v1/traffic-split-policies/{policyId}/destinations", policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "name", name,
                "upstreamPoolId", poolId.toString(),
                "weight", weight,
                "priority", priority,
                "primary", primary))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void bindPolicy(UUID routeId, UUID policyId) {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("trafficSplitPolicyId", policyId.toString()))
        .exchange()
        .expectStatus()
        .isOk();
  }
}
