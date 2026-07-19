package com.autoapi.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

public final class ManagementAuthTestSupport {

  public static final String TEST_MANAGEMENT_PEPPER =
      "development-only-test-management-pepper-minimum-sixteen";
  public static final String TEST_BOOTSTRAP_TOKEN =
      "test-bootstrap-admin-token-for-integration-tests-only";
  public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000001";

  private static volatile String adminToken;

  private ManagementAuthTestSupport() {}

  public static String adminToken(WebTestClient client) {
    if (adminToken != null) {
      return adminToken;
    }
    synchronized (ManagementAuthTestSupport.class) {
      if (adminToken != null) {
        return adminToken;
      }
      JsonNode body =
          client
              .post()
              .uri("/api/v1/management/bootstrap")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_BOOTSTRAP_TOKEN)
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();
      if (body == null || !body.hasNonNull("token")) {
        throw new IllegalStateException("Bootstrap did not return a management token");
      }
      adminToken = body.get("token").asText();
      return adminToken;
    }
  }

  public static WebTestClient.RequestHeadersSpec<?> authorized(
      WebTestClient.RequestBodyUriSpec spec, WebTestClient client) {
    return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken(client));
  }

  public static WebTestClient.RequestHeadersSpec<?> authorizedGet(
      WebTestClient.RequestHeadersUriSpec<?> spec, WebTestClient client) {
    return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken(client));
  }
}
