package com.autoapi.controlplane.traffic;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class TrafficSplitPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  private UUID apiId;
  private UUID stablePoolId;
  private UUID canaryPoolId;
  private UUID routeId;

  @BeforeEach
  void setUpApi() throws Exception {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
    webTestClient =
        webTestClient.mutate().responseTimeout(java.time.Duration.ofSeconds(30)).build();

    String projectId =
        extract(
            postJson("/api/v1/projects", "{\"name\":\"phase7-" + UUID.randomUUID() + "\"}"), "id");
    apiId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/projects/" + projectId + "/apis",
                    "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
                "id"));

    stablePoolId = createPool("stable-pool", "http://stable-v1:8080");
    canaryPoolId = createPool("canary-pool", "http://canary-v1:8080");

    routeId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/routes",
                    """
                    {
                      "name": "orders-route",
                      "host": "api.autoapi.local",
                      "pathPrefix": "/v1/orders",
                      "methods": ["GET"],
                      "upstreamPoolId": "%s",
                      "enabled": true
                    }
                    """
                        .formatted(stablePoolId)),
                "id"));
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
  void bindMissingPolicyReturns404() {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"trafficSplitPolicyId\":\"" + UUID.randomUUID() + "\"}")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("RESOURCE_NOT_FOUND");
  }

  @Test
  void publishedSnapshotRemainsImmutableAfterDraftPolicyUpdate() throws Exception {
    UUID policyId =
        createPolicy("immutable-split", "HEADER", "X-AutoAPI-Test-User", "FALLBACK_TO_PRIMARY");
    addDestination(policyId, "stable", stablePoolId, 80, 0, true);
    UUID canaryDestinationId = addDestination(policyId, "canary", canaryPoolId, 20, 1, false);
    bindPolicy(routeId, policyId);
    publishVersion("Version 1");

    webTestClient
        .get()
        .uri("/api/v1/apis/{apiId}/config/versions/1", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].trafficSplit.destinations[?(@.name=='canary')].weight")
        .isEqualTo(20);

    webTestClient
        .patch()
        .uri(
            "/api/v1/traffic-split-policies/{policyId}/destinations/{destinationId}",
            policyId,
            canaryDestinationId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"weight\":5}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.weight")
        .isEqualTo(5);

    webTestClient
        .get()
        .uri("/api/v1/apis/{apiId}/config/versions/1", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].trafficSplit.destinations[?(@.name=='canary')].weight")
        .isEqualTo(20);

    publishVersion("Version 2");

    webTestClient
        .get()
        .uri("/api/v1/apis/{apiId}/config/versions/2", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].trafficSplit.destinations[?(@.name=='canary')].weight")
        .isEqualTo(5);
  }

  @Test
  void rejectsForbiddenHeaderSelectionKey() {
    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies", apiId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-header",
              "selectionKey": "HEADER",
              "selectionKeyName": "Authorization",
              "fallbackMode": "STRICT",
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void rejectsDisabledPolicyBinding() {
    UUID policyId = createPolicy("disabled", "REQUEST_ID", null, "STRICT");
    webTestClient
        .patch()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies/{policyId}", apiId, policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.enabled")
        .isEqualTo(false);

    webTestClient
        .get()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies/{policyId}", apiId, policyId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.enabled")
        .isEqualTo(false)
        .jsonPath("$.selectionKey")
        .isEqualTo("REQUEST_ID");

    addDestination(policyId, "stable", stablePoolId, 50, 0, true);
    addDestination(policyId, "canary", canaryPoolId, 50, 1, false);

    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"trafficSplitPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void patchEnabledFalsePersistsAndPreservesOtherFields() {
    UUID policyId =
        createPolicy("patch-enabled", "HEADER", "X-AutoAPI-Test-User", "FALLBACK_TO_PRIMARY");

    webTestClient
        .patch()
        .uri("/api/v1/apis/{apiId}/traffic-split-policies/{policyId}", apiId, policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.enabled")
        .isEqualTo(false)
        .jsonPath("$.selectionKey")
        .isEqualTo("HEADER")
        .jsonPath("$.selectionKeyName")
        .isEqualTo("X-AutoAPI-Test-User")
        .jsonPath("$.fallbackMode")
        .isEqualTo("FALLBACK_TO_PRIMARY");
  }

  @Test
  void rejectsCrossApiPolicyBinding() throws Exception {
    String otherProjectId =
        extract(
            postJson("/api/v1/projects", "{\"name\":\"other-" + UUID.randomUUID() + "\"}"), "id");
    UUID otherApiId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/projects/" + otherProjectId + "/apis",
                    "{\"name\":\"other-api\",\"host\":\"other.local\",\"basePath\":\"/\"}"),
                "id"));
    UUID otherPolicyId =
        createPolicyOnApi(otherApiId, "other-policy", "REQUEST_ID", null, "STRICT");

    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"trafficSplitPolicyId\":\"" + otherPolicyId + "\"}")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void rejectsDirectPoolAndTrafficSplitConflictAtPublication() {
    UUID policyId = createPolicy("conflict-policy", "REQUEST_ID", null, "STRICT");
    addDestination(policyId, "stable", stablePoolId, 50, 0, true);
    addDestination(policyId, "canary", canaryPoolId, 50, 1, false);
    bindPolicy(routeId, policyId);

    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/validate", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(true);

    databaseClient
        .sql("UPDATE routes SET upstream_pool_id = :poolId WHERE id = :routeId")
        .bind("poolId", stablePoolId)
        .bind("routeId", routeId)
        .fetch()
        .rowsUpdated()
        .block();

    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/validate", apiId)
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(false)
        .jsonPath("$.errors[?(@.code == 'ROUTE_TRAFFIC_SPLIT_CONFLICT')]")
        .exists();
  }

  @Test
  void publicationRejectsPrimaryFallbackWithoutExactlyOnePrimary() {
    UUID policyId = createPolicy("primary-policy", "REQUEST_ID", null, "FALLBACK_TO_PRIMARY");
    addDestination(policyId, "stable", stablePoolId, 50, 0, false);
    addDestination(policyId, "canary", canaryPoolId, 50, 1, false);
    bindPolicy(routeId, policyId);

    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/validate", apiId)
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(false)
        .jsonPath("$.errors[?(@.code == 'TRAFFIC_SPLIT_PRIMARY_REQUIRED')]")
        .exists();
  }

  private UUID createPool(String name, String targetUrl) throws Exception {
    UUID poolId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/upstream-pools",
                    "{\"name\":\"" + name + "\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
                "id"));

    webTestClient
        .post()
        .uri("/api/v1/upstream-pools/{poolId}/targets", poolId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"url\":\"" + targetUrl + "\",\"enabled\":true,\"weight\":1}")
        .exchange()
        .expectStatus()
        .isCreated();
    return poolId;
  }

  private UUID createPolicy(
      String name, String selectionKey, String selectionKeyName, String fallbackMode) {
    return createPolicyOnApi(apiId, name, selectionKey, selectionKeyName, fallbackMode);
  }

  private UUID createPolicyOnApi(
      UUID targetApiId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode) {
    String body =
        selectionKeyName == null
            ? """
            {
              "name": "%s",
              "selectionKey": "%s",
              "fallbackMode": "%s",
              "enabled": true
            }
            """
                .formatted(name, selectionKey, fallbackMode)
            : """
            {
              "name": "%s",
              "selectionKey": "%s",
              "selectionKeyName": "%s",
              "fallbackMode": "%s",
              "enabled": true
            }
            """
                .formatted(name, selectionKey, selectionKeyName, fallbackMode);

    try {
      return UUID.fromString(
          extract(postJson("/api/v1/apis/" + targetApiId + "/traffic-split-policies", body), "id"));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private UUID addDestination(
      UUID policyId, String name, UUID poolId, int weight, int priority, boolean primary) {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/traffic-split-policies/{policyId}/destinations", policyId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "%s",
                  "upstreamPoolId": "%s",
                  "weight": %d,
                  "priority": %d,
                  "primary": %s
                }
                """
                    .formatted(name, poolId, weight, priority, primary))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      return UUID.fromString(extract(body, "id"));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void publishVersion(String message) {
    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/validate", apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(true);
    webTestClient
        .post()
        .uri("/api/v1/apis/{apiId}/config/versions", apiId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"" + message + "\"}")
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void bindPolicy(UUID routeId, UUID policyId) {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"trafficSplitPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.trafficSplitPolicyId")
        .isEqualTo(policyId.toString());
  }

  private byte[] postJson(String uri, String body) {
    return webTestClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .returnResult()
        .getResponseBody();
  }

  private String extract(byte[] body, String field) throws Exception {
    JsonNode json = MAPPER.readTree(body);
    return json.get(field).asText();
  }
}
