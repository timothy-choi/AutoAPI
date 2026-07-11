package com.autoapi.controlplane;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ControlPlaneRestIntegrationTest extends ControlPlaneIntegrationTest {

  @Test
  void fullControlPlaneFlow() {
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"payments-platform-flow\",\"description\":\"demo\"}"),
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
        .bodyValue("{\"message\":\"Initial orders routing configuration\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(1);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"message\":\"Duplicate publish\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("CONFIG_VERSION_ALREADY_EXISTS");

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/config/versions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].configSnapshot")
        .doesNotExist();

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/config/versions/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.snapshot.routes[0].pathPrefix")
        .isEqualTo("/v1/orders");
  }

  @Test
  void managementRoutesAreNotProxied() {
    webTestClient
        .get()
        .uri("/api/v1/projects")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void gatewayStillUsesStaticConfig() {
    webTestClient
        .get()
        .uri("/v1/orders/123")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.path")
        .isEqualTo("/v1/orders/123");
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
      fail(e);
      return null;
    }
  }
}
