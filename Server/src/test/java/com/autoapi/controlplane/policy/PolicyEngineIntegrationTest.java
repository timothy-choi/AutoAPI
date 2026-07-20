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

class PolicyEngineIntegrationTest extends ControlPlaneIntegrationTest {

  private static final String ORG_ID = ManagementAuthTestSupport.DEFAULT_ORG_ID;

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void apiOverrideWinsOverOrganizationBundleRateLimit() {
    Bootstrap bootstrap = bootstrapPublishedApi();
    String bundleId = createBundle("standard-public");
    createRevision(
        bundleId,
        """
        {
          "rateLimit": {
            "limitCount": 1000,
            "windowSeconds": 60,
            "identitySource": "API_KEY",
            "redisFailureMode": "FAIL_OPEN"
          }
        }
        """);
    assignOrganizationBundle(bundleId, 1);

    webTestClient
        .post()
        .uri("/api/v1/management/apis/" + bootstrap.apiId() + "/policy-overrides")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "policyType": "rateLimit",
              "mode": "OVERRIDE",
              "content": {
                "limitCount": 500,
                "windowSeconds": 60,
                "identitySource": "API_KEY",
                "redisFailureMode": "FAIL_OPEN"
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();

    webTestClient
        .get()
        .uri(
            "/api/v1/management/apis/"
                + bootstrap.apiId()
                + "/effective-policy?routeId="
                + bootstrap.routeId())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.rateLimit.limitCount")
        .isEqualTo(500);
  }

  @Test
  void headersMergeAcrossHierarchyLevels() {
    Bootstrap bootstrap = bootstrapPublishedApi();
    webTestClient
        .post()
        .uri("/api/v1/management/organizations/" + ORG_ID + "/policy-overrides")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "policyType": "headers",
              "mode": "MERGE",
              "content": { "X-Org": "org-value" }
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();
    createApiOverride(
        bootstrap.apiId(),
        """
        {
          "policyType": "headers",
          "mode": "MERGE",
          "content": { "X-Api": "api-value" }
        }
        """);

    webTestClient
        .get()
        .uri("/api/v1/management/apis/" + bootstrap.apiId() + "/effective-policy?explain=true")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.headers.X-Org")
        .isEqualTo("org-value")
        .jsonPath("$.headers.X-Api")
        .isEqualTo("api-value")
        .jsonPath("$.explanations[?(@.policyType=='headers')].winningLevel")
        .isEqualTo("API");
  }

  @Test
  void publishEmbedsFlattenedEffectivePolicyInGatewaySnapshot() {
    Bootstrap bootstrap = bootstrapPublishedApi();
    String bundleId = createBundle("high-throughput");
    createRevision(
        bundleId,
        """
        {
          "rateLimit": {
            "limitCount": 250,
            "windowSeconds": 60,
            "identitySource": "API_KEY",
            "redisFailureMode": "FAIL_OPEN"
          }
        }
        """);
    assignOrganizationBundle(bundleId, 1);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"policy overlay\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(2);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + bootstrap.apiId() + "/config/versions/2/activate")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + bootstrap.apiId() + "/versions/2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.effectivePolicies[0].policies.rateLimit.limitCount")
        .isEqualTo(250)
        .jsonPath("$.routes[0].rateLimit.limitCount")
        .isEqualTo(250);
  }

  @Test
  void dryRunEvaluateReturnsResolvedPolicy() {
    Bootstrap bootstrap = bootstrapPublishedApi();
    createApiOverride(
        bootstrap.apiId(),
        """
        {
          "policyType": "timeout",
          "mode": "OVERRIDE",
          "content": { "timeoutMs": 3000 }
        }
        """);

    webTestClient
        .post()
        .uri("/api/v1/management/policies/evaluate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{"
                + "\"apiId\":\""
                + bootstrap.apiId()
                + "\","
                + "\"routeId\":\""
                + bootstrap.routeId()
                + "\","
                + "\"explain\":true"
                + "}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.timeout.timeoutMs")
        .isEqualTo(3000)
        .jsonPath("$.explanations[?(@.policyType=='timeout')].winningLevel")
        .isEqualTo("API");
  }

  @Test
  void viewerCannotCreatePolicyBundles() throws Exception {
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"policy-rbac-" + UUID.randomUUID() + "\",\"description\":\"rbac\"}"),
            "id");

    String saId =
        extractJsonField(
            postJson(
                "/api/v1/management/organizations/" + ORG_ID + "/service-accounts",
                "{\"name\":\"policy-viewer\",\"description\":\"viewer\",\"projectId\":\""
                    + projectId
                    + "\"}"),
            "id");

    postJson(
        "/api/v1/management/projects/" + projectId + "/role-bindings",
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
        .post()
        .uri("/api/v1/management/organizations/" + ORG_ID + "/policy-bundles")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"blocked\",\"description\":\"viewer cannot create\"}")
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
        .bodyValue("{\"message\":\"initial\",\"content\":" + contentJson + "}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void assignOrganizationBundle(String bundleId, int revisionNumber) {
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
        .isOk();
  }

  private void createApiOverride(String apiId, String body) {
    webTestClient
        .post()
        .uri("/api/v1/management/apis/" + apiId + "/policy-overrides")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private Bootstrap bootstrapPublishedApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"policy-it-" + suffix + "\",\"description\":\"policy\"}"),
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

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"initial\"}")
        .exchange()
        .expectStatus()
        .isCreated();

    return new Bootstrap(projectId, apiId, routeId);
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
