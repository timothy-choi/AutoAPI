package com.autoapi.controlplane.gatewayconfig;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class GatewayConfigIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void desiredConfigNotSetBeforeActivation() {
    String apiId = bootstrapApiWithPublishedVersion();

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/desired")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("DESIRED_CONFIG_NOT_SET");
  }

  @Test
  void getDesiredMetadataAfterActivation() {
    String apiId = bootstrapApiWithPublishedVersion();
    activateVersion(apiId, 1);

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/desired")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.apiId")
        .isEqualTo(apiId)
        .jsonPath("$.version")
        .isEqualTo(1)
        .jsonPath("$.contentHash")
        .isNotEmpty()
        .jsonPath("$.snapshotUrl")
        .isEqualTo("/api/v1/gateway-config/" + apiId + "/versions/1");
  }

  @Test
  void desiredResponseIncludesEtagHeader() {
    String apiId = bootstrapApiWithPublishedVersion();
    activateVersion(apiId, 1);

    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/gateway-config/" + apiId + "/desired")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(HttpHeaders.ETAG)
            .expectBody()
            .returnResult()
            .getResponseBody();

    String contentHash = extractJsonField(body, "contentHash");

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/desired")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ETAG, "\"" + contentHash + "\"");
  }

  @Test
  void ifNoneMatchReturnsNotModifiedForDesired() {
    String apiId = bootstrapApiWithPublishedVersion();
    activateVersion(apiId, 1);

    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/gateway-config/" + apiId + "/desired")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    String contentHash = extractJsonField(body, "contentHash");

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/desired")
        .header(HttpHeaders.IF_NONE_MATCH, "\"" + contentHash + "\"")
        .exchange()
        .expectStatus()
        .isNotModified()
        .expectHeader()
        .valueEquals(HttpHeaders.ETAG, "\"" + contentHash + "\"")
        .expectBody()
        .isEmpty();
  }

  @Test
  void getSnapshotReturnsJsonWithCacheHeaders() {
    String apiId = bootstrapApiWithPublishedVersion();
    activateVersion(apiId, 1);

    byte[] desiredBody =
        webTestClient
            .get()
            .uri("/api/v1/gateway-config/" + apiId + "/desired")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    String contentHash = extractJsonField(desiredBody, "contentHash");

    webTestClient
        .get()
        .uri("/api/v1/gateway-config/" + apiId + "/versions/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ETAG, "\"" + contentHash + "\"")
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "private, immutable")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.apiId")
        .isEqualTo(apiId)
        .jsonPath("$.version")
        .isEqualTo(1)
        .jsonPath("$.contentHash")
        .isEqualTo(contentHash)
        .jsonPath("$.routes[0].pathPrefix")
        .isEqualTo("/v1/orders");
  }

  private void activateVersion(String apiId, long version) {
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/" + version + "/activate")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private String bootstrapApiWithPublishedVersion() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"gateway-config-it-" + suffix + "\",\"description\":\"demo\"}"),
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

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/routes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{"
                + "\"name\":\"orders-route\","
                + "\"host\":\"api.autoapi.local\","
                + "\"pathPrefix\":\"/v1/orders\","
                + "\"methods\":[\"GET\",\"POST\"],"
                + "\"upstreamPoolId\":\""
                + poolId
                + "\","
                + "\"enabled\":true"
                + "}")
        .exchange()
        .expectStatus()
        .isCreated();

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
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get(field).asText();
    } catch (Exception e) {
      org.junit.jupiter.api.Assertions.fail(e);
      return null;
    }
  }
}
