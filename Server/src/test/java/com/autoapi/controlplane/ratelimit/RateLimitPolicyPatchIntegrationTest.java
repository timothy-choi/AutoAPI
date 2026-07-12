package com.autoapi.controlplane.ratelimit;

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

class RateLimitPolicyPatchIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void patchWindowSecondsOnlyPreservesOtherFields() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"windowSeconds\":10}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.windowSeconds")
        .isEqualTo(10)
        .jsonPath("$.limitCount")
        .isEqualTo(5)
        .jsonPath("$.identitySource")
        .isEqualTo("API_KEY")
        .jsonPath("$.redisFailureMode")
        .isEqualTo("FAIL_OPEN")
        .jsonPath("$.name")
        .isEqualTo("patch-policy")
        .jsonPath("$.enabled")
        .isEqualTo(true);
  }

  @Test
  void patchRedisFailureModeOnlyPreservesOtherFields() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"redisFailureMode\":\"FAIL_CLOSED\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.redisFailureMode")
        .isEqualTo("FAIL_CLOSED")
        .jsonPath("$.windowSeconds")
        .isEqualTo(300)
        .jsonPath("$.limitCount")
        .isEqualTo(5)
        .jsonPath("$.identitySource")
        .isEqualTo("API_KEY");
  }

  @Test
  void patchMultipleFields() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"limitCount\":7,\"windowSeconds\":60,\"enabled\":false}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.limitCount")
        .isEqualTo(7)
        .jsonPath("$.windowSeconds")
        .isEqualTo(60)
        .jsonPath("$.enabled")
        .isEqualTo(false)
        .jsonPath("$.redisFailureMode")
        .isEqualTo("FAIL_OPEN");
  }

  @Test
  void emptyPatchBodyPreservesExistingValues() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.windowSeconds")
        .isEqualTo(300)
        .jsonPath("$.limitCount")
        .isEqualTo(5)
        .jsonPath("$.redisFailureMode")
        .isEqualTo("FAIL_OPEN");
  }

  @Test
  void patchInvalidWindowRejected() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"windowSeconds\":0}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void patchInvalidLimitRejected() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"limitCount\":0}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void patchUnsupportedFailureModeRejected() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"redisFailureMode\":\"FAIL_LATER\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void patchMissingPolicyReturns404() {
    String apiId = bootstrapApi();
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + apiId + "/rate-limit-policies/" + UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"windowSeconds\":10}")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("RESOURCE_NOT_FOUND");
  }

  @Test
  void patchCrossApiPolicyAccessRejected() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    String otherApiId = bootstrapApi();
    webTestClient
        .patch()
        .uri("/api/v1/apis/" + otherApiId + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"windowSeconds\":10}")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("RESOURCE_NOT_FOUND");
  }

  @Test
  void publishedSnapshotRemainsImmutableAfterDraftPolicyUpdate() throws Exception {
    PolicyContext context = createPublishedPolicy(300);
    publishVersion(context.apiId(), "Version 1");

    webTestClient
        .get()
        .uri("/api/v1/apis/" + context.apiId() + "/config/versions/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].rateLimit.windowSeconds")
        .isEqualTo(300);

    webTestClient
        .patch()
        .uri("/api/v1/apis/" + context.apiId() + "/rate-limit-policies/" + context.policyId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"windowSeconds\":10}")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .get()
        .uri("/api/v1/apis/" + context.apiId() + "/config/versions/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].rateLimit.windowSeconds")
        .isEqualTo(300);

    publishVersion(context.apiId(), "Version 2");
    webTestClient
        .get()
        .uri("/api/v1/apis/" + context.apiId() + "/config/versions/2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].rateLimit.windowSeconds")
        .isEqualTo(10);
  }

  private PolicyContext createPublishedPolicy(int windowSeconds) throws Exception {
    Bootstrap bootstrap = bootstrapRoute();
    byte[] policyBody =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + bootstrap.apiId() + "/rate-limit-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "patch-policy",
                  "limitCount": 5,
                  "windowSeconds": %d,
                  "identitySource": "API_KEY",
                  "redisFailureMode": "FAIL_OPEN"
                }
                """
                    .formatted(windowSeconds))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    JsonNode policy = MAPPER.readTree(policyBody);
    String policyId = policy.get("id").asText();

    webTestClient
        .put()
        .uri("/api/v1/routes/" + bootstrap.routeId() + "/policy-binding")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"authenticationRequired\":true,\"rateLimitPolicyId\":\"" + policyId + "\"}")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/api-keys")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"patch-smoke-client\"}")
        .exchange()
        .expectStatus()
        .isCreated();

    return new PolicyContext(bootstrap.apiId(), policyId);
  }

  private void publishVersion(String apiId, String message) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/validate")
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"" + message + "\"}")
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private Bootstrap bootstrapRoute() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extract(postJson("/api/v1/projects", "{\"name\":\"patch-" + suffix + "\"}"), "id");
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
        extract(postJson("/api/v1/projects", "{\"name\":\"patch-api-" + suffix + "\"}"), "id");
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

  private record PolicyContext(String apiId, String policyId) {}

  private record Bootstrap(String apiId, String routeId) {}
}
