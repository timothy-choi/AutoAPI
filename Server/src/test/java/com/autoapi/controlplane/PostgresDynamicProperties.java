package com.autoapi.controlplane;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spring discovers {@link DynamicPropertySource} on the concrete test class and its interfaces, but
 * not on superclass methods. This interface wires the shared singleton Testcontainers PostgreSQL
 * instance for all control plane integration tests.
 */
public interface PostgresDynamicProperties {

  @DynamicPropertySource
  static void registerPostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.r2dbc.url", ControlPlaneIntegrationTest::r2dbcUrl);
    registry.add("spring.r2dbc.username", ControlPlaneIntegrationTest::postgresUsername);
    registry.add("spring.r2dbc.password", ControlPlaneIntegrationTest::postgresPassword);
    registry.add("spring.datasource.url", ControlPlaneIntegrationTest::jdbcUrl);
    registry.add("spring.datasource.username", ControlPlaneIntegrationTest::postgresUsername);
    registry.add("spring.datasource.password", ControlPlaneIntegrationTest::postgresPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add(
        "autoapi.security.api-key-pepper",
        () -> com.autoapi.support.SecurityTestFixtures.TEST_PEPPER);
  }
}
