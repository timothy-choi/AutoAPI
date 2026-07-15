package com.autoapi.controlplane;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@TestPropertySource(properties = {"autoapi.role=combined"})
class ControlPlaneRestIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired DatabaseClient databaseClient;

  @Test
  void controlPlaneRoutesAndSchemaAreAvailable(
      @Autowired RouterFunction<ServerResponse> controlPlaneRoutes) {
    assertNotNull(controlPlaneRoutes);

    Long tableCount =
        databaseClient
            .sql(
                "SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_schema ="
                    + " 'public' AND table_name IN ('projects', 'apis', 'upstream_pools',"
                    + " 'upstream_targets', 'routes', 'config_versions', 'api_keys',"
                    + " 'rate_limit_policies', 'route_policy_bindings', 'backend_health_policies',"
                    + " 'retry_policies')")
            .map(row -> row.get("count", Long.class))
            .one()
            .block();
    assertEquals(11L, tableCount);
  }

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void fullControlPlaneFlow() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"payments-platform-" + suffix + "\",\"description\":\"demo\"}"),
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
    String upstreamPathBefore = upstream.lastPath();

    webTestClient
        .get()
        .uri("/api/v1/projects")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$")
        .isArray();

    org.junit.jupiter.api.Assertions.assertEquals(
        upstreamPathBefore,
        upstream.lastPath(),
        "Management GET must not reach the configured upstream");
  }

  @Test
  void unknownManagementPathsAreNotProxied() {
    String upstreamPathBefore = upstream.lastPath();

    webTestClient
        .get()
        .uri("/api/v1/does-not-exist")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .doesNotExist();

    org.junit.jupiter.api.Assertions.assertEquals(
        upstreamPathBefore, upstream.lastPath(), "Unknown management paths must not be proxied");
  }

  @Test
  void similarPathsOutsideManagementNamespaceAreNotReserved() {
    webTestClient
        .get()
        .uri("/api/v1example")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("ROUTE_NOT_FOUND");

    webTestClient
        .get()
        .uri("/api/v10/example")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("ROUTE_NOT_FOUND");
  }

  @Test
  void postManagementProjectsReachesControlPlane() {
    String upstreamPathBefore = upstream.lastPath();
    String projectName = "management-post-check-" + UUID.randomUUID();

    webTestClient
        .post()
        .uri("/api/v1/projects")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"" + projectName + "\",\"description\":\"demo\"}")
        .header("Host", "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo(projectName);

    org.junit.jupiter.api.Assertions.assertEquals(
        upstreamPathBefore,
        upstream.lastPath(),
        "Management POST must not reach the configured upstream");
  }

  @Test
  void publishConfigWorksWhenJdbcFlywayAndR2dbcAreBothConfigured() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(
            postJson(
                "/api/v1/projects",
                "{\"name\":\"publish-tx-check-" + suffix + "\",\"description\":\"demo\"}"),
            "id");

    String apiId =
        extractJsonField(
            postJson(
                "/api/v1/projects/" + projectId + "/apis",
                "{\"name\":\"publish-api\",\"host\":\"api.autoapi.local\",\"basePath\":\"/\"}"),
            "id");

    String poolId =
        extractJsonField(
            postJson(
                "/api/v1/apis/" + apiId + "/upstream-pools",
                "{\"name\":\"publish-pool\",\"loadBalancing\":\"ROUND_ROBIN\"}"),
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
                + "\"name\":\"publish-route\","
                + "\"host\":\"api.autoapi.local\","
                + "\"pathPrefix\":\"/v1/publish-check\","
                + "\"methods\":[\"GET\"],"
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
        .bodyValue("{\"message\":\"Publish with dual transaction managers\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(1);
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

    org.junit.jupiter.api.Assertions.assertEquals("/v1/orders/123", upstream.lastPath());
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
