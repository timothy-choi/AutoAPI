package com.autoapi.controlplane.managementauth;

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

class ManagementAuthIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired DatabaseClient databaseClient;

  @BeforeEach
  void cleanDatabase() {
    ControlPlaneDatabaseCleaner.cleanAll(databaseClient);
  }

  @Test
  void unauthenticatedManagementRequestReturns401() {
    webTestClient
        .mutate()
        .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
        .build()
        .get()
        .uri("/api/v1/projects")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("AUTHENTICATION_REQUIRED");
  }

  @Test
  void bootstrapAuthMeReturnsPrincipal() {
    webTestClient
        .get()
        .uri("/api/v1/management/auth/me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.principalType")
        .isEqualTo("BOOTSTRAP_ADMIN");
  }

  @Test
  void invalidManagementTokenFormatIsRejected() {
    webTestClient
        .mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ak_live_not_a_management_token")
        .build()
        .get()
        .uri("/api/v1/projects")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void scopedViewerCanReadAssignedProjectButNotMutate() {
    String projectId = createProject("viewer-scope-" + UUID.randomUUID());
    String serviceAccountId = createServiceAccount(projectId, "viewer-sa");
    bindProjectRole(serviceAccountId, projectId, "PROJECT_VIEWER");
    String viewerToken = createCredential(serviceAccountId, "project.read");

    webTestClient
        .mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
        .build()
        .get()
        .uri("/api/v1/projects/" + projectId)
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
        .build()
        .post()
        .uri("/api/v1/projects/" + projectId + "/apis")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"denied-api\",\"host\":\"denied.autoapi.local\",\"basePath\":\"/\"}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void scopedViewerCannotReadOtherProject() {
    String allowedProjectId = createProject("allowed-" + UUID.randomUUID());
    String otherProjectId = createProject("other-" + UUID.randomUUID());
    String serviceAccountId = createServiceAccount(allowedProjectId, "isolated-viewer");
    bindProjectRole(serviceAccountId, allowedProjectId, "PROJECT_VIEWER");
    String viewerToken = createCredential(serviceAccountId, "project.read");

    webTestClient
        .mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
        .build()
        .get()
        .uri("/api/v1/projects/" + otherProjectId)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void gatewayRegistrationRemainsPublic() {
    webTestClient
        .mutate()
        .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
        .build()
        .post()
        .uri("/api/v1/gateways/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "gatewayId": "auth-smoke-gateway",
              "gatewayGroup": "default",
              "softwareVersion": "0.1.0",
              "startedAt": "2026-07-11T23:30:00Z"
            }
            """)
        .exchange()
        .expectStatus()
        .value(
            status ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    status >= 200 && status < 300 || status == 409,
                    "Expected gateway registration to remain public, got " + status));
  }

  private String createProject(String name) {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"" + name + "\",\"description\":\"auth test\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return extractField(body, "id");
  }

  private String createServiceAccount(String projectId, String name) {
    byte[] body =
        webTestClient
            .post()
            .uri(
                "/api/v1/management/organizations/"
                    + ManagementAuthTestSupport.DEFAULT_ORG_ID
                    + "/service-accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"name\":\""
                    + name
                    + "\",\"description\":\"auth test\",\"projectId\":\""
                    + projectId
                    + "\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return extractField(body, "id");
  }

  private void bindProjectRole(String serviceAccountId, String projectId, String role) {
    webTestClient
        .post()
        .uri("/api/v1/management/projects/" + projectId + "/role-bindings")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"principalType\":\"SERVICE_ACCOUNT\",\"principalId\":\""
                + serviceAccountId
                + "\",\"role\":\""
                + role
                + "\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private String createCredential(String serviceAccountId, String scope) {
    byte[] body =
        webTestClient
            .post()
            .uri("/api/v1/management/service-accounts/" + serviceAccountId + "/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"scoped-token\",\"scopes\":[\"" + scope + "\"]}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    return extractField(body, "token");
  }

  private static String extractField(byte[] body, String field) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse JSON field " + field, e);
    }
  }
}
