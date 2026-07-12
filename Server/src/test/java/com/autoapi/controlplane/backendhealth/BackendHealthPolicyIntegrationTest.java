package com.autoapi.controlplane.backendhealth;

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

class BackendHealthPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void createListGetAndDeleteBinding() throws Exception {
    Bootstrap bootstrap = bootstrapPool();
    String policyId = createPolicy(bootstrap.apiId(), "passive-health", 3, 60, 50, true);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/backend-health-policies")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .isEqualTo("passive-health")
        .jsonPath("$[0].consecutiveFailureThreshold")
        .isEqualTo(3);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/backend-health-policies/" + policyId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(policyId);

    webTestClient
        .put()
        .uri("/api/v1/upstream-pools/" + bootstrap.poolId() + "/backend-health-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backendHealthPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.backendHealthPolicyId")
        .isEqualTo(policyId);

    webTestClient
        .delete()
        .uri("/api/v1/upstream-pools/" + bootstrap.poolId() + "/backend-health-policy")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.backendHealthPolicyId")
        .doesNotExist();
  }

  @Test
  void patchUpdatesSelectedFields() throws Exception {
    Bootstrap bootstrap = bootstrapPool();
    String policyId = createPolicy(bootstrap.apiId(), "patch-policy", 2, 30, 50, true);

    webTestClient
        .patch()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/backend-health-policies/" + policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"consecutiveFailureThreshold\":5,\"maxEjectionPercent\":25,\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.consecutiveFailureThreshold")
        .isEqualTo(5)
        .jsonPath("$.maxEjectionPercent")
        .isEqualTo(25)
        .jsonPath("$.enabled")
        .isEqualTo(false)
        .jsonPath("$.ejectionDurationSeconds")
        .isEqualTo(30);
  }

  @Test
  void rejectsInvalidPolicyFields() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/backend-health-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-threshold",
              "consecutiveFailureThreshold": 0,
              "ejectionDurationSeconds": 30,
              "maxEjectionPercent": 50,
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/backend-health-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-percent",
              "consecutiveFailureThreshold": 2,
              "ejectionDurationSeconds": 30,
              "maxEjectionPercent": 101,
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void rejectsCrossApiBinding() throws Exception {
    Bootstrap bootstrap = bootstrapPool();
    String policyId = createPolicy(bootstrap.apiId(), "bind-policy", 2, 30, 50, true);
    String otherApiId = bootstrapApi();
    String otherPoolId =
        extract(
            postJson(
                "/api/v1/apis/" + otherApiId + "/upstream-pools",
                "{\"name\":\"other-pool\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
            "id");

    webTestClient
        .put()
        .uri("/api/v1/upstream-pools/" + otherPoolId + "/backend-health-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backendHealthPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void rejectsDisabledPolicyBinding() throws Exception {
    Bootstrap bootstrap = bootstrapPool();
    String policyId = createPolicy(bootstrap.apiId(), "disabled-policy", 2, 30, 50, false);

    webTestClient
        .put()
        .uri("/api/v1/upstream-pools/" + bootstrap.poolId() + "/backend-health-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backendHealthPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void publishValidationRejectsDisabledBoundPolicy() throws Exception {
    Bootstrap bootstrap = bootstrapPool();
    String policyId = createPolicy(bootstrap.apiId(), "validate-policy", 2, 30, 50, true);

    webTestClient
        .put()
        .uri("/api/v1/upstream-pools/" + bootstrap.poolId() + "/backend-health-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backendHealthPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .patch()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/backend-health-policies/" + policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/config/validate")
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[?(@.code == 'BACKEND_HEALTH_POLICY_DISABLED')]")
        .exists();
  }

  private String createPolicy(
      String apiId,
      String name,
      int threshold,
      int durationSeconds,
      int maxPercent,
      boolean enabled)
      throws Exception {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/backend-health-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "%s",
                  "consecutiveFailureThreshold": %d,
                  "ejectionDurationSeconds": %d,
                  "maxEjectionPercent": %d,
                  "enabled": %s
                }
                """
                    .formatted(name, threshold, durationSeconds, maxPercent, enabled))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    JsonNode json = MAPPER.readTree(body);
    return json.get("id").asText();
  }

  private Bootstrap bootstrapPool() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"bh-" + suffix + "\"}"), "id");
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
    return new Bootstrap(apiId, poolId);
  }

  private String bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"bh-api-" + suffix + "\"}"), "id");
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
        .is2xxSuccessful()
        .expectBody()
        .returnResult()
        .getResponseBody();
  }

  private static String extract(byte[] body, String field) {
    try {
      return MAPPER.readTree(body).get(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Bootstrap(String apiId, String poolId) {}
}
