package com.autoapi.controlplane.ratelimit;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class RateLimitPolicyIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void createValidPolicy() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/rate-limit-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "orders-client-limit",
              "limitCount": 100,
              "windowSeconds": 60,
              "identitySource": "API_KEY",
              "redisFailureMode": "FAIL_OPEN"
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.limitCount")
        .isEqualTo(100);
  }

  @Test
  void rejectsInvalidLimitAndWindow() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/rate-limit-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-limit",
              "limitCount": 0,
              "windowSeconds": 60,
              "identitySource": "API_KEY",
              "redisFailureMode": "FAIL_OPEN"
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/rate-limit-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-window",
              "limitCount": 10,
              "windowSeconds": 0,
              "identitySource": "API_KEY",
              "redisFailureMode": "FAIL_OPEN"
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void rejectsUnsupportedFields() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/rate-limit-policies")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "bad-source",
              "limitCount": 10,
              "windowSeconds": 10,
              "identitySource": "IP",
              "redisFailureMode": "FAIL_OPEN"
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void rejectsRateLimitBindingWithoutAuthentication() throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    String policyId = createPolicy(bootstrap.apiId());

    webTestClient
        .put()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/policy-binding")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"authenticationRequired\":false,\"rateLimitPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private String createPolicy(String apiId) throws Exception {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/rate-limit-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "bind-policy",
                  "limitCount": 5,
                  "windowSeconds": 10,
                  "identitySource": "API_KEY",
                  "redisFailureMode": "FAIL_OPEN"
                }
                """)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return MAPPER.readTree(body).get("id").asText();
  }

  private Bootstrap bootstrapRoute() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"rl-" + suffix + "\"}"), "id");
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
                "{"
                    + "\"name\":\"orders-route\","
                    + "\"host\":\"api.autoapi.local\","
                    + "\"pathPrefix\":\"/v1/orders\","
                    + "\"methods\":[\"GET\"],"
                    + "\"upstreamPoolId\":\""
                    + poolId
                    + "\","
                    + "\"enabled\":true"
                    + "}"),
            "id");
    return new Bootstrap(apiId, routeId);
  }

  private String bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"rl-api-" + suffix + "\"}"), "id");
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

  private record Bootstrap(String apiId, String routeId) {}
}
