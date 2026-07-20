package com.autoapi.controlplane;

import com.autoapi.support.TestUpstream;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=control-plane",
      "autoapi.controlplane.enabled=true",
      "spring.flyway.enabled=true",
      "autoapi.management-auth.enabled=true",
      "autoapi.management-auth.token.pepper=development-only-test-management-pepper-minimum-sixteen",
      "autoapi.management-auth.bootstrap.token=test-bootstrap-admin-token-for-integration-tests-only",
      "autoapi.management-auth.bootstrap.enabled=true"
    })
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = ControlPlaneIntegrationTest.Initializer.class)
public abstract class ControlPlaneIntegrationTest implements PostgresDynamicProperties {

  /**
   * Singleton PostgreSQL container for all control-plane integration tests in one Gradle JVM.
   * Started once in the static initializer and not stopped until process exit.
   */
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("autoapi_test")
          .withUsername("autoapi")
          .withPassword("autoapi")
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*", 1)
                  .withStartupTimeout(Duration.ofMinutes(2)))
          .withStartupTimeout(Duration.ofMinutes(2));

  protected static final java.nio.file.Path tempDir;
  protected static final TestUpstream upstream;
  protected static final java.nio.file.Path configPath;

  static {
    try {
      POSTGRES.start();
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "Control plane integration tests require a running Docker daemon for PostgreSQL"
              + " Testcontainers",
          ex);
    }
    try {
      tempDir = java.nio.file.Files.createTempDirectory("autoapi-cp-it");
      upstream = TestUpstream.start();
      configPath = TestUpstream.writeConfig(upstream, tempDir);
    } catch (java.io.IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @org.springframework.beans.factory.annotation.Autowired protected WebTestClient webTestClient;

  @org.springframework.beans.factory.annotation.Autowired
  private com.autoapi.controlplane.policy.EffectivePolicyCache effectivePolicyCache;

  @org.junit.jupiter.api.BeforeEach
  void authorizeManagementRequests() {
    if (effectivePolicyCache != null) {
      effectivePolicyCache.invalidateAll();
    }
    webTestClient =
        webTestClient
            .mutate()
            .defaultHeader(
                org.springframework.http.HttpHeaders.AUTHORIZATION,
                "Bearer " + com.autoapi.support.ManagementAuthTestSupport.TEST_BOOTSTRAP_TOKEN)
            .build();
  }

  @AfterAll
  static void shutdownUpstream() {
    upstream.stop();
  }

  static boolean isPostgresRunning() {
    return POSTGRES.isRunning();
  }

  static int postgresMappedPort() {
    return POSTGRES.getMappedPort(5432);
  }

  static String r2dbcUrl() {
    return "r2dbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + POSTGRES.getDatabaseName()
        + "?sslMode=disable";
  }

  static String jdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  static String postgresUsername() {
    return POSTGRES.getUsername();
  }

  static String postgresPassword() {
    return POSTGRES.getPassword();
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      TestUpstream.initializer(configPath).initialize(context);
    }
  }
}
