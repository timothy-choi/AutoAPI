package com.autoapi.controlplane.routepolicy;

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

class RoutePolicyBindingPreservationIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  private UUID apiId;
  private UUID routeId;
  private UUID rateLimitPolicyId;
  private UUID retryPolicyId;
  private UUID trafficSplitPolicyId;

  @BeforeEach
  void setUp() throws Exception {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);

    String projectId =
        extract(
            postJson("/api/v1/projects", "{\"name\":\"binding-" + UUID.randomUUID() + "\"}"), "id");
    apiId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/projects/" + projectId + "/apis",
                    "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
                "id"));
    UUID poolId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/upstream-pools",
                    "{\"name\":\"pool\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
                "id"));
    webTestClient
        .post()
        .uri("/api/v1/upstream-pools/{poolId}/targets", poolId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"url\":\"http://127.0.0.1:" + upstream.port() + "\",\"enabled\":true,\"weight\":1}")
        .exchange()
        .expectStatus()
        .isCreated();
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
                        .formatted(poolId)),
                "id"));
    rateLimitPolicyId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/rate-limit-policies",
                    """
                    {
                      "name": "limit",
                      "limitCount": 5,
                      "windowSeconds": 300,
                      "identitySource": "API_KEY",
                      "redisFailureMode": "FAIL_OPEN"
                    }
                    """),
                "id"));
    retryPolicyId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/retry-policies",
                    """
                    {
                      "name": "retry",
                      "maxAttempts": 2,
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
                    """),
                "id"));
    trafficSplitPolicyId =
        UUID.fromString(
            extract(
                postJson(
                    "/api/v1/apis/" + apiId + "/traffic-split-policies",
                    """
                    {
                      "name": "split",
                      "selectionKey": "REQUEST_ID",
                      "fallbackMode": "STRICT",
                      "enabled": true
                    }
                    """),
                "id"));
  }

  @Test
  void bindingTrafficSplitPreservesAuthenticationRateLimitAndRetry() {
    bindAuthAndRateLimit();
    bindRetry();
    bindTrafficSplit();

    assertBinding(true, rateLimitPolicyId, retryPolicyId, trafficSplitPolicyId);
  }

  @Test
  void bindingPoliciesInReverseOrderPreservesTrafficSplit() {
    bindTrafficSplit();
    bindAuthAndRateLimit();
    bindRetry();

    assertBinding(true, rateLimitPolicyId, retryPolicyId, trafficSplitPolicyId);
  }

  @Test
  void clearingTrafficSplitPreservesAuthenticationRateLimitAndRetry() {
    bindAuthAndRateLimit();
    bindRetry();
    bindTrafficSplit();

    webTestClient
        .delete()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .exchange()
        .expectStatus()
        .isOk();

    assertBinding(true, rateLimitPolicyId, retryPolicyId, null);
  }

  private void bindAuthAndRateLimit() {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/policy-binding", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"authenticationRequired\":true,\"rateLimitPolicyId\":\"" + rateLimitPolicyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.authenticationRequired")
        .isEqualTo(true);
  }

  private void bindRetry() {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/retry-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"retryPolicyId\":\"" + retryPolicyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void bindTrafficSplit() {
    webTestClient
        .put()
        .uri("/api/v1/routes/{routeId}/traffic-split-policy", routeId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"trafficSplitPolicyId\":\"" + trafficSplitPolicyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void assertBinding(
      boolean authenticationRequired,
      UUID expectedRateLimitPolicyId,
      UUID expectedRetryPolicyId,
      UUID expectedTrafficSplitPolicyId) {
    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/routes/{routeId}/policy-binding", routeId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      JsonNode json = MAPPER.readTree(body);
      org.junit.jupiter.api.Assertions.assertEquals(
          authenticationRequired, json.get("authenticationRequired").asBoolean());
      org.junit.jupiter.api.Assertions.assertEquals(
          expectedRateLimitPolicyId.toString(), json.get("rateLimitPolicyId").asText());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }

    var row =
        databaseClient
            .sql(
                """
                SELECT authentication_required, rate_limit_policy_id, retry_policy_id,
                       traffic_split_policy_id
                FROM route_policy_bindings
                WHERE route_id = :routeId
                """)
            .bind("routeId", routeId)
            .fetch()
            .one()
            .block();
    org.junit.jupiter.api.Assertions.assertNotNull(row);
    org.junit.jupiter.api.Assertions.assertEquals(
        authenticationRequired, row.get("authentication_required"));
    org.junit.jupiter.api.Assertions.assertEquals(
        expectedRateLimitPolicyId, row.get("rate_limit_policy_id"));
    org.junit.jupiter.api.Assertions.assertEquals(
        expectedRetryPolicyId, row.get("retry_policy_id"));
    org.junit.jupiter.api.Assertions.assertEquals(
        expectedTrafficSplitPolicyId, row.get("traffic_split_policy_id"));
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
    return MAPPER.readTree(body).get(field).asText();
  }
}
