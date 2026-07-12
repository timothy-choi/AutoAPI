package com.autoapi.controlplane.apikey;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.ControlPlaneDatabaseCleaner;
import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.StructuredApiKey;
import com.autoapi.support.SecurityTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

class ApiKeyManagementIntegrationTest extends ControlPlaneIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void clean() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void createReturnsPlaintextOnceAndPersistsDigestOnly() throws Exception {
    String apiId = bootstrapApi();
    byte[] createResponse =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"orders-production-client\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.plaintextKey")
            .exists()
            .returnResult()
            .getResponseBody();

    JsonNode created = MAPPER.readTree(createResponse);
    String plaintextKey = created.get("plaintextKey").asText();
    StructuredApiKey parsed = StructuredApiKey.parse(plaintextKey);

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/api-keys")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].plaintextKey")
        .doesNotExist();

    webTestClient
        .get()
        .uri("/api/v1/apis/" + apiId + "/api-keys/" + parsed.keyId())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.plaintextKey")
        .doesNotExist();

    byte[] storedDigest =
        databaseClient
            .sql("SELECT secret_digest FROM api_keys WHERE key_id = :keyId")
            .bind("keyId", parsed.keyId())
            .map(row -> row.get("secret_digest", byte[].class))
            .one()
            .block();
    assertNotNull(storedDigest);
    assertArrayEquals(
        ApiKeyDigestService.digestSecret(parsed.secret(), SecurityTestFixtures.TEST_PEPPER),
        storedDigest);
  }

  @Test
  void duplicateNameConflicts() {
    String apiId = bootstrapApi();
    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/api-keys")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"duplicate-name\"}")
        .exchange()
        .expectStatus()
        .isCreated();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/api-keys")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"duplicate-name\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void revokeIsIdempotent() throws Exception {
    String apiId = bootstrapApi();
    byte[] createResponse =
        webTestClient
            .post()
            .uri("/api/v1/apis/" + apiId + "/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"revoke-me\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    String keyId = MAPPER.readTree(createResponse).get("keyId").asText();

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/api-keys/" + keyId + "/revoke")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revokedAt")
        .exists()
        .jsonPath("$.enabled")
        .isEqualTo(false);

    webTestClient
        .post()
        .uri("/api/v1/apis/" + apiId + "/api-keys/" + keyId + "/revoke")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revokedAt")
        .exists();
  }

  private String bootstrapApi() {
    String suffix = UUID.randomUUID().toString();
    String projectId =
        extractJsonField(postJson("/api/v1/projects", "{\"name\":\"keys-" + suffix + "\"}"), "id");
    return extractJsonField(
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

  private static String extractJsonField(byte[] body, String field) {
    try {
      return MAPPER.readTree(body).get(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
