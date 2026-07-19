package com.autoapi.controlplane.rollout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class RuntimeRolloutIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<String> GATEWAY_IDS = List.of("gateway-a", "gateway-b", "gateway-c");

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void progressiveRolloutManualFlow() {
    Bootstrap bootstrap = bootstrapApiWithThreeGateways();
    String projectId = bootstrap.projectId();
    String apiId = bootstrap.apiId();
    String groupId = createGatewayGroup(projectId, apiId);

    String stagesJson = progressiveStagesJson();
    String previewBody =
        postManagementJson(
            "/api/v1/management/projects/" + projectId + "/rollouts/preview",
            rolloutBody(groupId, 2, stagesJson));
    assertEquals(3, readJsonInt(previewBody, "eligibleGatewayCount"));
    assertStageCounts(previewBody, List.of(1, 2, 3));

    String createBody =
        postManagementJson(
            "/api/v1/management/projects/" + projectId + "/rollouts",
            rolloutBody(groupId, 2, stagesJson));
    String rolloutId = readJsonField(createBody, "id");

    String deterministicPreview =
        postManagementJson(
            "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/preview",
            "{}");
    List<String> previewSample = readJsonStringList(deterministicPreview, "sampleGatewayIds");
    List<String> rankedForRollout =
        RolloutCohortRanker.rankGateways(UUID.fromString(rolloutId), GATEWAY_IDS).stream()
            .map(RolloutCohortRanker.RankedGateway::gatewayId)
            .toList();
    assertEquals(rankedForRollout.subList(0, Math.min(3, rankedForRollout.size())), previewSample);

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/start", "{}");
    pollUntil("rollout running", () -> "RUNNING".equals(readRolloutStatus(projectId, rolloutId)));

    String firstCohortGateway = rankedForRollout.get(0);
    pollUntil(
        "first cohort assigned",
        () ->
            countAssignmentsWithStatus(projectId, rolloutId, "ASSIGNED") >= 1
                && assignmentHasGateway(projectId, rolloutId, firstCohortGateway, "ASSIGNED"));

    assertDesiredVersion(apiId, firstCohortGateway, 2, "ROLLOUT_ASSIGNMENT");

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/pause", "{}");
    pollUntil("rollout paused", () -> "PAUSED".equals(readRolloutStatus(projectId, rolloutId)));

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/resume", "{}");
    pollUntil("rollout resumed", () -> "RUNNING".equals(readRolloutStatus(projectId, rolloutId)));

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/advance", "{}");
    pollUntil(
        "stage expanded to two gateways",
        () -> countAssignmentsWithStatus(projectId, rolloutId, "ASSIGNED") >= 2);

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/advance", "{}");
    pollUntil(
        "stage expanded to three gateways",
        () -> countAssignmentsWithStatus(projectId, rolloutId, "ASSIGNED") >= 3);

    postManagementJson(
        "/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId + "/advance", "{}");
    pollUntil(
        "rollout succeeded", () -> "SUCCEEDED".equals(readRolloutStatus(projectId, rolloutId)));

    webTestClient
        .get()
        .uri("/api/v1/management/projects/" + projectId + "/gateway-groups/" + groupId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .isEqualTo(2);
  }

  private record Bootstrap(String projectId, String apiId) {}

  private Bootstrap bootstrapApiWithThreeGateways() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        readJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"rollout-it-" + suffix + "\",\"description\":\"phase12\"}"),
            "id");
    String apiId =
        readJsonField(
            postJson(
                "/api/v1/projects/" + projectId + "/apis",
                "{\"name\":\"orders-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
            "id");
    String poolId =
        readJsonField(
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
    addRoute(apiId, "/v1/orders", poolId);
    publishVersion(apiId, "version 1");
    activateVersion(apiId, 1);
    addRoute(apiId, "/v2/orders", poolId);
    publishVersion(apiId, "version 2");

    for (String gatewayId : GATEWAY_IDS) {
      registerGateway(gatewayId);
      setGatewayRolloutMetadata(gatewayId);
      sendHeartbeat(gatewayId);
    }

    return new Bootstrap(projectId, apiId);
  }

  private String createGatewayGroup(String projectId, String apiId) {
    return readJsonField(
        postManagementJson(
            "/api/v1/management/projects/" + projectId + "/gateway-groups",
            """
            {
              "apiId": "%s",
              "name": "prod-us-west",
              "description": "production gateways in us-west",
              "selectorJson": "{\\"matchLabels\\":{\\"region\\":\\"us-west\\",\\"environment\\":\\"production\\"}}",
              "enabled": true
            }
            """
                .formatted(apiId)),
        "id");
  }

  private static String progressiveStagesJson() {
    return """
        [
          {
            "percentage": 33,
            "minimumGatewayCount": 1,
            "requiredConvergedPercentage": 100,
            "maximumFailedGateways": 0,
            "maximumTimedOutGateways": 0,
            "requiredOnlinePercentage": 0,
            "observationDurationMs": 1000,
            "stageTimeoutMs": 60000
          },
          {
            "percentage": 50,
            "minimumGatewayCount": 1,
            "requiredConvergedPercentage": 100,
            "maximumFailedGateways": 0,
            "maximumTimedOutGateways": 0,
            "requiredOnlinePercentage": 0,
            "observationDurationMs": 1000,
            "stageTimeoutMs": 60000
          },
          {
            "percentage": 100,
            "minimumGatewayCount": 1,
            "requiredConvergedPercentage": 100,
            "maximumFailedGateways": 0,
            "maximumTimedOutGateways": 0,
            "requiredOnlinePercentage": 0,
            "observationDurationMs": 1000,
            "stageTimeoutMs": 60000
          }
        ]
        """;
  }

  private static String rolloutBody(String groupId, long targetVersion, String stagesJson) {
    return """
        {
          "gatewayGroupId": "%s",
          "targetVersion": %d,
          "strategy": "PROGRESSIVE_PERCENTAGE",
          "progressionMode": "MANUAL",
          "autoRollbackOnFailure": false,
          "cancelBehavior": "KEEP_CURRENT_ASSIGNMENTS",
          "stages": %s
        }
        """
        .formatted(groupId, targetVersion, stagesJson);
  }

  private void registerGateway(String gatewayId) {
    webTestClient
        .post()
        .uri("/api/v1/gateways/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "gatewayId": "%s",
              "gatewayGroup": "default",
              "softwareVersion": "0.1.0-SNAPSHOT",
              "startedAt": "2026-07-11T23:30:00Z"
            }
            """
                .formatted(gatewayId))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void setGatewayRolloutMetadata(String gatewayId) {
    databaseClient
        .sql(
            """
            UPDATE gateways
            SET admin_labels = :labels::jsonb,
                runtime_schema_version = 1
            WHERE id = :gatewayId
            """)
        .bind("gatewayId", gatewayId)
        .bind("labels", "{\"region\":\"us-west\",\"environment\":\"production\"}")
        .fetch()
        .rowsUpdated()
        .block();
  }

  private void sendHeartbeat(String gatewayId) {
    webTestClient
        .post()
        .uri("/api/v1/gateways/" + gatewayId + "/heartbeat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sentAt\":\"2026-07-11T23:32:00Z\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void publishVersion(String apiId, String message) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"" + message + "\"}")
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void activateVersion(String apiId, long version) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/" + version + "/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedDesiredVersion\":null}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void addRoute(String apiId, String pathPrefix, String poolId) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/routes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{"
                + "\"name\":\"orders-route-"
                + pathPrefix.replace("/", "-")
                + "\","
                + "\"host\":\"api.autoapi.local\","
                + "\"pathPrefix\":\""
                + pathPrefix
                + "\","
                + "\"methods\":[\"GET\",\"POST\"],"
                + "\"upstreamPoolId\":\""
                + poolId
                + "\","
                + "\"enabled\":true"
                + "}")
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void assertDesiredVersion(
      String apiId, String gatewayId, long version, String desiredSource) {
    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/desired?gatewayId=" + gatewayId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo((int) version)
        .jsonPath("$.desiredSource")
        .isEqualTo(desiredSource);
  }

  private String readRolloutStatus(String projectId, String rolloutId) {
    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/management/projects/" + projectId + "/rollouts/" + rolloutId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return readJsonField(body, "status");
  }

  private int countAssignmentsWithStatus(String projectId, String rolloutId, String status) {
    byte[] body =
        webTestClient
            .get()
            .uri(
                "/api/v1/management/projects/"
                    + projectId
                    + "/rollouts/"
                    + rolloutId
                    + "/assignments?limit=50")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      JsonNode node = MAPPER.readTree(body);
      int count = 0;
      for (JsonNode assignment : node) {
        if (status.equals(assignment.get("status").asText())) {
          count++;
        }
      }
      return count;
    } catch (Exception e) {
      fail(e);
      return 0;
    }
  }

  private boolean assignmentHasGateway(
      String projectId, String rolloutId, String gatewayId, String status) {
    byte[] body =
        webTestClient
            .get()
            .uri(
                "/api/v1/management/projects/"
                    + projectId
                    + "/rollouts/"
                    + rolloutId
                    + "/assignments?limit=50")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      for (JsonNode assignment : MAPPER.readTree(body)) {
        if (gatewayId.equals(assignment.get("gatewayId").asText())
            && status.equals(assignment.get("status").asText())) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      fail(e);
      return false;
    }
  }

  private static void assertStageCounts(String previewBody, List<Integer> expectedCounts) {
    try {
      JsonNode stages = MAPPER.readTree(previewBody).get("stageGatewayCounts");
      assertEquals(expectedCounts.size(), stages.size());
      for (int i = 0; i < expectedCounts.size(); i++) {
        assertEquals(expectedCounts.get(i), stages.get(i).get("gatewayCount").asInt());
      }
    } catch (Exception e) {
      fail(e);
    }
  }

  private static void pollUntil(String description, java.util.function.BooleanSupplier condition) {
    for (int attempt = 0; attempt < 30; attempt++) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail(e);
      }
    }
    fail("Timed out waiting for: " + description);
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

  private String postManagementJson(String uri, String body) {
    byte[] response =
        webTestClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return new String(response);
  }

  private static String readJsonField(byte[] body, String field) {
    try {
      return MAPPER.readTree(body).get(field).asText();
    } catch (Exception e) {
      fail(e);
      return null;
    }
  }

  private static String readJsonField(String body, String field) {
    return readJsonField(body.getBytes(), field);
  }

  private static int readJsonInt(String body, String field) {
    try {
      return MAPPER.readTree(body).get(field).asInt();
    } catch (Exception e) {
      fail(e);
      return 0;
    }
  }

  private static List<String> readJsonStringList(String body, String field) {
    try {
      JsonNode node = MAPPER.readTree(body).get(field);
      List<String> values = new ArrayList<>();
      node.forEach(item -> values.add(item.asText()));
      return values;
    } catch (Exception e) {
      fail(e);
      return List.of();
    }
  }
}
