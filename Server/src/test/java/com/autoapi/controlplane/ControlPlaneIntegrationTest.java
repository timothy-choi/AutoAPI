package com.autoapi.controlplane;

import com.autoapi.support.TestUpstream;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(initializers = ControlPlaneIntegrationTest.Initializer.class)
public abstract class ControlPlaneIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("autoapi")
          .withUsername("autoapi")
          .withPassword("autoapi");

  protected static final java.nio.file.Path tempDir;
  protected static final TestUpstream upstream;
  protected static final java.nio.file.Path configPath;

  static {
    try {
      tempDir = java.nio.file.Files.createTempDirectory("autoapi-cp-it");
      upstream = TestUpstream.start();
      configPath = TestUpstream.writeConfig(upstream, tempDir);
    } catch (java.io.IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @org.springframework.beans.factory.annotation.Autowired protected WebTestClient webTestClient;

  @AfterAll
  static void stopUpstream() {
    upstream.stop();
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.r2dbc.url", () -> r2dbcUrl(POSTGRES));
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("autoapi.controlplane.enabled", () -> "true");
  }

  private static String r2dbcUrl(PostgreSQLContainer<?> postgres) {
    return "r2dbc:postgresql://"
        + postgres.getHost()
        + ":"
        + postgres.getFirstMappedPort()
        + "/"
        + postgres.getDatabaseName();
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
