package com.autoapi.controlplane.circuitbreaker;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class CircuitBreakerPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void createListGetPatchBindAndUnbind() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId(), "orders-breaker");

    webTestClient
        .get()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/circuit-breaker-policies")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .isEqualTo("orders-breaker")
        .jsonPath("$[0].failureThreshold")
        .isEqualTo(3);

    webTestClient
        .put()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/circuit-breaker-policy")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"circuitBreakerPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.circuitBreakerPolicyId")
        .isEqualTo(policyId);

    webTestClient
        .patch()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/circuit-breaker-policies/" + policyId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"failureThreshold\":5,\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.failureThreshold")
        .isEqualTo(5)
        .jsonPath("$.enabled")
        .isEqualTo(false);

    webTestClient
        .delete()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/circuit-breaker-policy")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.circuitBreakerPolicyId")
        .doesNotExist();
  }

  @Test
  void rejectsZeroRollingWindow() throws Exception {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/circuit-breaker-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-window",
              "failureThreshold": 2,
              "rollingWindowSeconds": 0,
              "openDurationSeconds": 5,
              "halfOpenMaxRequests": 1,
              "successThreshold": 1,
              "failurePredicate": {
                "countHttp5xx": true,
                "countConnectFailure": true,
                "countConnectTimeout": true,
                "countReadTimeout": true,
                "countTlsFailure": true,
                "countTransportException": true,
                "countHttp429": false
              },
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private String createPolicy(String apiId, String name) throws Exception {
    byte[] response =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/circuit-breaker-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "%s",
                  "failureThreshold": 3,
                  "rollingWindowSeconds": 30,
                  "openDurationSeconds": 10,
                  "halfOpenMaxRequests": 2,
                  "successThreshold": 1,
                  "failurePredicate": {
                    "countHttp5xx": true,
                    "countConnectFailure": true,
                    "countConnectTimeout": true,
                    "countReadTimeout": true,
                    "countTlsFailure": true,
                    "countTransportException": true,
                    "countHttp429": false
                  },
                  "enabled": true
                }
                """
                    .formatted(name))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return MAPPER.readTree(response).get("id").asText();
  }

  private Bootstrap bootstrapRoute() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"cb-" + suffix + "\"}"), "id");
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
                "{\"name\":\"orders-route\",\"host\":\"api.autoapi.local\",\"pathPrefix\":\"/v1/orders\",\"methods\":[\"GET\"],\"upstreamPoolId\":\""
                    + poolId
                    + "\",\"enabled\":true}"),
            "id");
    return new Bootstrap(apiId, poolId, routeId);
  }

  private String bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"cb-api-" + suffix + "\"}"), "id");
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
