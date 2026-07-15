package com.autoapi.controlplane.retry;

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

class RetryPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void createListGetPatchAndRemoveBinding() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId(), "safe-orders-retry", 2, 1000, true);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/retry-policies")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .isEqualTo("safe-orders-retry")
        .jsonPath("$[0].maxAttempts")
        .isEqualTo(2);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/retry-policies/" + policyId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(policyId);

    webTestClient
        .put()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/retry-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"retryPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.retryPolicyId")
        .isEqualTo(policyId);

    webTestClient
        .delete()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/retry-policy")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.retryPolicyId")
        .doesNotExist();
  }

  @Test
  void patchUpdatesSelectedFields() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId(), "patch-retry", 2, 1000, true);

    webTestClient
        .patch()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/retry-policies/" + policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"maxAttempts\":3,\"budgetPercent\":30,\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.maxAttempts")
        .isEqualTo(3)
        .jsonPath("$.budgetPercent")
        .isEqualTo(30)
        .jsonPath("$.enabled")
        .isEqualTo(false)
        .jsonPath("$.perAttemptTimeoutMs")
        .isEqualTo(1000);
  }

  @Test
  void rejectsInvalidPolicyFields() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/retry-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-attempts",
              "maxAttempts": 0,
              "perAttemptTimeoutMs": 1000,
              "retryOnConnectFailure": true,
              "retryOnConnectionReset": true,
              "retryOnDnsFailure": true,
              "retryOnResponseTimeout": true,
              "retryableMethods": ["GET"],
              "requireIdempotencyKeyForUnsafeMethods": true,
              "budgetPercent": 20,
              "budgetMinRetriesPerSecond": 2,
              "budgetWindowSeconds": 10,
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/retry-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "post-without-idempotency",
              "maxAttempts": 2,
              "perAttemptTimeoutMs": 1000,
              "retryOnConnectFailure": true,
              "retryOnConnectionReset": true,
              "retryOnDnsFailure": true,
              "retryOnResponseTimeout": true,
              "retryableMethods": ["POST"],
              "requireIdempotencyKeyForUnsafeMethods": false,
              "budgetPercent": 20,
              "budgetMinRetriesPerSecond": 2,
              "budgetWindowSeconds": 10,
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void rejectsCrossApiBinding() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId(), "bind-policy", 2, 1000, true);
    String otherApiId = bootstrapApi();
    String otherRouteId = createRoute(otherApiId);

    webTestClient
        .put()
        .uri("/api/v1/routes/" + otherRouteId + "/retry-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"retryPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void rejectsDisabledPolicyBinding() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId(), "disabled-retry", 2, 1000, false);

    webTestClient
        .put()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/retry-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"retryPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  private String createPolicy(
      String apiId, String name, int maxAttempts, int timeoutMs, boolean enabled) throws Exception {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/retry-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "%s",
                  "maxAttempts": %d,
                  "perAttemptTimeoutMs": %d,
                  "retryOnConnectFailure": true,
                  "retryOnConnectionReset": true,
                  "retryOnDnsFailure": true,
                  "retryOnResponseTimeout": true,
                  "retryableMethods": ["GET", "POST"],
                  "requireIdempotencyKeyForUnsafeMethods": true,
                  "budgetPercent": 20,
                  "budgetMinRetriesPerSecond": 2,
                  "budgetWindowSeconds": 10,
                  "enabled": %s
                }
                """
                    .formatted(name, maxAttempts, timeoutMs, enabled))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    JsonNode json = MAPPER.readTree(body);
    return json.get("id").asText();
  }

  private Bootstrap bootstrapRoute() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"retry-" + suffix + "\"}"), "id");
    String apiId =
        extract(
            postJson(
                "/api/v1/projects/" + projectId + "/apis",
                "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
            "id");
    String poolId =
        extract(
            postJson(
                "/api/v1/apis/" + apiId + "/upstream-pools",
                "{\"name\":\"pool\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
            "id");
    webTestClient
        .post()
        .uri("/api/v1/upstream-pools/" + poolId + "/targets")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"url\":\"http://127.0.0.1:" + upstream.port() + "\",\"enabled\":true,\"weight\":1}")
        .exchange()
        .expectStatus()
        .isCreated();
    String routeId =
        extract(
            postJson(
                "/api/v1/apis/" + apiId + "/routes",
                "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\",\"POST\"],\"upstreamPoolId\":\""
                    + poolId
                    + "\",\"enabled\":true}"),
            "id");
    return new Bootstrap(apiId, poolId, routeId);
  }

  private String createRoute(String apiId) {
    String poolId =
        extract(
            postJson(
                "/api/v1/apis/" + apiId + "/upstream-pools",
                "{\"name\":\"other-pool\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
            "id");
    webTestClient
        .post()
        .uri("/api/v1/upstream-pools/" + poolId + "/targets")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"url\":\"http://127.0.0.1:" + upstream.port() + "\",\"enabled\":true,\"weight\":1}")
        .exchange()
        .expectStatus()
        .isCreated();
    return extract(
        postJson(
            "/api/v1/apis/" + apiId + "/routes",
            "{\"name\":\"other-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/other\",\"methods\":[\"GET\"],\"upstreamPoolId\":\""
                + poolId
                + "\",\"enabled\":true}"),
        "id");
  }

  private String bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"retry-api-" + suffix + "\"}"), "id");
    return extract(
        postJson(
            "/api/v1/projects/" + projectId + "/apis",
            "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
        "id");
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

  private String extract(byte[] body, String field) {
    try {
      return MAPPER.readTree(body).get(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Bootstrap(String apiId, String poolId, String routeId) {}
}
