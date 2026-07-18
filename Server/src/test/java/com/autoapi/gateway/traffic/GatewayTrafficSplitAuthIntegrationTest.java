package com.autoapi.gateway.traffic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeConfigCompilerAuthenticationTest;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.gateway.config.remote.RemoteSnapshotAdapter;
import com.autoapi.support.RedisDynamicProperties;
import com.autoapi.support.SecurityTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=gateway",
      "autoapi.controlplane.enabled=false",
      "spring.flyway.enabled=false",
      "autoapi.gateway.api-id=00000000-0000-0000-0000-000000000001",
      "autoapi.gateway.config-source=control_plane",
      "autoapi.security.api-key-pepper=" + SecurityTestFixtures.TEST_PEPPER
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = GatewayTrafficSplitAuthIntegrationTest.Initializer.class)
class GatewayTrafficSplitAuthIntegrationTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired private WebTestClient webTestClient;
  @Autowired private ActiveRuntimeConfigHolder activeRuntimeConfigHolder;

  @MockBean private TrafficSplitSelector trafficSplitSelector;

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisDynamicProperties.registerRedisProperties(registry);
  }

  @BeforeEach
  void activateAuthenticatedSplitRoute() {
    HashableRuntimePayload payload =
        RuntimeConfigCompilerAuthenticationTest.authenticatedTrafficSplitPayload();
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    StoredRuntimeSnapshot snapshot = RuntimeConfigCompiler.toStoredSnapshot(payload, 1, hash);
    ActiveRuntimeBundle bundle = RemoteSnapshotAdapter.toActiveBundle(snapshot, API_ID);
    activeRuntimeConfigHolder.activate(bundle);
  }

  @Test
  void missingApiKeyReturns401BeforeTrafficSplitSelection() {
    webTestClient
        .get()
        .uri("/v1/orders/smoke")
        .header(HttpHeaders.HOST, "api.autoapi.local")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.error.code")
        .isEqualTo("INVALID_API_KEY");

    verify(trafficSplitSelector, never()).select(any(), any(), any());
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "gatewayTrafficSplitAuthIntegrationTest",
              java.util.Map.of(
                  "spring.autoconfigure.exclude",
                  String.join(
                      ",",
                      "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration",
                      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                      "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")));
      context.getEnvironment().getPropertySources().addFirst(propertySource);
    }
  }
}
