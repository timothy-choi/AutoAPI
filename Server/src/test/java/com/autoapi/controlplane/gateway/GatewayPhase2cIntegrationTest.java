package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class GatewayPhase2cIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void registrationHeartbeatAckAndConvergence() {
    String apiId = bootstrapPublishedApi();

    webTestClient
        .post()
        .uri("/api/v1/gateways/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "gatewayId": "gateway-a",
              "gatewayGroup": "default",
              "softwareVersion": "0.1.0-SNAPSHOT",
              "startedAt": "2026-07-11T23:30:00Z",
              "metadata": {"configSource": "control-plane"}
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.gatewayId")
        .isEqualTo("gateway-a");

    webTestClient
        .post()
        .uri("/api/v1/gateways/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "gatewayId": "gateway-a",
              "gatewayGroup": "default",
              "softwareVersion": "0.1.0-SNAPSHOT",
              "startedAt": "2026-07-11T23:31:00Z",
              "metadata": {"configSource": "control-plane"}
            }
            """)
        .exchange()
        .expectStatus()
        .isOk();

    activateVersion(apiId, 1);

    UUID reportId = UUID.randomUUID();
    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 1,
              "contentHash": %s,
              "status": "ACK",
              "applyDurationMs": 12
            }
            """
                .formatted(reportId, apiId, quotedContentHash(apiId, 1)))
        .exchange()
        .expectStatus()
        .isAccepted();

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/heartbeat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {"sentAt":"2026-07-11T23:32:00Z"}
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.nextHeartbeatSeconds")
        .isEqualTo(10);

    webTestClient
        .post()
        .uri("/api/v1/gateways/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "gatewayId": "gateway-b",
              "gatewayGroup": "default",
              "softwareVersion": "0.1.0-SNAPSHOT",
              "startedAt": "2026-07-11T23:30:00Z"
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();

    UUID nackReportId = UUID.randomUUID();
    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-b/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 1,
              "contentHash": %s,
              "status": "NACK",
              "errorCode": "CONTENT_HASH_MISMATCH",
              "diagnostic": "hash mismatch during test",
              "applyDurationMs": 8
            }
            """
                .formatted(nackReportId, apiId, quotedContentHash(apiId, 1)))
        .exchange()
        .expectStatus()
        .isAccepted();

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-b/heartbeat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sentAt\":\"2026-07-11T23:32:00Z\"}")
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/convergence")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.derivedState")
        .isEqualTo("DEGRADED")
        .jsonPath("$.liveGatewayCount")
        .isEqualTo(2)
        .jsonPath("$.ackedGatewayCount")
        .isEqualTo(1)
        .jsonPath("$.nackedGatewayCount")
        .isEqualTo(1);

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-b/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 1,
              "contentHash": %s,
              "status": "ACK",
              "applyDurationMs": 10
            }
            """
                .formatted(UUID.randomUUID(), apiId, quotedContentHash(apiId, 1)))
        .exchange()
        .expectStatus()
        .isAccepted();

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/convergence")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.derivedState")
        .isEqualTo("CONVERGED")
        .jsonPath("$.ackedGatewayCount")
        .isEqualTo(2);

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 1,
              "contentHash": %s,
              "status": "ACK",
              "applyDurationMs": 12
            }
            """
                .formatted(reportId, apiId, quotedContentHash(apiId, 1)))
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.idempotent")
        .isEqualTo(true);

    webTestClient
        .post()
        .uri("/api/v1/gateways/unknown/heartbeat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sentAt\":\"2026-07-11T23:32:00Z\"}")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("GATEWAY_NOT_REGISTERED");
  }

  @Test
  void nackPreservesActiveVersion() {
    String apiId = bootstrapPublishedApi();
    registerGateway("gateway-a");
    activateVersion(apiId, 1);

    UUID ackReport = UUID.randomUUID();
    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 1,
              "contentHash": %s,
              "status": "ACK",
              "applyDurationMs": 5
            }
            """
                .formatted(ackReport, apiId, quotedContentHash(apiId, 1)))
        .exchange()
        .expectStatus()
        .isAccepted();

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/heartbeat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sentAt\":\"2026-07-11T23:32:00Z\"}")
        .exchange()
        .expectStatus()
        .isOk();

    addRoute(apiId, "/v2/orders");
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"version 2\"}")
        .exchange()
        .expectStatus()
        .isCreated();
    activateVersion(apiId, 2);

    webTestClient
        .post()
        .uri("/api/v1/gateways/gateway-a/config-status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "reportId": "%s",
              "apiId": "%s",
              "version": 2,
              "contentHash": %s,
              "status": "NACK",
              "errorCode": "UNSUPPORTED_RUNTIME_FEATURE",
              "diagnostic": "unsupported",
              "applyDurationMs": 3
            }
            """
                .formatted(UUID.randomUUID(), apiId, quotedContentHash(apiId, 2)))
        .exchange()
        .expectStatus()
        .isAccepted();

    webTestClient
        .get()
        .uri("/api/v1/gateways/gateway-a")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.apiStatuses[0].activeVersion")
        .isEqualTo(1)
        .jsonPath("$.apiStatuses[0].lastAttemptedVersion")
        .isEqualTo(2)
        .jsonPath("$.apiStatuses[0].lastStatus")
        .isEqualTo("NACK");
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

  private String bootstrapPublishedApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"phase2c-" + suffix + "\",\"description\":\"demo\"}"),
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
    addRoute(apiId, "/v1/orders", poolId);
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"Initial configuration\"}")
        .exchange()
        .expectStatus()
        .isCreated();
    return apiId;
  }

  private void activateVersion(String apiId, long version) {
    Long expectedDesiredVersion = readDesiredConfigVersion(apiId);
    String body =
        expectedDesiredVersion == null
            ? "{\"expectedDesiredVersion\":null}"
            : "{\"expectedDesiredVersion\":" + expectedDesiredVersion + "}";
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/" + version + "/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk();
  }

  private Long readDesiredConfigVersion(String apiId) {
    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/apis/" + apiId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
      if (node.hasNonNull("desiredConfigVersion")) {
        return node.get("desiredConfigVersion").asLong();
      }
      return null;
    } catch (Exception e) {
      org.junit.jupiter.api.Assertions.fail(e);
      return null;
    }
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

  private void addRoute(String apiId, String pathPrefix) {
    String poolId =
        extractJsonField(
            webTestClient
                .get()
                .uri("/api/v1/apis/" + apiId + "/upstream-pools")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody(),
            "id",
            true);
    addRoute(apiId, pathPrefix, poolId);
  }

  private String quotedContentHash(String apiId, long version) {
    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/apis/" + apiId + "/config/versions/" + version)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return "\"" + extractJsonField(body, "contentHash") + "\"";
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

  private static String extractJsonField(byte[] body, String field) {
    return extractJsonField(body, field, false);
  }

  private static String extractJsonField(byte[] body, String field, boolean fromArray) {
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
      if (fromArray) {
        return node.get(0).get(field).asText();
      }
      return node.get(field).asText();
    } catch (Exception e) {
      org.junit.jupiter.api.Assertions.fail(e);
      return null;
    }
  }
}
