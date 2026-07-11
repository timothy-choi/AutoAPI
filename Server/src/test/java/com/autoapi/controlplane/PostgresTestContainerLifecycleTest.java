package com.autoapi.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Guards the shared PostgreSQL Testcontainer lifecycle used by all control-plane integration tests.
 * The container must stay running for the entire Gradle test JVM even when Spring caches
 * application contexts across test classes.
 */
class PostgresTestContainerLifecycleTest extends ControlPlaneIntegrationTest {

  @Autowired private DatabaseClient databaseClient;

  @Test
  void sharedPostgresContainerIsRunningWhenIntegrationTestsStart() {
    assertTrue(
        ControlPlaneIntegrationTest.isPostgresRunning(),
        "Shared PostgreSQL container must be running before integration tests execute");
    assertNotNull(ControlPlaneIntegrationTest.postgresMappedPort());
  }

  @Test
  void jdbcAndR2dbcPropertiesReferenceSameContainer() {
    int mappedPort = ControlPlaneIntegrationTest.postgresMappedPort();
    String hostPort = ControlPlaneIntegrationTest.POSTGRES.getHost() + ":" + mappedPort;

    assertTrue(
        ControlPlaneIntegrationTest.r2dbcUrl().contains(hostPort),
        "R2DBC URL must use the shared container host and mapped port");
    assertTrue(
        ControlPlaneIntegrationTest.jdbcUrl().contains(hostPort),
        "JDBC URL must use the shared container host and mapped port");
    assertTrue(
        ControlPlaneIntegrationTest.r2dbcUrl().contains("/autoapi_test"),
        "R2DBC URL must use the shared test database");
    assertTrue(
        ControlPlaneIntegrationTest.jdbcUrl().contains("/autoapi_test"),
        "JDBC URL must use the shared test database");
    assertEquals("autoapi", ControlPlaneIntegrationTest.postgresUsername());
    assertEquals("autoapi", ControlPlaneIntegrationTest.postgresPassword());
  }

  @Test
  void r2dbcConnectionUsesLiveSharedContainer() {
    Long tableCount =
        databaseClient
            .sql(
                "SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_schema ="
                    + " 'public' AND table_name = 'projects'")
            .map(row -> row.get("count", Long.class))
            .one()
            .block();

    assertEquals(1L, tableCount);
    assertTrue(
        ControlPlaneIntegrationTest.isPostgresRunning(),
        "Shared PostgreSQL container must remain running after R2DBC queries");
  }
}
