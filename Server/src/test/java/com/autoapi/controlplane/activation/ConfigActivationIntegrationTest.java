package com.autoapi.controlplane.activation;

import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ConfigActivationIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    databaseClient.sql("DELETE FROM config_versions").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_targets").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM upstream_pools").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM apis").fetch().rowsUpdated().block();
    databaseClient.sql("DELETE FROM projects").fetch().rowsUpdated().block();
  }

  @Test
  void activateVersionSetsDesiredConfigVersion() {
    String apiId = bootstrapApiWithPublishedVersion("/v1/orders");

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedDesiredVersion\":null}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredVersion")
        .isEqualTo(1);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .isEqualTo(1);
  }

  @Test
  void rollbackToPriorVersion() {
    String apiId = bootstrapApiWithPublishedVersion("/v1/orders");

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();

    addRoute(apiId, "/v2/orders");

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"Second version\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(2);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/2/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedDesiredVersion\":1}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredVersion")
        .isEqualTo(2);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedDesiredVersion\":2}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredVersion")
        .isEqualTo(1);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .isEqualTo(1);
  }

  @Test
  void desiredVersionConflictWithWrongExpectedDesiredVersion() {
    String apiId = bootstrapApiWithPublishedVersion("/v1/orders");

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();

    addRoute(apiId, "/v2/orders");

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"Second version\"}")
        .exchange()
        .expectStatus()
        .isCreated();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/2/activate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedDesiredVersion\":99}")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("DESIRED_VERSION_CONFLICT");

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .isEqualTo(1);
  }

  @Test
  void activateWithNullExpectedDesiredVersionWhenCurrentIsNull() {
    String apiId = bootstrapApiWithPublishedVersion("/v1/orders");

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .doesNotExist();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1/activate")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredVersion")
        .isEqualTo(1);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.desiredConfigVersion")
        .isEqualTo(1);
  }

  private String bootstrapApiWithPublishedVersion(String pathPrefix) {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"activation-it-" + suffix + "\",\"description\":\"demo\"}"),
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

    addRoute(apiId, pathPrefix, poolId);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/validate")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.valid")
        .isEqualTo(true);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"Initial configuration\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(1);

    return apiId;
  }

  private void addRoute(String apiId, String pathPrefix) {
    addRoute(apiId, pathPrefix, fetchFirstPoolId(apiId));
  }

  private String fetchFirstPoolId(String apiId) {
    byte[] body =
        webTestClient
            .get()
            .uri("/api/v1/apis/" + apiId + "/upstream-pools")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readTree(body)
          .get(0)
          .get("id")
          .asText();
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
