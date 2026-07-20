package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.autoapi.support.ManagementAuthTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class PolicyBundleAssignmentIntegrationTest extends ControlPlaneIntegrationTest {

  private static final String ORG_ID = ManagementAuthTestSupport.DEFAULT_ORG_ID;

  @Autowired DatabaseClient databaseClient;
  @Autowired EffectivePolicyCache effectivePolicyCache;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
    effectivePolicyCache.invalidateAll();
  }

  @Test
  void upgradeAssignmentRevisionInvalidatesEffectivePolicy() {
    Bootstrap bootstrap = bootstrapApi();
    String bundleId = createBundle("cache-upgrade");
    createRevision(
        bundleId,
        """
        {
          "headers": { "X-Policy-Revision": "v1" },
          "rateLimit": {
            "limitCount": 1000,
            "windowSeconds": 60,
            "identitySource": "API_KEY",
            "redisFailureMode": "FAIL_OPEN"
          }
        }
        """);
    String assignmentId = assignOrganizationBundle(bundleId, 1);

    publishConfig(bootstrap.apiId(), "initial with bundle v1");

    webTestClient
        .get()
        .uri(
            "/api/v1/management/apis/"
                + bootstrap.apiId()
                + "/effective-policy?routeId="
                + bootstrap.routeId()
                + "&explain=true")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.headers['X-Policy-Revision']")
        .isEqualTo("v1")
        .jsonPath("$.rateLimit.limitCount")
        .isEqualTo(1000)
        .jsonPath("$.explanations[?(@.policyType=='headers')].winningRevision")
        .isEqualTo(1);

    createRevision(
        bundleId,
        """
        {
          "headers": { "X-Policy-Revision": "v2" },
          "rateLimit": {
            "limitCount": 750,
            "windowSeconds": 60,
            "identitySource": "API_KEY",
            "redisFailureMode": "FAIL_OPEN"
          }
        }
        """);

    webTestClient
        .patch()
        .uri("/api/v1/management/policy-bundle-assignments/" + assignmentId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"revisionNumber\":2}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(assignmentId)
        .jsonPath("$.revisionNumber")
        .isEqualTo(2)
        .jsonPath("$.scopeLevel")
        .isEqualTo("ORGANIZATION")
        .jsonPath("$.bundleId")
        .isEqualTo(bundleId);

    webTestClient
        .get()
        .uri(
            "/api/v1/management/apis/"
                + bootstrap.apiId()
                + "/effective-policy?routeId="
                + bootstrap.routeId()
                + "&explain=true")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.headers['X-Policy-Revision']")
        .isEqualTo("v2")
        .jsonPath("$.rateLimit.limitCount")
        .isEqualTo(750)
        .jsonPath("$.explanations[?(@.policyType=='headers')].winningRevision")
        .isEqualTo(2);

    publishConfig(bootstrap.apiId(), "after upgrade");

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + bootstrap.apiId() + "/versions/2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.effectivePolicies[0].policies.headers['X-Policy-Revision']")
        .isEqualTo("v2")
        .jsonPath("$.routes[0].rateLimit.limitCount")
        .isEqualTo(750);
  }

  @Test
  void duplicateOrganizationAssignmentReturns409() {
    String bundleId = createBundle("duplicate-assignment");
    createRevision(bundleId, "{\"timeout\":{\"timeoutMs\":5000}}");
    assignOrganizationBundle(bundleId, 1);

    webTestClient
        .post()
        .uri(
            "/api/v1/management/organizations/"
                + ORG_ID
                + "/policy-bundles/"
                + bundleId
                + "/assignments")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"revisionNumber\":1}")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("RESOURCE_CONFLICT");
  }

  @Test
  void viewerCannotUpgradeAssignment() throws Exception {
    Bootstrap bootstrap = bootstrapPublishedApi();
    String bundleId = createBundle("viewer-upgrade");
    createRevision(bundleId, "{\"timeout\":{\"timeoutMs\":4000}}");
    String assignmentId = assignOrganizationBundle(bundleId, 1);
    createRevision(bundleId, "{\"timeout\":{\"timeoutMs\":3000}}");

    String saId =
        extractJsonField(
            postJson(
                "/api/v1/management/organizations/" + ORG_ID + "/service-accounts",
                "{\"name\":\"assignment-viewer\",\"description\":\"viewer\",\"projectId\":\""
                    + bootstrap.projectId()
                    + "\"}"),
            "id");

    postJson(
        "/api/v1/management/projects/" + bootstrap.projectId() + "/role-bindings",
        "{\"principalType\":\"SERVICE_ACCOUNT\",\"principalId\":\""
            + saId
            + "\",\"role\":\"PROJECT_VIEWER\"}");

    String viewerToken =
        extractJsonField(
            postJson(
                "/api/v1/management/service-accounts/" + saId + "/credentials",
                "{\"name\":\"viewer\",\"scopes\":[\"project.read\"]}"),
            "token");

    webTestClient
        .mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
        .build()
        .patch()
        .uri("/api/v1/management/policy-bundle-assignments/" + assignmentId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"revisionNumber\":2}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  private String createBundle(String name) {
    return extractJsonField(
        postJson(
            "/api/v1/management/organizations/" + ORG_ID + "/policy-bundles",
            "{\"name\":\"" + name + "-" + UUID.randomUUID() + "\",\"description\":\"bundle\"}"),
        "id");
  }

  private void createRevision(String bundleId, String contentJson) {
    webTestClient
        .post()
        .uri(
            "/api/v1/management/organizations/"
                + ORG_ID
                + "/policy-bundles/"
                + bundleId
                + "/revisions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"revision\",\"content\":" + contentJson + "}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private String assignOrganizationBundle(String bundleId, int revisionNumber) {
    byte[] body =
        webTestClient
            .post()
            .uri(
                "/api/v1/management/organizations/"
                    + ORG_ID
                    + "/policy-bundles/"
                    + bundleId
                    + "/assignments")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"revisionNumber\":" + revisionNumber + "}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return extractJsonField(body, "id");
  }

  private Bootstrap bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"assignment-it-" + suffix + "\",\"description\":\"policy\"}"),
            "id");

    String apiId =
        extractJsonField(
            postJson(
                "/api/v1/projects/" + projectId + "/apis",
                "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
            "id");

    String poolId =
        extractJsonField(
            postJson(
                "/api/v1/apis/" + apiId + "/upstream-pools",
                "{\"name\":\"orders-v1\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
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
        extractJsonField(
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

    return new Bootstrap(projectId, apiId, routeId);
  }

  private void publishConfig(String apiId, String message) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"" + message + "\"}")
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private Bootstrap bootstrapPublishedApi() {
    Bootstrap bootstrap = bootstrapApi();
    publishConfig(bootstrap.apiId(), "initial");
    return bootstrap;
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

  private static String extractJsonField(byte[] body, String field) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Bootstrap(String projectId, String apiId, String routeId) {}
}
